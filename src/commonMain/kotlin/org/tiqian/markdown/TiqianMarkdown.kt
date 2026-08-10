package org.tiqian.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.tiqian.compose.CjkInlineObject
import org.tiqian.compose.CjkInlineObjectBoundary
import org.tiqian.compose.CjkInlineObjectPreferredStretch
import org.tiqian.compose.CjkInlineObjectPreferredStretchKind
import org.tiqian.compose.CjkText
import org.tiqian.compose.cjkTextCompatibility
import org.tiqian.compose.rememberParagraphMeasurer
import org.tiqian.compose.toCjkTextStyle
import org.tiqian.compose.toCoreTextStyle
import org.tiqian.core.LayoutConstraints
import org.tiqian.core.LayoutResult
import org.tiqian.core.LineLengthGrid
import org.tiqian.core.ParagraphStyle
import org.tiqian.core.ic
import kotlin.math.ceil

enum class MarkdownTextFallbackPolicy {
    /** Use Compose text whenever a text block contains semantics Tiqian cannot preserve. */
    Automatic,

    /** Always use Tiqian. Intended for capability dogfooding. */
    Disabled,
}

/** Slots for non-prose blocks. The default set never performs network loading. */
class MarkdownBlockSlots(
    val codeBlock: (@Composable (MarkdownCodeBlock, MarkdownStyle) -> Unit)? = null,
    val imageBlock: (@Composable (MarkdownImageBlock, MarkdownStyle) -> Unit)? = null,
    val mathBlock: (@Composable (MarkdownMathBlock, MarkdownStyle) -> Unit)? = null,
    val htmlBlock: (@Composable (MarkdownHtmlBlock, MarkdownStyle) -> Unit)? = null,
    val table: (@Composable (MarkdownTable, MarkdownStyle) -> Unit)? = null,
    val footnoteDefinition: (@Composable (MarkdownFootnoteDefinition, MarkdownStyle) -> Unit)? = null,
    val customBlock: (@Composable (MarkdownCustomBlock, MarkdownStyle) -> Unit)? = null,
    val thematicBreak: (@Composable (MarkdownThematicBreak, MarkdownStyle) -> Unit)? = null,
    val unsupportedBlock: (@Composable (MarkdownUnsupportedBlock, MarkdownStyle) -> Unit)? = null,
)

val DefaultMarkdownBlockSlots: MarkdownBlockSlots = MarkdownBlockSlots()

/** Renders a host-adapted Markdown document without owning parsing or scrolling. */
@Composable
fun TiqianMarkdown(
    document: MarkdownRenderDocument,
    modifier: Modifier = Modifier,
    style: MarkdownStyle = MarkdownStyle(),
    slots: MarkdownBlockSlots = DefaultMarkdownBlockSlots,
    inlineSlots: MarkdownInlineSlots = DefaultMarkdownInlineSlots,
    fallbackPolicy: MarkdownTextFallbackPolicy = MarkdownTextFallbackPolicy.Automatic,
    onLinkClick: ((String) -> Unit)? = null,
    onFootnoteClick: ((String) -> Unit)? = null,
) {
    val footnoteNavigationState = rememberMarkdownFootnoteNavigationState()
    CompositionLocalProvider(LocalMarkdownFootnoteNavigationState provides footnoteNavigationState) {
        MarkdownBlocks(
            blocks = document.blocks,
            modifier = modifier,
            style = style,
            slots = slots,
            inlineSlots = inlineSlots,
            fallbackPolicy = fallbackPolicy,
            onLinkClick = onLinkClick,
            onFootnoteClick = onFootnoteClick,
            compact = false,
        )
    }
}

@Composable
private fun MarkdownBlocks(
    blocks: List<MarkdownBlock>,
    modifier: Modifier,
    style: MarkdownStyle,
    slots: MarkdownBlockSlots,
    inlineSlots: MarkdownInlineSlots,
    fallbackPolicy: MarkdownTextFallbackPolicy,
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
    compact: Boolean,
) {
    val spacing = if (compact) style.compactBlockSpacing else style.blockSpacing
    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(spacing))
            key(block.metadata.key) {
                MarkdownBlock(
                    block = block,
                    style = style,
                    slots = slots,
                    inlineSlots = inlineSlots,
                    fallbackPolicy = fallbackPolicy,
                    onLinkClick = onLinkClick,
                    onFootnoteClick = onFootnoteClick,
                )
            }
        }
    }
}

