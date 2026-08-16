package org.tiqian.markdown.compose

import org.tiqian.markdown.MarkdownBlock
import org.tiqian.markdown.MarkdownBlockQuote
import org.tiqian.markdown.MarkdownHeading
import org.tiqian.markdown.MarkdownImageBlock
import org.tiqian.markdown.MarkdownParagraph
import org.tiqian.markdown.MarkdownText
import org.tiqian.markdown.MarkdownTextMark
import org.tiqian.markdown.MarkdownTextRange
import org.tiqian.markdown.MarkdownTextSpan

import androidx.compose.runtime.staticCompositionLocalOf
import org.tiqian.compose.ParagraphMeasurer
import org.tiqian.compose.measureWithInlineContent
import org.tiqian.core.LayoutConstraints
import org.tiqian.core.LayoutResult
import org.tiqian.core.ParagraphStyle
import org.tiqian.core.ic

internal data class MarkdownPrecomputedLayout(
    val layout: LayoutResult,
    val preparedInlineMath: Map<Int, MarkdownInlineContent>,
)

internal val LocalMarkdownPrecomputedLayouts =
    staticCompositionLocalOf<Map<Any, MarkdownPrecomputedLayout>> { emptyMap() }

internal fun prelayoutMarkdownBlockIndices(
    blocks: List<MarkdownBlock>,
    visibleRange: IntRange,
    forward: Boolean,
    defaultMath: Boolean = false,
    blockHeightsPx: List<Int> = emptyList(),
    prefetchDistancePx: Int = Int.MAX_VALUE,
    maximumCount: Int = BackgroundPrelayoutMaximumBlocks,
): List<Int> {
    if (blocks.isEmpty() || visibleRange.isEmpty() || maximumCount <= 0) return emptyList()
    val visible = visibleRange
        .asSequence()
        .filter { it in blocks.indices && blocks[it].supportsBackgroundPrelayout(defaultMath) }
        .take(maximumCount)
        .toList()
    val ahead = prefetchMarkdownBlockIndices(
        blocks = blocks,
        visibleRange = visibleRange,
        forward = forward,
        defaultMath = defaultMath,
        blockHeightsPx = blockHeightsPx,
        prefetchDistancePx = prefetchDistancePx,
        maximumCount = maximumCount - visible.size,
    )
    return visible + ahead
}

internal fun backgroundPrelayoutPriorityIndices(
    blocks: List<MarkdownBlock>,
    visibleRange: IntRange,
    forward: Boolean,
    defaultMath: Boolean = false,
): List<Int> {
    if (blocks.isEmpty() || visibleRange.isEmpty()) return emptyList()
    val after = (visibleRange.last + 1 until blocks.size).asSequence()
    val before = (visibleRange.first - 1 downTo 0).asSequence()
    val directional = if (forward) after + before else before + after
    return directional
        .filter { blocks[it].supportsBackgroundPrelayout(defaultMath) }
        .toList()
}

internal fun prefetchMarkdownBlockIndices(
    blocks: List<MarkdownBlock>,
    visibleRange: IntRange,
    forward: Boolean,
    defaultMath: Boolean = false,
    blockHeightsPx: List<Int> = emptyList(),
    prefetchDistancePx: Int = Int.MAX_VALUE,
    maximumCount: Int = BackgroundPrelayoutMaximumBlocks,
): List<Int> {
    if (blocks.isEmpty() || visibleRange.isEmpty() || maximumCount <= 0) return emptyList()
    val indices = if (forward) {
        (visibleRange.last + 1 until blocks.size)
    } else {
        (visibleRange.first - 1 downTo 0)
    }
    val selected = mutableListOf<Int>()
    var distance = 0L
    for (index in indices) {
        if (distance >= prefetchDistancePx || selected.size >= maximumCount) break
        distance += blockHeightsPx.getOrNull(index)?.coerceAtLeast(1) ?: 1
        if (blocks[index].supportsBackgroundPrelayout(defaultMath)) selected += index
    }
    return selected
}

private fun MarkdownBlock.supportsBackgroundPrelayout(defaultMath: Boolean): Boolean {
    val text = when (this) {
        is MarkdownParagraph -> text
        is MarkdownHeading -> text
        // `QuoteSubtreePrelayout`: a block quote is eligible when every nested block is; its
        // nested prose is precomputed at the quote's inset width and content style, keyed by
        // each nested block's own selection key so the ordinary consume path picks it up.
        is MarkdownBlockQuote -> return blocks.isNotEmpty() &&
            blocks.all { it.supportsBackgroundPrelayout(defaultMath) }
        // `ImageFallbackPrelayout`: the URL-text fallback an image renders while offline or on
        // load error is deterministic, so it can be laid out ahead of entry like any paragraph.
        is MarkdownImageBlock -> return true
        else -> return false
    }
    return text.spans.none { span ->
        when (span.mark) {
            is MarkdownTextMark.InlineImage,
            is MarkdownTextMark.Custom,
            -> true
            is MarkdownTextMark.InlineMath -> !defaultMath
            else -> false
        }
    }
}