@Composable
private fun MarkdownBlock(
    block: MarkdownBlock,
    style: MarkdownStyle,
    slots: MarkdownBlockSlots,
    inlineSlots: MarkdownInlineSlots,
    fallbackPolicy: MarkdownTextFallbackPolicy,
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
) {
    when (block) {
        is MarkdownParagraph -> MarkdownTextBlock(
            block.text,
            style.body,
            style,
            inlineSlots,
            fallbackPolicy,
            onLinkClick,
            onFootnoteClick,
        )
        is MarkdownHeading -> MarkdownTextBlock(
            block.text,
            style.heading(block.level),
            style,
            inlineSlots,
            fallbackPolicy,
            onLinkClick,
            onFootnoteClick,
        )
        is MarkdownBlockQuote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Spacer(
                Modifier
                    .width(style.quoteBarWidth)
                    .fillMaxHeight()
                    .background(style.quoteBarColor),
            )
            MarkdownBlocks(
                blocks = block.blocks,
                modifier = Modifier.weight(1f).padding(start = style.quoteContentPadding),
                style = style,
                slots = slots,
                inlineSlots = inlineSlots,
                fallbackPolicy = fallbackPolicy,
                onLinkClick = onLinkClick,
                onFootnoteClick = onFootnoteClick,
                compact = true,
            )
        }

        is MarkdownList -> MarkdownListBlock(
            block,
            style,
            slots,
            inlineSlots,
            fallbackPolicy,
            onLinkClick,
            onFootnoteClick,
        )
        is MarkdownCodeBlock -> slots.codeBlock?.invoke(block, style) ?: DefaultMarkdownCodeBlock(block, style)
        is MarkdownImageBlock -> slots.imageBlock?.invoke(block, style)
            ?: DefaultMarkdownImageBlock(block, style, onLinkClick, inlineSlots)
        is MarkdownMathBlock -> slots.mathBlock?.invoke(block, style) ?: DefaultMarkdownMathBlock(block, style)
        is MarkdownHtmlBlock -> slots.htmlBlock?.invoke(block, style) ?: DefaultMarkdownHtmlBlock(block, style)
        is MarkdownTable -> slots.table?.invoke(block, style)
            ?: DefaultMarkdownTable(block, style, fallbackPolicy, onLinkClick, onFootnoteClick, inlineSlots)
        is MarkdownFootnoteDefinition -> slots.footnoteDefinition?.invoke(block, style)
            ?: DefaultMarkdownFootnoteDefinition(
                block,
                style,
                slots,
                fallbackPolicy,
                onLinkClick,
                onFootnoteClick,
                inlineSlots,
            )
        is MarkdownCustomBlock -> slots.customBlock?.invoke(block, style)
            ?: DefaultMarkdownCustomBlock(block, style)
        is MarkdownThematicBreak -> slots.thematicBreak?.invoke(block, style) ?: DefaultMarkdownThematicBreak(style)
        is MarkdownUnsupportedBlock -> slots.unsupportedBlock?.invoke(block, style)
            ?: DefaultMarkdownUnsupportedBlock(block, style)
    }
}