internal fun precomputeMarkdownBlockEntries(
    block: MarkdownBlock,
    style: MarkdownStyle,
    inlineSlots: MarkdownInlineSlots,
    mathRuntime: MarkdownMathRuntime?,
    density: androidx.compose.ui.unit.Density,
    widthPx: Float,
    measurer: ParagraphMeasurer,
): List<Pair<Any, MarkdownPrecomputedLayout>> = when (block) {
    // `QuoteSubtreePrelayout`: mirror the MarkdownBlockQuote row exactly — the bar spacer and
    // content padding inset the nested prose width, and nested blocks render with the quote's
    // content style. Entries are keyed by each nested block's own selection key.
    is MarkdownBlockQuote -> {
        val innerWidthPx = with(density) {
            widthPx - style.quoteBarWidth.roundToPx() - style.quoteContentPadding.roundToPx()
        }.coerceAtLeast(1f)
        val quoteStyle = style.quoteContentStyle()
        block.blocks.flatMap { nested ->
            precomputeMarkdownBlockEntries(
                block = nested,
                style = quoteStyle,
                inlineSlots = inlineSlots,
                mathRuntime = mathRuntime,
                density = density,
                widthPx = innerWidthPx,
                measurer = measurer,
            )
        }
    }
    // `ImageFallbackPrelayout`: precompute the deterministic URL-text fallback under the
    // fallback's own selection key ("description" scope). When the image loads normally the
    // entry is simply never consumed; captioned fallbacks keep laying out at entry because
    // they render without their own fragment key.
    is MarkdownImageBlock -> {
        val label = block.description.ifBlank { block.destination }
        val fallback = MarkdownParagraph(
            MarkdownText(
                value = label,
                spans = listOf(
                    MarkdownTextSpan(
                        MarkdownTextRange(0, label.length),
                        MarkdownTextMark.Link(block.destination, block.title),
                    ),
                ),
            ),
            block.metadata,
        )
        precomputeMarkdownBlock(
            block = fallback,
            style = style,
            inlineSlots = inlineSlots,
            mathRuntime = mathRuntime,
            density = density,
            widthPx = widthPx,
            measurer = measurer,
        )?.let { listOf(markdownSelectionKey(block.metadata.key, "description") to it) }.orEmpty()
    }
    else -> precomputeMarkdownBlock(
        block = block,
        style = style,
        inlineSlots = inlineSlots,
        mathRuntime = mathRuntime,
        density = density,
        widthPx = widthPx,
        measurer = measurer,
    )?.let { listOf(markdownSelectionKey(block.metadata.key) to it) }.orEmpty()
}

private fun precomputeMarkdownBlock(
    block: MarkdownBlock,
    style: MarkdownStyle,
    inlineSlots: MarkdownInlineSlots,
    mathRuntime: MarkdownMathRuntime?,
    density: androidx.compose.ui.unit.Density,
    widthPx: Float,
    measurer: ParagraphMeasurer,
): MarkdownPrecomputedLayout? = markdownTraceSection("TiqianMarkdown.prelayout") {
    val text = when (block) {
        is MarkdownParagraph -> block.text
        is MarkdownHeading -> block.text
        else -> return@markdownTraceSection null
    }
    val textStyle = when (block) {
        is MarkdownParagraph -> style.body
        is MarkdownHeading -> style.heading(block.level)
    }
    val mathMarks = text.spans.mapIndexedNotNull { spanIndex, span ->
        (span.mark as? MarkdownTextMark.InlineMath)?.let { spanIndex to it }
    }
    val preparedMath = if (mathMarks.isNotEmpty() && inlineSlots.math == null && mathRuntime != null) {
        mathMarks.mapNotNull { (spanIndex, mark) ->
            prepareDefaultMarkdownInlineMath(
                mark = mark,
                style = style,
                hostTextStyle = textStyle,
                density = density,
                preparer = mathRuntime.formulaPreparer,
            )?.let { spanIndex to it }
        }.toMap()
    } else {
        emptyMap()
    }
    if (preparedMath.size != mathMarks.size) return@markdownTraceSection null
    val resolved = resolveMarkdownTextWithPreparedMath(text, style, preparedMath)
    MarkdownPrecomputedLayout(
        layout = measurer.measureWithInlineContent(
            text = resolved.annotated,
            constraints = LayoutConstraints(maxWidth = widthPx),
            density = density,
            style = textStyle,
            paragraphStyle = ParagraphStyle(firstLineIndent = 0.ic),
            inlineObjects = resolved.toCjkInlineObjects(density),
            inlineDecorations = resolved.decorations.toCjkInlineDecorations(),
            inlineBackgrounds = resolved.backgrounds.toCjkInlineBackgrounds(),
        ),
        preparedInlineMath = preparedMath,
    )
}

private const val BackgroundPrelayoutMaximumBlocks = 32

// `ScrollAheadPrelayout` keeps this many viewports of upcoming content laid out ahead of the
// scroll direction; beyond it the worker goes quiet until the viewport moves again.
internal const val ScrollAheadPrelayoutViewportBudget = 2

internal const val BackgroundPrelayoutIdleDelayMillis = 250L