@Composable
private fun MarkdownListBlock(
    block: MarkdownList,
    style: MarkdownStyle,
    slots: MarkdownBlockSlots,
    inlineSlots: MarkdownInlineSlots,
    fallbackPolicy: MarkdownTextFallbackPolicy,
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
) {
    val density = LocalDensity.current
    val markerMeasurer = rememberParagraphMeasurer()
    val markerParagraphStyle = remember {
        ParagraphStyle(
            firstLineIndent = 0.ic,
            lineLengthGrid = LineLengthGrid(enabled = false),
        )
    }
    val markerTextStyle = remember(style.body) { style.body.toCjkTextStyle() }
    val coreMarkerTextStyle = remember(markerTextStyle, density) {
        markerTextStyle.toCoreTextStyle(density)
    }
    val markers = remember(block) {
        block.items.mapIndexed { index, item ->
            when (item.task) {
                MarkdownTaskState.Checked -> "[x]"
                MarkdownTaskState.Unchecked -> "[ ]"
                null -> if (block.ordered) "${block.startNumber + index}." else "•"
            }
        }
    }
    val markerGutter = remember(markers, coreMarkerTextStyle, markerParagraphStyle, markerMeasurer) {
        val widestMarker = markers.maxOfOrNull { marker ->
            markerMeasurer.measure(
                text = marker,
                constraints = LayoutConstraints(maxWidth = 100_000f),
                textStyle = coreMarkerTextStyle,
                paragraphStyle = markerParagraphStyle,
            ).size.width
        } ?: 0f
        ceil(widestMarker / coreMarkerTextStyle.fontSize).toInt().coerceAtLeast(1).ic
    }
    val markerWidth = with(density) { markerGutter.toPx(coreMarkerTextStyle.fontSize).toDp() }
    val narrowBreakpoint = with(density) {
        style.listNarrowBreakpoint.toPx(coreMarkerTextStyle.fontSize).toDp()
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrowMeasure = maxWidth < narrowBreakpoint
        val minimumContentIndent = if (narrowMeasure) {
            style.listNarrowContentIndent
        } else {
            style.listContentIndent
        }
        val contentIndent = if (markerGutter.count >= minimumContentIndent.count) {
            markerGutter
        } else {
            minimumContentIndent
        }
        val markerLeadingSpace = with(density) {
            (contentIndent + -markerGutter).toPx(coreMarkerTextStyle.fontSize).toDp()
        }

        Column {
            block.items.forEachIndexed { index, item ->
                if (index > 0) {
                    Spacer(Modifier.height(if (block.tight) style.compactBlockSpacing else style.listItemSpacing))
                }
                Row(Modifier.fillMaxWidth().padding(start = markerLeadingSpace)) {
                    val marker = markers[index]
                    CjkText(
                        text = marker,
                        modifier = Modifier
                            .width(markerWidth)
                            .alignByBaseline(),
                        style = style.body,
                        paragraphStyle = markerParagraphStyle,
                        measurer = markerMeasurer,
                    )
                    MarkdownBlocks(
                        blocks = item.blocks,
                        modifier = Modifier
                            .weight(1f)
                            .alignByBaseline(),
                        style = style,
                        slots = slots,
                        inlineSlots = inlineSlots,
                        fallbackPolicy = fallbackPolicy,
                        onLinkClick = onLinkClick,
                        onFootnoteClick = onFootnoteClick,
                        compact = block.tight,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTextBlock(
    text: MarkdownText,
    textStyle: TextStyle,
    markdownStyle: MarkdownStyle,
    inlineSlots: MarkdownInlineSlots,
    fallbackPolicy: MarkdownTextFallbackPolicy,
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val footnoteNavigationState = LocalMarkdownFootnoteNavigationState.current
    val footnoteLabels = remember(text) {
        text.spans.mapNotNull { (it.mark as? MarkdownTextMark.Footnote)?.label }.distinct()
    }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    DisposableEffect(footnoteNavigationState, footnoteLabels, bringIntoViewRequester) {
        footnoteLabels.forEach { label ->
            footnoteNavigationState?.registerReference(label, bringIntoViewRequester)
        }
        onDispose {
            footnoteLabels.forEach { label ->
                footnoteNavigationState?.unregisterReference(label, bringIntoViewRequester)
            }
        }
    }
    val currentLinkClick = rememberUpdatedState(onLinkClick)
    val currentFootnoteClick = rememberUpdatedState(onFootnoteClick)
    val currentFootnoteNavigation = rememberUpdatedState(footnoteNavigationState)
    val stableLinkClick = remember(onLinkClick != null) {
        if (onLinkClick == null) {
            null
        } else {
            { destination: String ->
                currentLinkClick.value?.invoke(destination)
                Unit
            }
        }
    }
    val handleFootnoteClick = remember(footnoteNavigationState != null || onFootnoteClick != null) {
        if (footnoteNavigationState == null && onFootnoteClick == null) {
            null
        } else {
            { label: String ->
                currentFootnoteNavigation.value?.bringDefinitionIntoView(label, bringIntoViewRequester)
                currentFootnoteClick.value?.invoke(label)
                Unit
            }
        }
    }
    val resolved = resolveMarkdownText(
        text = text,
        style = markdownStyle,
        textStyle = textStyle,
        inlineSlots = inlineSlots,
        onLinkClick = stableLinkClick,
        onFootnoteClick = handleFootnoteClick,
    )
    val density = LocalDensity.current
    val tiqianInlineObjects = resolved.tiqianInlineObjects.map { resolvedObject ->
        val metrics = requireNotNull(resolvedObject.content.metrics)
        CjkInlineObject(
            range = TextRange(resolvedObject.start, resolvedObject.endExclusive),
            advance = with(density) { metrics.widthPx.toDp() },
            ascent = with(density) { metrics.ascentPx.toDp() },
            descent = with(density) { metrics.descentPx.toDp() },
            leadingBoundary = CjkInlineObjectBoundary(
                participatesInUniformStretch =
                    resolvedObject.content.leadingBoundary.participatesInUniformStretch,
                preferredStretch = resolvedObject.content.leadingBoundary.preferredStretch?.let {
                        CjkInlineObjectPreferredStretch(
                        kind = when (it.kind) {
                            MarkdownInlinePreferredStretchKind.PunctuationTrailing ->
                                CjkInlineObjectPreferredStretchKind.PunctuationTrailing
                            MarkdownInlinePreferredStretchKind.Relation ->
                                CjkInlineObjectPreferredStretchKind.Relation
                            MarkdownInlinePreferredStretchKind.BinaryOperator ->
                                CjkInlineObjectPreferredStretchKind.BinaryOperator
                        },
                        naturalWidth = with(density) { it.naturalWidthPx.toDp() },
                        targetWidth = with(density) { it.targetWidthPx.toDp() },
                    )
                },
                shrinkCapacity = with(density) {
                    resolvedObject.content.leadingBoundary.shrinkCapacityPx.toDp()
                },
                lineEndDiscardableAdvance = with(density) {
                    resolvedObject.content.leadingBoundary.lineEndDiscardableAdvancePx.toDp()
                },
                preventsLineBreak = resolvedObject.content.leadingBoundary.preventsLineBreak,
            ),
            trailingBoundary = CjkInlineObjectBoundary(
                participatesInUniformStretch =
                    resolvedObject.content.trailingBoundary.participatesInUniformStretch,
                preferredStretch = resolvedObject.content.trailingBoundary.preferredStretch?.let {
                        CjkInlineObjectPreferredStretch(
                        kind = when (it.kind) {
                            MarkdownInlinePreferredStretchKind.PunctuationTrailing ->
                                CjkInlineObjectPreferredStretchKind.PunctuationTrailing
                            MarkdownInlinePreferredStretchKind.Relation ->
                                CjkInlineObjectPreferredStretchKind.Relation
                            MarkdownInlinePreferredStretchKind.BinaryOperator ->
                                CjkInlineObjectPreferredStretchKind.BinaryOperator
                        },
                        naturalWidth = with(density) { it.naturalWidthPx.toDp() },
                        targetWidth = with(density) { it.targetWidthPx.toDp() },
                    )
                },
                shrinkCapacity = with(density) {
                    resolvedObject.content.trailingBoundary.shrinkCapacityPx.toDp()
                },
                lineEndDiscardableAdvance = with(density) {
                    resolvedObject.content.trailingBoundary.lineEndDiscardableAdvancePx.toDp()
                },
                preventsLineBreak = resolvedObject.content.trailingBoundary.preventsLineBreak,
            ),
            content = {
                resolvedObject.content.content(resolvedObject.content.alternateText)
            },
        )
    }
    val compatibility = resolved.annotated.cjkTextCompatibility(
        style = textStyle,
        inlineObjects = tiqianInlineObjects,
    )
    val shouldFallback = fallbackPolicy == MarkdownTextFallbackPolicy.Automatic &&
        (text.issues.isNotEmpty() || !compatibility.canPreserveAllKnownSemantics)
    val blockModifier = if (footnoteLabels.isEmpty()) {
        modifier
    } else {
        modifier.bringIntoViewRequester(bringIntoViewRequester)
    }
    if (shouldFallback || resolved.requiresComposeFallback) {
        val fallbackText = resolved.composeFallbackAnnotatedString()
        BasicText(
            text = fallbackText,
            modifier = blockModifier.markdownInlineInteractionSemantics(resolved.interactions),
            style = textStyle,
            inlineContent = resolved.inlineContent,
        )
    } else {
        val layoutResult = remember { arrayOfNulls<LayoutResult>(1) }
        CjkText(
            text = resolved.annotated,
            modifier = blockModifier
                .markdownInlineInteractionSemantics(resolved.interactions)
                .drawTiqianMarkdownInlineDecorations(resolved.decorations) { layoutResult[0] },
            style = textStyle,
            inlineObjects = tiqianInlineObjects,
            onTextLayout = { layoutResult[0] = it },
        )
    }
}

@Composable
fun DefaultMarkdownCodeBlock(block: MarkdownCodeBlock, style: MarkdownStyle) {
    BasicText(
        text = block.code,
        modifier = Modifier.fillMaxWidth().background(style.codeBackground).padding(style.codePadding),
        style = style.codeBlock,
    )
}

@Composable
fun DefaultMarkdownImageBlock(
    block: MarkdownImageBlock,
    style: MarkdownStyle,
    onLinkClick: ((String) -> Unit)? = null,
    inlineSlots: MarkdownInlineSlots = DefaultMarkdownInlineSlots,
) {
    val label = block.description.ifBlank { block.destination }
    MarkdownTextBlock(
        text = MarkdownText(
            value = label,
            spans = listOf(
                MarkdownTextSpan(
                    MarkdownTextRange(0, label.length),
                    MarkdownTextMark.Link(block.destination, block.title),
                ),
            ),
        ),
        textStyle = style.body,
        markdownStyle = style,
        inlineSlots = inlineSlots,
        fallbackPolicy = MarkdownTextFallbackPolicy.Automatic,
        onLinkClick = onLinkClick,
        onFootnoteClick = null,
    )
}

@Composable
fun DefaultMarkdownHtmlBlock(block: MarkdownHtmlBlock, style: MarkdownStyle) {
    BasicText(text = block.html, style = style.codeBlock)
}

@Composable
fun DefaultMarkdownTable(
    block: MarkdownTable,
    style: MarkdownStyle,
    fallbackPolicy: MarkdownTextFallbackPolicy = MarkdownTextFallbackPolicy.Automatic,
    onLinkClick: ((String) -> Unit)? = null,
    onFootnoteClick: ((String) -> Unit)? = null,
    inlineSlots: MarkdownInlineSlots = DefaultMarkdownInlineSlots,
) {
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        block.rows.forEach { row ->
            Row {
                row.cells.forEach { cell ->
                    val cellModifier = Modifier
                        .width(style.tableColumnWidth)
                        .border(0.5.dp, style.tableBorderColor)
                        .let { modifier ->
                            if (cell.header) modifier.background(style.tableHeaderBackground) else modifier
                        }
                        .padding(style.tableCellPadding)
                    val textStyle = style.body.copy(
                        fontWeight = if (cell.header) FontWeight.Bold else style.body.fontWeight,
                        textAlign = when (cell.alignment) {
                            MarkdownTableAlignment.Start,
                            MarkdownTableAlignment.Unspecified,
                            -> TextAlign.Start
                            MarkdownTableAlignment.Center -> TextAlign.Center
                            MarkdownTableAlignment.End -> TextAlign.End
                        },
                    )
                    MarkdownTextBlock(
                        text = cell.text,
                        textStyle = textStyle,
                        markdownStyle = style,
                        inlineSlots = inlineSlots,
                        fallbackPolicy = fallbackPolicy,
                        onLinkClick = onLinkClick,
                        onFootnoteClick = onFootnoteClick,
                        modifier = cellModifier,
                    )
                }
            }
        }
    }
}

@Composable
fun DefaultMarkdownFootnoteDefinition(
    block: MarkdownFootnoteDefinition,
    style: MarkdownStyle,
    slots: MarkdownBlockSlots = DefaultMarkdownBlockSlots,
    fallbackPolicy: MarkdownTextFallbackPolicy = MarkdownTextFallbackPolicy.Automatic,
    onLinkClick: ((String) -> Unit)? = null,
    onFootnoteClick: ((String) -> Unit)? = null,
    inlineSlots: MarkdownInlineSlots = DefaultMarkdownInlineSlots,
) {
    val footnoteNavigationState = LocalMarkdownFootnoteNavigationState.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    DisposableEffect(footnoteNavigationState, block.label, bringIntoViewRequester) {
        footnoteNavigationState?.registerDefinition(block.label, bringIntoViewRequester)
        onDispose {
            footnoteNavigationState?.unregisterDefinition(block.label, bringIntoViewRequester)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester),
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = style.footnoteLabelWidth),
        ) {
            BasicText(
                text = "[${block.index}]",
                style = style.body.merge(style.footnote).copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                text = "↩",
                modifier = Modifier.let { modifier ->
                    if (footnoteNavigationState == null) {
                        modifier
                    } else {
                        modifier.clickable {
                            footnoteNavigationState.bringReferenceIntoView(block.label)
                        }
                    }
                },
                style = style.body.merge(style.footnote).merge(style.link),
            )
        }
        MarkdownBlocks(
            blocks = block.blocks,
            modifier = Modifier.weight(1f),
            style = style,
            slots = slots,
            inlineSlots = inlineSlots,
            fallbackPolicy = fallbackPolicy,
            onLinkClick = onLinkClick,
            onFootnoteClick = onFootnoteClick,
            compact = true,
        )
    }
}

@Composable
fun DefaultMarkdownCustomBlock(block: MarkdownCustomBlock, style: MarkdownStyle) {
    BasicText(text = block.metadata.sourceMarkdown.orEmpty(), style = style.body)
}

@Composable
fun DefaultMarkdownThematicBreak(style: MarkdownStyle) {
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(style.thematicBreakColor))
}

@Composable
fun DefaultMarkdownUnsupportedBlock(block: MarkdownUnsupportedBlock, style: MarkdownStyle) {
    val fallback = block.fallbackText.ifBlank { block.metadata.sourceMarkdown.orEmpty() }
    BasicText(text = fallback, style = style.body)
}
