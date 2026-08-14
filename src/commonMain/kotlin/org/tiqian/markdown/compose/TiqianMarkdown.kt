package org.tiqian.markdown.compose

import org.tiqian.markdown.DefaultMarkdownCodeHighlighter
import org.tiqian.markdown.MarkdownBlock
import org.tiqian.markdown.MarkdownBlockQuote
import org.tiqian.markdown.MarkdownCodeBlock
import org.tiqian.markdown.MarkdownCodeHighlighter
import org.tiqian.markdown.MarkdownCustomBlock
import org.tiqian.markdown.MarkdownFootnoteDefinition
import org.tiqian.markdown.MarkdownFootnotePlacement
import org.tiqian.markdown.MarkdownHeading
import org.tiqian.markdown.MarkdownHtmlBlock
import org.tiqian.markdown.MarkdownImageBlock
import org.tiqian.markdown.MarkdownList
import org.tiqian.markdown.MarkdownMathBlock
import org.tiqian.markdown.MarkdownNodeKey
import org.tiqian.markdown.MarkdownParagraph
import org.tiqian.markdown.MarkdownRenderDocument
import org.tiqian.markdown.MarkdownTable
import org.tiqian.markdown.MarkdownTableAlignment
import org.tiqian.markdown.MarkdownTaskState
import org.tiqian.markdown.MarkdownText
import org.tiqian.markdown.MarkdownTextMark
import org.tiqian.markdown.MarkdownTextRange
import org.tiqian.markdown.MarkdownTextSpan
import org.tiqian.markdown.MarkdownThematicBreak
import org.tiqian.markdown.MarkdownUnsupportedBlock
import org.tiqian.markdown.markdownImageGallery
import org.tiqian.markdown.placeFootnotes
import org.tiqian.markdown.resolveMarkdownCodeLanguage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import org.tiqian.compose.CjkInlineObject
import org.tiqian.compose.CjkInlineObjectBoundary
import org.tiqian.compose.CjkInlineObjectPreferredStretch
import org.tiqian.compose.CjkInlineObjectPreferredStretchKind
import org.tiqian.compose.CjkSelectionScope
import org.tiqian.compose.CjkSelectionDocumentFragment
import org.tiqian.compose.CjkText
import org.tiqian.compose.ParagraphMeasurer
import org.tiqian.compose.ParagraphMeasurementSession
import org.tiqian.compose.createPlatformParagraphMeasurer
import org.tiqian.compose.measure
import org.tiqian.compose.measureWithInlineContent
import org.tiqian.compose.rememberParagraphMeasurer
import org.tiqian.compose.toCjkTextStyle
import org.tiqian.compose.toCoreTextStyle
import org.tiqian.core.LayoutConstraints
import org.tiqian.core.LayoutResult
import org.tiqian.core.LastLineAlignment
import org.tiqian.core.LineLengthGrid
import org.tiqian.core.ParagraphStyle
import org.tiqian.core.TextStyle as CoreTextStyle
import org.tiqian.core.ic
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.tiqian.markdown.compose.generated.resources.Res
import org.tiqian.markdown.compose.generated.resources.code_copied
import org.tiqian.markdown.compose.generated.resources.ic_check_box_20dp
import org.tiqian.markdown.compose.generated.resources.ic_check_box_outline_blank_20dp
import org.tiqian.markdown.compose.generated.resources.ic_check_16dp
import org.tiqian.markdown.compose.generated.resources.ic_content_copy_16dp
import org.tiqian.markdown.compose.generated.resources.copy_code
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

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

private val LocalMarkdownCodeHighlighter = staticCompositionLocalOf<MarkdownCodeHighlighter> {
    DefaultMarkdownCodeHighlighter
}

private val LocalMarkdownParagraphMeasurer = staticCompositionLocalOf<ParagraphMeasurer?> { null }
private data class MarkdownPrecomputedLayout(
    val layout: LayoutResult,
    val preparedInlineMath: Map<Int, MarkdownInlineContent>,
)

private val LocalMarkdownPrecomputedLayouts =
    staticCompositionLocalOf<Map<Any, MarkdownPrecomputedLayout>> { emptyMap() }
private val LocalMarkdownSelectionFragmentKey = staticCompositionLocalOf<Any?> { null }

internal data class MarkdownDeferredLayout(
    val viewportHeightPx: Int,
    val scrollInProgress: Boolean,
    val scrollOffsetPx: () -> Int,
    val requestedOwnerKeys: Set<Any>,
    val onMaterializeOwner: (Any) -> Boolean,
)

internal val LocalMarkdownDeferredLayout = staticCompositionLocalOf<MarkdownDeferredLayout?> { null }
private val LocalMarkdownSelectionRetentionKey = staticCompositionLocalOf<Any?> { null }

@Composable
private fun MarkdownSelectionScope(key: Any, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMarkdownSelectionFragmentKey provides key) {
        CjkSelectionScope(
            ownerKey = key,
            retentionKey = LocalMarkdownSelectionRetentionKey.current ?: key,
            content = content,
        )
    }
}

@Composable
private fun rememberMarkdownParagraphMeasurer(): ParagraphMeasurer =
    LocalMarkdownParagraphMeasurer.current ?: rememberParagraphMeasurer()

private sealed interface MarkdownListMarker {
    data class Text(val value: String) : MarkdownListMarker
    data class Task(val checked: Boolean) : MarkdownListMarker
}

/** Renders a host-adapted Markdown document without owning parsing or scrolling. */
@Composable
fun TiqianMarkdown(
    document: MarkdownRenderDocument,
    modifier: Modifier = Modifier,
    style: MarkdownStyle = rememberMarkdownStyle(),
    slots: MarkdownBlockSlots = DefaultMarkdownBlockSlots,
    inlineSlots: MarkdownInlineSlots = DefaultMarkdownInlineSlots,
    onLinkClick: ((String) -> Unit)? = null,
    onFootnoteClick: ((String) -> Unit)? = null,
    footnotePlacement: MarkdownFootnotePlacement = MarkdownFootnotePlacement.AfterBlock,
    codeHighlighter: MarkdownCodeHighlighter = DefaultMarkdownCodeHighlighter,
) {
    val density = LocalDensity.current
    val deferredLayout = LocalMarkdownDeferredLayout.current
    val placedBlocks = remember(document, footnotePlacement) {
        markdownTraceSection("TiqianMarkdown.placeFootnotes") {
            document.placeFootnotes(footnotePlacement)
        }
    }
    val footnoteOwnerKeys = remember(placedBlocks) {
        placedBlocks.filterIsInstance<MarkdownFootnoteDefinition>()
            .associate { it.label to it.metadata.key }
    }
    val footnoteNavigationState = rememberMarkdownFootnoteNavigationState(
        materializeDefinition = { label ->
            footnoteOwnerKeys[label]?.let { key -> deferredLayout?.onMaterializeOwner?.invoke(key) } == true
        },
    )
    val imageGallery = remember(placedBlocks) {
        markdownTraceSection("TiqianMarkdown.imageGallery") {
            placedBlocks.markdownImageGallery()
        }
    }
    val measurementSession = remember(
        document,
        style,
        density.density,
        density.fontScale,
    ) {
        ParagraphMeasurementSession()
    }
    val paragraphMeasurer = rememberParagraphMeasurer(session = measurementSession)
    val containsMath = remember(placedBlocks) {
        markdownTraceSection("TiqianMarkdown.containsMath") {
            placedBlocks.containsMarkdownMath()
        }
    }
    val mathRuntime = if (containsMath) rememberMarkdownMathRuntime(style) else null
    CompositionLocalProvider(
        LocalMarkdownFootnoteNavigationState provides footnoteNavigationState,
        LocalMarkdownImageGallery provides imageGallery,
        LocalMarkdownCodeHighlighter provides codeHighlighter,
        LocalMarkdownParagraphMeasurer provides paragraphMeasurer,
        LocalMarkdownMathRuntime provides mathRuntime,
    ) {
        BoxWithConstraints(modifier) {
            val bodyCoreTextStyle = remember(style.body, density) {
                style.body.toCjkTextStyle().toCoreTextStyle(density)
            }
            val availableWidthPx = with(density) { maxWidth.toPx() }
            val proseWidth = if (style.proseMeasure.enabled) {
                availableWidthPx.takeIf(Float::isFinite)?.let { width ->
                    val availableCells = width / bodyCoreTextStyle.fontSize
                    val resolvedCells = resolveMarkdownProseMeasureCells(availableCells, style.proseMeasure)
                    with(density) { (resolvedCells * bodyCoreTextStyle.fontSize).toDp() }
                }
            } else {
                null
            }
            val estimatedBlockHeights = remember(
                placedBlocks,
                style,
                maxWidth,
                density.density,
                density.fontScale,
                deferredLayout != null,
            ) {
                if (deferredLayout == null) {
                    emptyList()
                } else {
                    val widthDp = maxWidth.value.coerceAtLeast(1f)
                    markdownTraceSection("TiqianMarkdown.estimateHeights") {
                        placedBlocks.map { block ->
                            estimateMarkdownBlockHeightDp(block, widthDp, style)
                        }
                    }
                }
            }
            if (deferredLayout == null) {
                MarkdownBlocks(
                    blocks = placedBlocks,
                    modifier = Modifier.fillMaxWidth(),
                    style = style,
                    slots = slots,
                    inlineSlots = inlineSlots,
                    onLinkClick = onLinkClick,
                    onFootnoteClick = onFootnoteClick,
                    compact = false,
                    topLevelProseWidth = proseWidth,
                )
            } else {
                DeferredMarkdownBlocks(
                    blocks = placedBlocks,
                    estimatedHeightsDp = estimatedBlockHeights,
                    deferredLayout = deferredLayout,
                    modifier = Modifier.fillMaxWidth(),
                    style = style,
                    slots = slots,
                    inlineSlots = inlineSlots,
                    onLinkClick = onLinkClick,
                    onFootnoteClick = onFootnoteClick,
                    topLevelProseWidth = proseWidth,
                    mathRuntime = mathRuntime,
                    prelayoutWidth = proseWidth ?: maxWidth,
                    measurementSession = measurementSession,
                )
            }
        }
    }
}

internal fun deferredMarkdownBlockIndices(
    estimatedHeightsDp: List<Float>,
    density: Float,
    visibleTopPx: Float,
    visibleBottomPx: Float,
    viewportHeightPx: Int,
    // Lookahead belongs to DeferredMarkdownBlocks' background pre-layout. Composing another half
    // viewport synchronously made the 205-block offline fixture lay out 23 paragraphs before first
    // draw; keep this range to blocks that actually intersect the viewport.
    prefetchViewports: Float = 0f,
): IntRange {
    if (estimatedHeightsDp.isEmpty()) return IntRange.EMPTY
    val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
    val prefetchPx = viewportHeightPx.coerceAtLeast(1) * prefetchViewports
    val targetTopDp = (visibleTopPx - prefetchPx).coerceAtLeast(0f) / safeDensity
    val targetBottomDp = (visibleBottomPx + prefetchPx).coerceAtLeast(0f) / safeDensity
    var topDp = 0f
    var first = -1
    var last = -1
    estimatedHeightsDp.forEachIndexed { index, heightDp ->
        val bottomDp = topDp + heightDp.coerceAtLeast(0f)
        if (bottomDp >= targetTopDp && topDp <= targetBottomDp) {
            if (first < 0) first = index
            last = index
        }
        topDp = bottomDp
    }
    return if (first < 0) IntRange.EMPTY else first..last
}

internal fun estimateMarkdownBlockHeightDp(
    block: MarkdownBlock,
    widthDp: Float,
    style: MarkdownStyle,
): Float {
    val fontSize = style.body.fontSize.value.takeIf { it.isFinite() && it > 0f } ?: 16f
    val lineHeight = style.bodyLineHeightSpOrNull()?.value
        ?.takeIf { it.isFinite() && it > 0f }
        ?: fontSize * 1.625f
    val safeWidth = widthDp.coerceAtLeast(fontSize * 8f)
    val spacing = lineHeight * style.blockSpacingBodyLines
    fun textHeight(text: MarkdownText): Float {
        val units = text.value.sumOf { character ->
            when {
                character == '\n' -> 0.0
                character.code > 0x7f -> 1.0
                character.isWhitespace() -> 0.35
                character in "ilI.,'`:;!|" -> 0.3
                character in "MW@#%&" -> 0.9
                else -> 0.6
            }
        }.toFloat()
        val forcedLines = text.value.count { it == '\n' }
        return (ceil(units * fontSize / safeWidth).toInt().coerceAtLeast(1) + forcedLines) * lineHeight
    }
    fun childrenHeight(children: List<MarkdownBlock>): Float = children
        .sumOf { estimateMarkdownBlockHeightDp(it, safeWidth, style).toDouble() }
        .toFloat() + spacing * (children.size - 1).coerceAtLeast(0)

    return when (block) {
        is MarkdownParagraph -> textHeight(block.text)
        is MarkdownHeading -> lineHeight * when (block.level.coerceIn(1, 6)) {
            1 -> 1.6f
            2 -> 1.4f
            3 -> 1.2f
            else -> 1f
        }
        is MarkdownBlockQuote -> childrenHeight(block.blocks)
        is MarkdownList -> block.items.sumOf { item ->
            childrenHeight(item.blocks).toDouble()
        }.toFloat()
        is MarkdownCodeBlock -> {
            val codeLineHeight = style.codeBlock.lineHeight.value
                .takeIf { it.isFinite() && it > 0f }
                ?: lineHeight
            block.code.lineSequence().count().coerceAtLeast(1) * codeLineHeight +
                style.codePadding.value * 2f
        }
        is MarkdownImageBlock -> {
            val ratio = block.widthPixels
                ?.takeIf { it > 0 }
                ?.let { width -> block.heightPixels?.toFloat()?.div(width) }
                ?: 0.75f
            safeWidth * ratio.coerceIn(0.25f, 2f) + if (block.caption == null) 0f else lineHeight
        }
        is MarkdownMathBlock -> lineHeight * 2f
        is MarkdownTable -> block.rows.size.coerceAtLeast(1) * lineHeight * 1.6f
        is MarkdownFootnoteDefinition -> childrenHeight(block.blocks)
        is MarkdownThematicBreak -> style.tableBorderWidth.value.coerceAtLeast(1f)
        is MarkdownHtmlBlock,
        is MarkdownCustomBlock,
        is MarkdownUnsupportedBlock,
        -> lineHeight * 1.5f
    }.coerceAtLeast(0f) + spacing
}

@Composable
private fun DeferredMarkdownBlocks(
    blocks: List<MarkdownBlock>,
    estimatedHeightsDp: List<Float>,
    deferredLayout: MarkdownDeferredLayout,
    modifier: Modifier,
    style: MarkdownStyle,
    slots: MarkdownBlockSlots,
    inlineSlots: MarkdownInlineSlots,
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
    topLevelProseWidth: Dp?,
    mathRuntime: MarkdownMathRuntime?,
    prelayoutWidth: Dp,
    measurementSession: ParagraphMeasurementSession,
) {
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val viewportHeightPx = deferredLayout.viewportHeightPx
        .takeIf { it > 0 }
        ?: windowHeightPx
    val stateHolder = rememberSaveableStateHolder()
    val measuredHeightsPx = remember(blocks, style, prelayoutWidth, density.density, density.fontScale) {
        mutableStateMapOf<Pair<Int, Any>, Int>()
    }
    val blockKeys = remember(blocks) { blocks.map { it.metadata.key } }
    val estimatedHeightsPx = remember(estimatedHeightsDp, density.density) {
        estimatedHeightsDp.map { estimated ->
            with(density) { estimated.dp.roundToPx() }.coerceAtLeast(1)
        }
    }
    var layoutWidthPx by remember(prelayoutWidth, density.density) {
        mutableIntStateOf(with(density) { prelayoutWidth.roundToPx() }.coerceAtLeast(1))
    }
    val footnoteMarkerWidths = rememberFootnoteMarkerWidths(blocks, style)
    var contentOriginInScrollPx by remember { mutableFloatStateOf(Float.NaN) }
    val currentBlockHeightsDp by remember(
        blockKeys,
        estimatedHeightsPx,
        density.density,
    ) {
        derivedStateOf {
            blocks.indices.map { index ->
                val heightPx = measuredHeightsPx[layoutWidthPx to blockKeys[index]]
                    ?: estimatedHeightsPx[index]
                with(density) { heightPx.toDp().value }
            }
        }
    }
    // `VisibleBlockRange`: collapse pixel-by-pixel ScrollState changes into a stable block range.
    // Compose only re-enters the subcomposition measure path when that range actually changes.
    val visibleRange by remember(
        viewportHeightPx,
        density.density,
        deferredLayout.scrollOffsetPx,
    ) {
        derivedStateOf {
            val scrollOffsetPx = deferredLayout.scrollOffsetPx()
            val currentTopInWindowPx = contentOriginInScrollPx
                .takeUnless { it.isNaN() }
                ?.minus(scrollOffsetPx)
                ?: -scrollOffsetPx.toFloat()
            deferredMarkdownBlockIndices(
                estimatedHeightsDp = currentBlockHeightsDp,
                density = density.density,
                visibleTopPx = -currentTopInWindowPx,
                visibleBottomPx = viewportHeightPx - currentTopInWindowPx,
                viewportHeightPx = viewportHeightPx,
            )
        }
    }
    val precomputedLayouts = remember(
        blocks,
        style,
        prelayoutWidth,
        inlineSlots,
        mathRuntime,
        density.density,
        density.fontScale,
    ) {
        mutableStateMapOf<Any, MarkdownPrecomputedLayout>()
    }
    LaunchedEffect(
        blocks,
        style,
        topLevelProseWidth,
        prelayoutWidth,
        density.density,
        density.fontScale,
        viewportHeightPx,
        mathRuntime,
        inlineSlots,
        measurementSession,
        deferredLayout.scrollInProgress,
    ) {
        if (deferredLayout.scrollInProgress) return@LaunchedEffect
        val widthPx = with(density) { prelayoutWidth.toPx() }.coerceAtLeast(1f)
        val defaultMath = inlineSlots.math == null && mathRuntime != null
        // `IdleWholeDocumentPrelayout`: visible prose never waits for this work. Once scrolling has
        // settled, retain exact layouts for the whole active document in current reading order;
        // starting after a short quiet period keeps cold start and active gestures uncontended.
        withFrameNanos { }
        delay(BackgroundPrelayoutIdleDelayMillis)
        coroutineScope {
            val wakeWorker = Channel<Unit>(capacity = Channel.CONFLATED)
            var latestVisibleRange: IntRange = IntRange.EMPTY
            var latestForward = true
            var latestHeightsPx = blocks.indices.map { blockIndex ->
                with(density) { estimatedHeightsDp[blockIndex].dp.roundToPx() }
            }

            launch {
                var previousScrollOffsetPx = deferredLayout.scrollOffsetPx()
                snapshotFlow {
                    Triple(
                        deferredLayout.scrollOffsetPx(),
                        contentOriginInScrollPx,
                        measuredHeightsPx.toMap(),
                    )
                }.collect { snapshot ->
                    val currentScrollOffsetPx = snapshot.first
                    val currentTop = snapshot.second
                        .takeUnless { it.isNaN() }
                        ?.minus(currentScrollOffsetPx)
                        ?: -currentScrollOffsetPx.toFloat()
                    val measuredByBlockKey = snapshot.third.entries.associate { (key, height) ->
                        key.second to height
                    }
                    latestHeightsPx = blocks.indices.map { blockIndex ->
                        val blockKey = blocks[blockIndex].metadata.key
                        measuredByBlockKey[blockKey]
                            ?: with(density) { estimatedHeightsDp[blockIndex].dp.roundToPx() }
                    }
                    latestVisibleRange = deferredMarkdownBlockIndices(
                        estimatedHeightsDp = latestHeightsPx.map { with(density) { it.toDp().value } },
                        density = density.density,
                        visibleTopPx = -currentTop,
                        visibleBottomPx = viewportHeightPx - currentTop,
                        viewportHeightPx = viewportHeightPx,
                    )
                    latestForward = currentScrollOffsetPx >= previousScrollOffsetPx
                    previousScrollOffsetPx = currentScrollOffsetPx
                    wakeWorker.trySend(Unit)
                }
            }

            launch {
                val workerMeasurer = createPlatformParagraphMeasurer(session = measurementSession)
                while (isActive) {
                    wakeWorker.receive()
                    val priority = backgroundPrelayoutPriorityIndices(
                        blocks = blocks,
                        visibleRange = latestVisibleRange,
                        forward = latestForward,
                        defaultMath = defaultMath,
                    )
                    val index = priority.firstOrNull { candidate ->
                        markdownSelectionKey(blocks[candidate].metadata.key) !in precomputedLayouts
                    } ?: continue
                    val block = blocks[index]
                    val key = markdownSelectionKey(block.metadata.key)
                    val result = withContext(Dispatchers.Default) {
                        precomputeMarkdownBlock(
                            block = block,
                            style = style,
                            inlineSlots = inlineSlots,
                            mathRuntime = mathRuntime,
                            density = density,
                            widthPx = widthPx,
                            measurer = workerMeasurer,
                        )
                    }
                    if (result != null) precomputedLayouts[key] = result
                    // At most one paragraph is prepared per display frame. This is idle work, not a
                    // second eager document render competing with Compose traversal and drawing.
                    withFrameNanos { }
                    wakeWorker.trySend(Unit)
                }
            }
        }
    }

    SubcomposeLayout(
        modifier = modifier
            .onSizeChanged { size ->
                if (size.width > 0 && size.width != layoutWidthPx) layoutWidthPx = size.width
            }
            .onLayoutRectChanged(
                throttleMillis = ScrollOriginCalibrationThrottleMillis,
                debounceMillis = 0,
            ) { bounds ->
                if (contentOriginInScrollPx.isNaN() || !deferredLayout.scrollInProgress) {
                    val newOrigin = bounds.positionInWindow.y.toFloat() + deferredLayout.scrollOffsetPx()
                    if (newOrigin != contentOriginInScrollPx) contentOriginInScrollPx = newOrigin
                }
            },
    ) { constraints ->
        val width = constraints.maxWidth
        val blockHeights = blocks.indices.map { index ->
            measuredHeightsPx[width to blockKeys[index]] ?: estimatedHeightsPx[index]
        }
        val blockTops = IntArray(blocks.size)
        var totalHeight = 0L
        blockHeights.forEachIndexed { index, height ->
            blockTops[index] = totalHeight.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            totalHeight += height
        }
        val childConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = buildList {
            blocks.forEachIndexed { index, block ->
                val ownerKey = blockKeys[index]
                val isRequested = ownerKey in deferredLayout.requestedOwnerKeys
                val wantsComposition = index in visibleRange || isRequested
                if (!wantsComposition) return@forEachIndexed
                val saveableKey = ownerKey.toSaveableMarkdownBlockKey()
                val placeable = subcompose(ownerKey) {
                    stateHolder.SaveableStateProvider(saveableKey) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { size ->
                                    if (
                                        size.height > 0 &&
                                        measuredHeightsPx[width to ownerKey] != size.height
                                    ) {
                                        measuredHeightsPx[width to ownerKey] = size.height
                                    }
                                },
                        ) {
                            CompositionLocalProvider(
                                LocalMarkdownSelectionRetentionKey provides ownerKey,
                                LocalMarkdownPrecomputedLayouts provides precomputedLayouts,
                            ) {
                                MarkdownBlockItem(
                                    index = index,
                                    blocks = blocks,
                                    style = style,
                                    slots = slots,
                                    inlineSlots = inlineSlots,
                                    onLinkClick = onLinkClick,
                                    onFootnoteClick = onFootnoteClick,
                                    topLevelProseWidth = topLevelProseWidth,
                                    footnoteMarkerWidth = footnoteMarkerWidths[index],
                                )
                            }
                            }
                    }
                }.single().measure(childConstraints)
                add(blockTops[index] to placeable)
            }
        }
        val resolvedHeight = totalHeight
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), resolvedHeight) {
            placeables.forEach { (top, placeable) -> placeable.placeRelative(0, top) }
        }
    }
}

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
private const val BackgroundPrelayoutIdleDelayMillis = 250L
private const val ScrollOriginCalibrationThrottleMillis = 64L

private fun org.tiqian.markdown.MarkdownNodeKey.toSaveableMarkdownBlockKey(): String =
    buildString {
        append(parserStableKey)
        path.forEach { part -> append(':').append(part) }
    }

@Composable
private fun MarkdownBlockItem(
    index: Int,
    blocks: List<MarkdownBlock>,
    style: MarkdownStyle,
    slots: MarkdownBlockSlots,
    inlineSlots: MarkdownInlineSlots,
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
    topLevelProseWidth: Dp?,
    footnoteMarkerWidth: Dp?,
) {
    val block = blocks[index]
    Column(Modifier.fillMaxWidth()) {
        markdownBlockSpacing(
            previousBlock = blocks.getOrNull(index - 1),
            block = block,
            style = style,
            compact = false,
        )?.let { spacing -> Spacer(Modifier.height(spacing)) }
        if (topLevelProseWidth == null) {
            MarkdownBlock(
                block = block,
                style = style,
                slots = slots,
                inlineSlots = inlineSlots,
                onLinkClick = onLinkClick,
                onFootnoteClick = onFootnoteClick,
                footnoteMarkerWidth = footnoteMarkerWidth,
            )
        } else {
            val blockWidthModifier = if (block is MarkdownTable) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.width(topLevelProseWidth)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(blockWidthModifier, propagateMinConstraints = true) {
                    MarkdownBlock(
                        block = block,
                        style = style,
                        slots = slots,
                        inlineSlots = inlineSlots,
                        onLinkClick = onLinkClick,
                        onFootnoteClick = onFootnoteClick,
                        footnoteMarkerWidth = footnoteMarkerWidth,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownBlocks(
    blocks: List<MarkdownBlock>,
    modifier: Modifier,
    style: MarkdownStyle,
    slots: MarkdownBlockSlots,
    inlineSlots: MarkdownInlineSlots,
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
    compact: Boolean,
    topLevelProseWidth: Dp? = null,
) {
    val density = LocalDensity.current
    val footnoteMarkerWidths = rememberFootnoteMarkerWidths(blocks, style)
    val bodyLineHeight = style.bodyLineHeightSpOrNull()?.let { lineHeight ->
        with(density) { lineHeight.toDp() }
    }
    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            markdownBlockSpacing(
                previousBlock = blocks.getOrNull(index - 1),
                block = block,
                style = style,
                compact = compact,
                bodyLineHeight = bodyLineHeight,
            )?.let { spacing -> Spacer(Modifier.height(spacing)) }
            key(block.metadata.key) {
                if (topLevelProseWidth == null) {
                    MarkdownBlock(
                        block = block,
                        style = style,
                        slots = slots,
                        inlineSlots = inlineSlots,
                        onLinkClick = onLinkClick,
                        onFootnoteClick = onFootnoteClick,
                        footnoteMarkerWidth = footnoteMarkerWidths[index],
                    )
                } else {
                    val blockWidthModifier = if (block is MarkdownTable) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.width(topLevelProseWidth)
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Box(blockWidthModifier, propagateMinConstraints = true) {
                            MarkdownBlock(
                                block = block,
                                style = style,
                                slots = slots,
                                inlineSlots = inlineSlots,
                                onLinkClick = onLinkClick,
                                onFootnoteClick = onFootnoteClick,
                                footnoteMarkerWidth = footnoteMarkerWidths[index],
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun markdownBlockSpacing(
    previousBlock: MarkdownBlock?,
    block: MarkdownBlock,
    style: MarkdownStyle,
    compact: Boolean,
    bodyLineHeight: Dp? = style.bodyLineHeightSpOrNull()?.let { lineHeight ->
        with(LocalDensity.current) { lineHeight.toDp() }
    },
): Dp? {
    if (previousBlock == null) return null
    val headingGapBodyLines = style.headingGapBodyLines(previousBlock, block)
    val adjacentLists = previousBlock is MarkdownList && block is MarkdownList
    val adjacentDisplayBlock = previousBlock.isMarkdownDisplayBlock() || block.isMarkdownDisplayBlock()
    return when {
        block is MarkdownFootnoteDefinition -> 0.dp
        bodyLineHeight != null -> {
            val ordinaryGapBodyLines = when {
                headingGapBodyLines != null -> headingGapBodyLines
                adjacentLists -> style.listBlockSpacingBodyLines
                compact -> style.compactBlockSpacingBodyLines
                else -> style.blockSpacingBodyLines
            }
            bodyLineHeight * maxOf(
                ordinaryGapBodyLines,
                if (adjacentDisplayBlock) style.displayBlockSpacingBodyLines else 0f,
            )
        }
        adjacentLists -> style.listBlockSpacing
        adjacentDisplayBlock -> style.displayBlockSpacing
        else -> if (compact) style.compactBlockSpacing else style.blockSpacing
    }
}

private fun MarkdownBlock.isMarkdownDisplayBlock(): Boolean = when (this) {
    is MarkdownCodeBlock,
    is MarkdownImageBlock,
    is MarkdownMathBlock,
    is MarkdownTable,
    -> true
    else -> false
}

internal fun List<MarkdownBlock>.containsMarkdownMath(): Boolean = any { block ->
    when (block) {
        is MarkdownMathBlock -> true
        is MarkdownParagraph -> block.text.containsMarkdownMath()
        is MarkdownHeading -> block.text.containsMarkdownMath()
        is MarkdownBlockQuote -> block.blocks.containsMarkdownMath()
        is MarkdownList -> block.items.any { it.blocks.containsMarkdownMath() }
        is MarkdownImageBlock -> block.caption?.containsMarkdownMath() == true
        is MarkdownTable ->
            block.caption?.containsMarkdownMath() == true ||
                block.rows.any { row -> row.cells.any { it.text.containsMarkdownMath() } }
        is MarkdownFootnoteDefinition -> block.blocks.containsMarkdownMath()
        is MarkdownCodeBlock,
        is MarkdownCustomBlock,
        is MarkdownHtmlBlock,
        is MarkdownThematicBreak,
        is MarkdownUnsupportedBlock,
        -> false
    }
}

private fun MarkdownText.containsMarkdownMath(): Boolean =
    spans.any { it.mark is MarkdownTextMark.InlineMath }

@Composable
private fun rememberFootnoteMarkerWidths(
    blocks: List<MarkdownBlock>,
    style: MarkdownStyle,
): Map<Int, Dp> {
    val density = LocalDensity.current
    val measurer = rememberMarkdownParagraphMeasurer()
    val footnoteTextStyle = remember(style) { style.footnoteContentTextStyle() }
    val markerTextStyle = remember(footnoteTextStyle) { footnoteTextStyle.toCjkTextStyle() }
    val coreMarkerTextStyle = remember(markerTextStyle, density) {
        markerTextStyle.toCoreTextStyle(density)
    }
    val paragraphStyle = remember {
        ParagraphStyle(
            firstLineIndent = 0.ic,
            lineLengthGrid = LineLengthGrid(enabled = false),
            lastLineAlignment = LastLineAlignment.Start,
        )
    }
    return remember(blocks, coreMarkerTextStyle, paragraphStyle, measurer, density) {
        buildMap {
            var start = 0
            while (start < blocks.size) {
                if (blocks[start] !is MarkdownFootnoteDefinition) {
                    start += 1
                    continue
                }
                var end = start + 1
                while (end < blocks.size && blocks[end] is MarkdownFootnoteDefinition) end += 1
                val widestMarker = (start until end).maxOf { index ->
                    val definition = blocks[index] as MarkdownFootnoteDefinition
                    measurer.measure(
                        text = "[${definition.index}]",
                        constraints = LayoutConstraints(maxWidth = 100_000f),
                        textStyle = coreMarkerTextStyle,
                        paragraphStyle = paragraphStyle,
                    ).size.width
                }
                val width = with(density) { widestMarker.toDp() }
                for (index in start until end) put(index, width)
                start = end
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
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
    footnoteMarkerWidth: Dp? = null,
) {
    when (block) {
        is MarkdownParagraph -> MarkdownSelectionScope(markdownSelectionKey(block.metadata.key)) {
            MarkdownTextBlock(
                block.text,
                style.body,
                style,
                inlineSlots,
                onLinkClick,
                onFootnoteClick,
            )
        }
        is MarkdownHeading -> MarkdownSelectionScope(markdownSelectionKey(block.metadata.key)) {
            MarkdownTextBlock(
                block.text,
                style.heading(block.level),
                style,
                inlineSlots,
                onLinkClick,
                onFootnoteClick,
            )
        }
        is MarkdownBlockQuote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            val quoteStyle = remember(style) { style.quoteContentStyle() }
            Spacer(
                Modifier
                    .width(style.quoteBarWidth)
                    .fillMaxHeight()
                    .background(style.quoteBarColor),
            )
            MarkdownBlocks(
                blocks = block.blocks,
                modifier = Modifier.weight(1f).padding(start = style.quoteContentPadding),
                style = quoteStyle,
                slots = slots,
                inlineSlots = inlineSlots,
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
            onLinkClick,
            onFootnoteClick,
        )
        is MarkdownCodeBlock -> slots.codeBlock?.invoke(block, style) ?: DefaultMarkdownCodeBlock(block, style)
        is MarkdownImageBlock -> slots.imageBlock?.invoke(block, style)
            ?: DefaultMarkdownImageBlock(block, style, onLinkClick, inlineSlots)
        is MarkdownMathBlock -> slots.mathBlock?.invoke(block, style) ?: DefaultMarkdownMathBlock(block, style)
        is MarkdownHtmlBlock -> slots.htmlBlock?.invoke(block, style) ?: DefaultMarkdownHtmlBlock(block, style)
        is MarkdownTable -> slots.table?.invoke(block, style)
            ?: DefaultMarkdownTable(block, style, onLinkClick, onFootnoteClick, inlineSlots)
        is MarkdownFootnoteDefinition -> slots.footnoteDefinition?.invoke(block, style)
            ?: DefaultMarkdownFootnoteDefinition(
                block,
                style,
                slots,
                onLinkClick,
                onFootnoteClick,
                inlineSlots,
                footnoteMarkerWidth,
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
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
) {
    val density = LocalDensity.current
    val markerMeasurer = rememberMarkdownParagraphMeasurer()
    val markerMeasureParagraphStyle = remember {
        ParagraphStyle(
            firstLineIndent = 0.ic,
            lineLengthGrid = LineLengthGrid(enabled = false),
        )
    }
    val markerStartParagraphStyle = remember {
        ParagraphStyle(
            firstLineIndent = 0.ic,
            lineLengthGrid = LineLengthGrid(enabled = false),
            lastLineAlignment = LastLineAlignment.Start,
        )
    }
    val markerEndParagraphStyle = remember {
        ParagraphStyle(
            firstLineIndent = 0.ic,
            lineLengthGrid = LineLengthGrid(enabled = false),
            lastLineAlignment = LastLineAlignment.End,
        )
    }
    val markerTextStyle = remember(style.body) { style.body.toCjkTextStyle() }
    val coreMarkerTextStyle = remember(markerTextStyle, density) {
        markerTextStyle.toCoreTextStyle(density)
    }
    val markers = remember(block) {
        block.items.mapIndexed { index, item ->
            when (item.task) {
                MarkdownTaskState.Checked -> MarkdownListMarker.Task(checked = true)
                MarkdownTaskState.Unchecked -> MarkdownListMarker.Task(checked = false)
                null -> MarkdownListMarker.Text(
                    if (block.ordered) "${block.startNumber + index}." else "•",
                )
            }
        }
    }
    val widestMarkerWidthPx = remember(markers, coreMarkerTextStyle, markerMeasureParagraphStyle, markerMeasurer) {
        markers.maxOfOrNull { marker ->
            when (marker) {
                is MarkdownListMarker.Task -> coreMarkerTextStyle.fontSize
                is MarkdownListMarker.Text -> markerMeasurer.measure(
                    text = marker.value,
                    constraints = LayoutConstraints(maxWidth = 100_000f),
                    textStyle = coreMarkerTextStyle,
                    paragraphStyle = markerMeasureParagraphStyle,
                ).size.width
            }
        } ?: 0f
    }
    val markerReferenceLayout = remember(coreMarkerTextStyle, markerMeasureParagraphStyle, markerMeasurer) {
        markerMeasurer.measure(
            text = "口",
            constraints = LayoutConstraints(maxWidth = coreMarkerTextStyle.fontSize * 2f),
            textStyle = coreMarkerTextStyle,
            paragraphStyle = markerMeasureParagraphStyle,
        )
    }
    val markerWidth = with(density) { widestMarkerWidthPx.toDp() }
    // This whole-cell band controls only where left-aligned bullets/tasks sit.
    // It is not the width subtracted from the body measure.
    val markerAlignmentBand = ceil(widestMarkerWidthPx / coreMarkerTextStyle.fontSize)
        .toInt()
        .coerceAtLeast(1)
        .ic
    val bodyLineHeight = style.bodyLineHeightSpOrNull()?.let { lineHeight ->
        with(density) { lineHeight.toDp() }
    }
    val tightItemSpacing = bodyLineHeight?.times(style.tightListItemSpacingBodyLines)
        ?: style.tightListItemSpacing
    val looseItemSpacing = bodyLineHeight?.times(style.listItemSpacingBodyLines)
        ?: style.listItemSpacing
    val longMeasureBreakpoint = with(density) {
        style.listLongMeasureBreakpoint.toPx(coreMarkerTextStyle.fontSize).toDp()
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val longMeasure = maxWidth > longMeasureBreakpoint
        val minimumContentIndent = if (longMeasure) style.listLongContentIndent else style.listContentIndent
        val minimumContentIndentWidth = with(density) {
            minimumContentIndent.toPx(coreMarkerTextStyle.fontSize).toDp()
        }
        val minimumMarkerRegionWidth = maxOf(markerWidth, minimumContentIndentWidth)
        val markerLeadingSpace = with(density) {
            (minimumContentIndent + -markerAlignmentBand)
                .toPx(coreMarkerTextStyle.fontSize)
                .coerceAtLeast(0f)
                .toDp()
        }

        Column {
            block.items.forEachIndexed { index, item ->
                if (index > 0) {
                    val itemSpacing = if (block.tight) tightItemSpacing else looseItemSpacing
                    if (itemSpacing > 0.dp) Spacer(Modifier.height(itemSpacing))
                }
                WholeCellContentMarkerRow(
                    minimumMarkerRegionWidth = minimumMarkerRegionWidth,
                    contentCellWidthPx = coreMarkerTextStyle.fontSize,
                    modifier = Modifier.fillMaxWidth(),
                ) { markerModifier, contentModifier ->
                    val marker = markers[index]
                    val ordinalMarker = block.ordered && marker is MarkdownListMarker.Text
                    when (marker) {
                        is MarkdownListMarker.Text -> if (ordinalMarker) {
                            MarkdownSelectionScope(markdownSelectionKey(item.metadata.key, "marker")) {
                                CjkText(
                                    text = marker.value,
                                    modifier = markerModifier,
                                    style = style.body,
                                    paragraphStyle = markerEndParagraphStyle,
                                    measurer = markerMeasurer,
                                )
                            }
                        } else {
                            Row(markerModifier) {
                                Spacer(Modifier.width(markerLeadingSpace))
                                MarkdownSelectionScope(markdownSelectionKey(item.metadata.key, "marker")) {
                                    CjkText(
                                        text = marker.value,
                                        modifier = Modifier.width(markerWidth),
                                        style = style.body,
                                        paragraphStyle = markerStartParagraphStyle,
                                        measurer = markerMeasurer,
                                    )
                                }
                            }
                        }
                        is MarkdownListMarker.Task -> Row(markerModifier) {
                            Spacer(Modifier.width(markerLeadingSpace))
                            MarkdownTaskListMarker(
                                checked = marker.checked,
                                modifier = Modifier.width(markerWidth),
                                referenceLayout = markerReferenceLayout,
                                fontSize = coreMarkerTextStyle.fontSize,
                            )
                        }
                    }
                    MarkdownBlocks(
                        blocks = item.blocks,
                        modifier = contentModifier,
                        style = style,
                        slots = slots,
                        inlineSlots = inlineSlots,
                        onLinkClick = onLinkClick,
                        onFootnoteClick = onFootnoteClick,
                        compact = block.tight,
                    )
                }
            }
        }
    }
}

internal data class WholeCellListGeometry(
    val markerRegionWidthPx: Int,
    val contentWidthPx: Int,
)

/**
 * Fits an integral number of content cells after the marker's measured width.
 * The marker itself is never character-cell rounded. Instead its layout region
 * absorbs the sub-cell remainder, so marker region + right-aligned content box
 * exactly equals the body measure.
 */
internal fun wholeCellListGeometryPx(
    bodyMeasurePx: Int,
    minimumMarkerRegionWidthPx: Int,
    contentCellWidthPx: Float,
): WholeCellListGeometry {
    val minimumMarkerWidth = minimumMarkerRegionWidthPx
        .coerceIn(0, bodyMeasurePx.coerceAtLeast(0))
    val available = (bodyMeasurePx - minimumMarkerWidth).coerceAtLeast(0)
    if (available == 0 || !contentCellWidthPx.isFinite() || contentCellWidthPx <= 0f) {
        return WholeCellListGeometry(bodyMeasurePx, available)
    }
    val cells = floor(available / contentCellWidthPx).toInt()
    val contentWidthPx = if (cells < 1) {
        available
    } else {
        floor(cells * contentCellWidthPx).toInt().coerceIn(1, available)
    }
    return WholeCellListGeometry(
        markerRegionWidthPx = bodyMeasurePx - contentWidthPx,
        contentWidthPx = contentWidthPx,
    )
}

/** Marker/content row whose content box stays on its own whole-cell grid. */
@Composable
private fun WholeCellContentMarkerRow(
    minimumMarkerRegionWidth: Dp,
    contentCellWidthPx: Float,
    modifier: Modifier = Modifier,
    content: @Composable (markerModifier: Modifier, contentModifier: Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val minimumMarkerRegionWidthPx = with(density) { minimumMarkerRegionWidth.roundToPx() }
    Layout(
        modifier = modifier,
        content = { content(Modifier, Modifier) },
    ) { measurables, constraints ->
        require(measurables.size == 2) { "WholeCellContentMarkerRow requires marker and content" }
        val rowWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val geometry = wholeCellListGeometryPx(
            bodyMeasurePx = rowWidth,
            minimumMarkerRegionWidthPx = minimumMarkerRegionWidthPx,
            contentCellWidthPx = contentCellWidthPx,
        )
        val fixedMarkerWidth = geometry.markerRegionWidthPx
        val contentWidth = geometry.contentWidthPx
        val markerPlaceable = measurables[0].measure(
            constraints.copy(minWidth = fixedMarkerWidth, maxWidth = fixedMarkerWidth, minHeight = 0),
        )
        val contentPlaceable = measurables[1].measure(
            constraints.copy(minWidth = contentWidth, maxWidth = contentWidth, minHeight = 0),
        )
        fun baselineOf(placeable: androidx.compose.ui.layout.Placeable): Int =
            placeable[FirstBaseline].takeUnless { it == AlignmentLine.Unspecified } ?: 0
        val markerBaseline = baselineOf(markerPlaceable)
        val contentBaseline = baselineOf(contentPlaceable)
        val rowBaseline = maxOf(markerBaseline, contentBaseline)
        val rowHeight = (
            rowBaseline + maxOf(
                markerPlaceable.height - markerBaseline,
                contentPlaceable.height - contentBaseline,
            )
        ).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(
            width = rowWidth,
            height = rowHeight,
            alignmentLines = mapOf(FirstBaseline to rowBaseline),
        ) {
            markerPlaceable.placeRelative(0, rowBaseline - markerBaseline)
            contentPlaceable.placeRelative(rowWidth - contentWidth, rowBaseline - contentBaseline)
        }
    }
}

@Composable
private fun MarkdownTaskListMarker(
    checked: Boolean,
    modifier: Modifier,
    referenceLayout: LayoutResult,
    fontSize: Float,
) {
    val density = LocalDensity.current
    val line = referenceLayout.lines.first()
    val metric = referenceLayout.debug.metricDecisions.firstOrNull()
    val faceAscent = metric?.layoutAscent ?: (line.baseline - line.top)
    val faceDescent = metric?.layoutDescent ?: (line.bottom - line.baseline)
    val firstBaseline = ceil(line.baseline).toInt()
    val markerHeight = ceil(referenceLayout.size.height).toInt()
    val markerSize = with(density) { (fontSize * 7f / 8f).toDp() }
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Layout(
        content = {
            Icon(
                painter = painterResource(if (checked) {
                    Res.drawable.ic_check_box_20dp
                } else {
                    Res.drawable.ic_check_box_outline_blank_20dp
                }),
                contentDescription = null,
                modifier = Modifier.requiredSize(markerSize),
                tint = disabledColor,
            )
        },
        modifier = modifier.semantics {
            role = Role.Checkbox
            toggleableState = ToggleableState(checked)
            disabled()
        },
    ) { measurables, constraints ->
        val checkbox = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val faceHeight = faceAscent + faceDescent
        val checkboxTop = line.baseline - faceAscent + (faceHeight - checkbox.height) / 2f
        layout(
            width = constraints.maxWidth,
            height = markerHeight.coerceIn(constraints.minHeight, constraints.maxHeight),
            alignmentLines = mapOf(FirstBaseline to firstBaseline),
        ) {
            checkbox.place(0, checkboxTop.roundToInt())
        }
    }
}

@Composable
private fun MarkdownTextBlock(
    text: MarkdownText,
    textStyle: TextStyle,
    markdownStyle: MarkdownStyle,
    inlineSlots: MarkdownInlineSlots,
    onLinkClick: ((String) -> Unit)?,
    onFootnoteClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    paragraphStyle: ParagraphStyle? = null,
) {
    val paragraphMeasurer = rememberMarkdownParagraphMeasurer()
    val selectionFragmentKey = LocalMarkdownSelectionFragmentKey.current
    val precomputed = selectionFragmentKey?.let(LocalMarkdownPrecomputedLayouts.current::get)
    val footnoteNavigationState = LocalMarkdownFootnoteNavigationState.current
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
                currentFootnoteNavigation.value?.bringDefinitionIntoView(label)
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
        preparedInlineMath = precomputed?.preparedInlineMath.orEmpty(),
    )
    val density = LocalDensity.current
    val tiqianInlineObjects = remember(resolved.tiqianInlineObjects, density) {
        resolved.toCjkInlineObjects(density)
    }
    val inlineDecorations = remember(resolved.decorations) {
        resolved.decorations.toCjkInlineDecorations()
    }
    val inlineBackgrounds = remember(resolved.backgrounds) {
        resolved.backgrounds.toCjkInlineBackgrounds()
    }
    val textModifier = modifier.markdownInlineInteractionSemantics(resolved.interactions)
    if (paragraphStyle == null) {
        CjkText(
            text = resolved.annotated,
            modifier = textModifier,
            style = textStyle,
            inlineObjects = tiqianInlineObjects,
            inlineBackgrounds = inlineBackgrounds,
            inlineDecorations = inlineDecorations,
            measurer = paragraphMeasurer,
            precomputedLayout = precomputed?.layout,
        )
    } else {
        CjkText(
            text = resolved.annotated,
            modifier = textModifier,
            style = textStyle,
            paragraphStyle = paragraphStyle,
            inlineObjects = tiqianInlineObjects,
            inlineBackgrounds = inlineBackgrounds,
            inlineDecorations = inlineDecorations,
            measurer = paragraphMeasurer,
            precomputedLayout = precomputed?.layout,
        )
    }
}

private fun ResolvedMarkdownText.toCjkInlineObjects(
    density: androidx.compose.ui.unit.Density,
): List<CjkInlineObject> = tiqianInlineObjects.map { resolvedObject ->
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

@Composable
fun DefaultMarkdownCodeBlock(block: MarkdownCodeBlock, style: MarkdownStyle) {
    val codeHighlighter = LocalMarkdownCodeHighlighter.current
    val codeLanguage = remember(block.language) { resolveMarkdownCodeLanguage(block.language) }
    val highlights = remember(block.code, codeLanguage, block.highlights, codeHighlighter) {
        block.highlights.ifEmpty {
            codeHighlighter.highlight(block.code, codeLanguage?.tokenizerLabel)
        }
    }
    val highlightedCode = remember(block.code, highlights, style.codeHighlight) {
        buildAnnotatedString {
            append(block.code)
            highlights.forEach { highlight ->
                val start = highlight.range.start.coerceIn(0, block.code.length)
                val end = highlight.range.endExclusive.coerceIn(start, block.code.length)
                if (start < end) addStyle(style.codeHighlight.span(highlight.kind), start, end)
            }
        }
    }
    val lineCount = remember(block.code) { block.code.count { it == '\n' } + 1 }
    val lineNumbers = remember(lineCount) { (1..lineCount).joinToString("\n") }
    val language = codeLanguage?.displayLabel
    val fileName = block.fileName?.takeIf { it.isNotBlank() }
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val copyCodeDescription = stringResource(Res.string.copy_code)
    val codeCopiedDescription = stringResource(Res.string.code_copied)
    var copied by remember(block.metadata.key) { mutableStateOf(false) }
    var copyFeedbackGeneration by remember(block.metadata.key) { mutableIntStateOf(0) }
    LaunchedEffect(copyFeedbackGeneration) {
        if (copyFeedbackGeneration > 0) {
            delay(1_400)
            copied = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(style.codeCornerRadius))
            .background(style.codeBackground),
    ) {
        val copyButtonCornerRadius = (style.codeCornerRadius - style.codeCopyOuterInset)
            .coerceAtLeast(0.dp)
        val codeMetaTextVerticalPadding = (
            style.codeMetaVerticalPadding - style.codeCopyOuterInset
        ).coerceAtLeast(0.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(style.codeMetaBackground)
                .padding(
                    start = style.codeMetaHorizontalPadding,
                    top = style.codeCopyOuterInset,
                    end = style.codeCopyOuterInset,
                    bottom = style.codeCopyOuterInset,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .padding(
                        top = codeMetaTextVerticalPadding,
                        bottom = codeMetaTextVerticalPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (fileName != null) BasicText(fileName, style = style.codeMeta)
                Spacer(Modifier.weight(1f))
                if (language != null) BasicText(language, style = style.codeLanguage)
            }
            Spacer(Modifier.width(style.codeMetaControlSpacing))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(copyButtonCornerRadius))
                    .markdownClickablePointer()
                    .clickable(role = Role.Button) {
                        clipboardManager.setText(AnnotatedString(block.code))
                        copied = true
                        copyFeedbackGeneration += 1
                    }
                    .semantics {
                        contentDescription = if (copied) codeCopiedDescription else copyCodeDescription
                    },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = copied,
                    modifier = Modifier.size(style.codeCopyIconSize),
                    contentAlignment = Alignment.Center,
                    transitionSpec = {
                        (fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.72f)) togetherWith
                            (fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.72f))
                    },
                ) { successful ->
                    Icon(
                        painter = painterResource(
                            if (successful) Res.drawable.ic_check_16dp
                            else Res.drawable.ic_content_copy_16dp,
                        ),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = style.codeMeta.color,
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(style.codePadding),
        ) {
            if (block.showLineNumbers) {
                BasicText(
                    text = lineNumbers,
                    style = style.codeBlock.copy(
                        color = style.codeLineNumberColor,
                        textAlign = TextAlign.End,
                    ),
                )
                Spacer(Modifier.width(style.codeColumnSpacing))
            }
            BasicText(text = highlightedCode, style = style.codeBlock)
        }
    }
}

@Composable
fun DefaultMarkdownImageBlock(
    block: MarkdownImageBlock,
    style: MarkdownStyle,
    onLinkClick: ((String) -> Unit)? = null,
    inlineSlots: MarkdownInlineSlots = DefaultMarkdownInlineSlots,
) {
    val label = block.description.ifBlank { block.destination }
    val imageContent = markdownImageContent(block)
    val viewerState = currentMarkdownImageViewerState()
    val imageGallery = currentMarkdownImageGallery()
    Column(Modifier.fillMaxWidth()) {
        if (imageContent == null || imageContent.loadState == MarkdownImageLoadState.Error) {
            val fallbackImageText = @Composable {
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
                        onLinkClick = onLinkClick,
                        onFootnoteClick = null,
                )
            }
            if (block.caption == null) {
                MarkdownSelectionScope(
                    markdownSelectionKey(block.metadata.key, "description"),
                    fallbackImageText,
                )
            } else {
                fallbackImageText()
            }
            block.caption?.let { caption ->
                MarkdownSelectionScope(markdownSelectionKey(block.metadata.key, "caption")) {
                    DefaultMarkdownFigureCaption(
                        caption = caption,
                        style = style,
                        inlineSlots = inlineSlots,
                        onLinkClick = onLinkClick,
                        onFootnoteClick = null,
                    )
                }
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val intrinsicSize = imageContent.intrinsicSize
                val widthHint = block.widthPixels ?: intrinsicSize?.width
                val heightHint = block.heightPixels ?: intrinsicSize?.height
                val ratio = if (widthHint != null && heightHint != null && heightHint > 0) {
                    widthHint.toFloat() / heightHint
                } else {
                    null
                }
                val imageWidth = widthHint?.dp?.coerceAtMost(maxWidth) ?: maxWidth
                val captionWidth = maxOf(imageWidth, style.figureCaptionMinimumWidthDp())
                    .coerceAtMost(maxWidth)
                val imageShape = RoundedCornerShape(style.imageCornerRadius)
                val imageModifier = Modifier
                    .width(imageWidth)
                    .then(if (ratio != null && ratio > 0f) Modifier.aspectRatio(ratio) else Modifier)
                    .border(style.imageOutlineWidth, style.imageOutlineColor, imageShape)
                    .clip(imageShape)
                    .openMarkdownImageOnClick(viewerState, block, imageGallery)
                Column(
                    modifier = Modifier.width(captionWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(imageModifier, contentAlignment = Alignment.Center) {
                        imageContent.content(Modifier.fillMaxSize())
                        if (imageContent.loadState == MarkdownImageLoadState.Loading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                progress = { imageContent.progress ?: 0f },
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                    block.caption?.let { caption ->
                        MarkdownSelectionScope(markdownSelectionKey(block.metadata.key, "caption")) {
                            DefaultMarkdownFigureCaption(
                                caption = caption,
                                style = style,
                                modifier = Modifier.fillMaxWidth(),
                                inlineSlots = inlineSlots,
                                onLinkClick = onLinkClick,
                                onFootnoteClick = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DefaultMarkdownHtmlBlock(block: MarkdownHtmlBlock, style: MarkdownStyle) {
    BasicText(text = block.html, style = style.codeBlock)
}

@Composable
fun DefaultMarkdownTable(
    block: MarkdownTable,
    style: MarkdownStyle,
    onLinkClick: ((String) -> Unit)? = null,
    onFootnoteClick: ((String) -> Unit)? = null,
    inlineSlots: MarkdownInlineSlots = DefaultMarkdownInlineSlots,
) {
    val density = LocalDensity.current
    val tableMeasurer = rememberMarkdownParagraphMeasurer()
    val borderWidthPx = with(density) { style.tableBorderWidth.toPx() }
    val columnCount = maxOf(
        block.columnAlignments.size,
        block.rows.maxOfOrNull { it.cells.size } ?: 0,
    )
    if (columnCount == 0) {
        block.caption?.let { caption ->
            MarkdownSelectionScope(markdownSelectionKey(block.metadata.key, "caption")) {
                DefaultMarkdownCaption(
                    caption = caption,
                    style = style,
                    inlineSlots = inlineSlots,
                    onLinkClick = onLinkClick,
                    onFootnoteClick = onFootnoteClick,
                )
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tableParagraphStyle = remember {
            ParagraphStyle(
                firstLineIndent = 0.ic,
                lineLengthGrid = LineLengthGrid(enabled = false),
                lastLineAlignment = LastLineAlignment.Start,
            )
        }
        val widthResolution = remember(block, style, density, tableMeasurer, columnCount, maxWidth) {
            val horizontalPaddingPx = with(density) { style.tableCellPadding.toPx() * 2f }
            val preferredWidthCapPx = with(density) { style.tableColumnWidth.toPx() }
            val normalCoreStyle = style.tableText.toCjkTextStyle().toCoreTextStyle(density)
            val headerCoreStyle = style.tableText
                .copy(fontWeight = FontWeight.Medium)
                .toCjkTextStyle()
                .toCoreTextStyle(density)
            val preferredWidthsPx = MutableList(columnCount) { horizontalPaddingPx }
            val minimumWidthsPx = MutableList(columnCount) { horizontalPaddingPx }
            block.rows.forEach { row ->
                row.cells.forEachIndexed { columnIndex, cell ->
                    if (columnIndex >= columnCount) return@forEachIndexed
                    val coreStyle = if (cell.header) headerCoreStyle else normalCoreStyle
                    val unwrapped = tableMeasurer.measure(
                        text = cell.text.value,
                        constraints = LayoutConstraints(maxWidth = 100_000f),
                        textStyle = coreStyle,
                        paragraphStyle = tableParagraphStyle,
                    )
                    val naturalTextWidthPx = unwrapped.lines.maxOfOrNull { line ->
                        line.indent + line.visualWidth + line.hyphenAdvance
                    } ?: 0f
                    val preferredContentWidthPx = naturalTextWidthPx.coerceAtMost(
                        (preferredWidthCapPx - horizontalPaddingPx).coerceAtLeast(0f),
                    )
                    val preferredCellWidthPx = preferredContentWidthPx + horizontalPaddingPx
                    val minimumContentWidthPx = measureTableMinimumContentWidth(
                        measurer = tableMeasurer,
                        text = cell.text.value,
                        textStyle = coreStyle,
                        paragraphStyle = tableParagraphStyle,
                        preferredContentWidth = preferredContentWidthPx,
                    )
                    val readableContentWidthPx = style.tableReadableColumnWidth
                        .toPx(coreStyle.fontSize)
                        .coerceAtMost(preferredContentWidthPx)
                    val minimumCellWidthPx = maxOf(
                        minimumContentWidthPx,
                        readableContentWidthPx,
                    ) + horizontalPaddingPx
                    preferredWidthsPx[columnIndex] = maxOf(
                        preferredWidthsPx[columnIndex],
                        preferredCellWidthPx,
                    )
                    minimumWidthsPx[columnIndex] = maxOf(
                        minimumWidthsPx[columnIndex],
                        minimumCellWidthPx,
                    )
                }
            }
            resolveMarkdownTableWidths(
                preferredWidths = preferredWidthsPx,
                minimumWidths = minimumWidthsPx,
                availableWidth = with(density) { maxWidth.toPx() }
                    .takeIf(Float::isFinite)
                    ?: preferredWidthsPx.sum(),
            )
        }
        val tableWidth = with(density) { widthResolution.tableWidth.toDp() }
        val cellWidths = widthResolution.columnWidths.map { width ->
            with(density) { width.toDp() }
        }
        val centeringWidth = maxOf(maxWidth, tableWidth)
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Box(Modifier.width(centeringWidth)) {
                Column(
                    Modifier
                        .width(tableWidth)
                        .align(Alignment.TopCenter),
                ) {
                    block.caption?.let { caption ->
                        MarkdownSelectionScope(markdownSelectionKey(block.metadata.key, "caption")) {
                            DefaultMarkdownCaption(
                                caption = caption,
                                style = style,
                                inlineSlots = inlineSlots,
                                onLinkClick = onLinkClick,
                                onFootnoteClick = onFootnoteClick,
                            )
                        }
                        Spacer(Modifier.height(style.captionSpacing()))
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(style.tableCornerRadius))
                            .border(
                                style.tableBorderWidth,
                                style.tableBorderColor,
                                RoundedCornerShape(style.tableCornerRadius),
                            ),
                    ) {
                        block.rows.forEachIndexed { rowIndex, row ->
                            Row(Modifier.height(IntrinsicSize.Min)) {
                                row.cells.forEachIndexed { columnIndex, cell ->
                                    val cellModifier = Modifier
                                        .width(cellWidths[columnIndex])
                                        .fillMaxHeight()
                                        .let { modifier ->
                                            if (cell.header) {
                                                modifier.background(style.tableHeaderBackground)
                                            } else {
                                                modifier
                                            }
                                        }
                                        .drawWithContent {
                                            drawContent()
                                            if (rowIndex > 0) {
                                                drawLine(
                                                    color = style.tableBorderColor,
                                                    start = Offset(0f, borderWidthPx / 2f),
                                                    end = Offset(size.width, borderWidthPx / 2f),
                                                    strokeWidth = borderWidthPx,
                                                )
                                            }
                                            if (columnIndex > 0) {
                                                drawLine(
                                                    color = style.tableBorderColor,
                                                    start = Offset(borderWidthPx / 2f, 0f),
                                                    end = Offset(borderWidthPx / 2f, size.height),
                                                    strokeWidth = borderWidthPx,
                                                )
                                            }
                                        }
                                        .padding(style.tableCellPadding)
                                    val textStyle = style.tableText.copy(
                                        fontWeight = if (cell.header) FontWeight.Medium else style.tableText.fontWeight,
                                        textAlign = when (cell.alignment) {
                                            MarkdownTableAlignment.Start,
                                            MarkdownTableAlignment.Unspecified,
                                            -> TextAlign.Start
                                            MarkdownTableAlignment.Center -> TextAlign.Center
                                            MarkdownTableAlignment.End -> TextAlign.End
                                        },
                                    )
                                    MarkdownSelectionScope(markdownSelectionKey(cell.metadata.key, "cell")) {
                                        MarkdownTextBlock(
                                            text = cell.text,
                                            textStyle = textStyle,
                                            markdownStyle = style,
                                            inlineSlots = inlineSlots,
                                            onLinkClick = onLinkClick,
                                            onFootnoteClick = onFootnoteClick,
                                            modifier = cellModifier,
                                            paragraphStyle = tableParagraphStyle,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun measureTableMinimumContentWidth(
    measurer: ParagraphMeasurer,
    text: String,
    textStyle: CoreTextStyle,
    paragraphStyle: ParagraphStyle,
    preferredContentWidth: Float,
): Float {
    if (text.isEmpty() || preferredContentWidth <= 0f) return 0f
    var lower = 0f
    var upper = preferredContentWidth
    repeat(10) {
        val candidate = (lower + upper) / 2f
        val result = measurer.measure(
            text = text,
            constraints = LayoutConstraints(maxWidth = candidate.coerceAtLeast(1f)),
            textStyle = textStyle,
            paragraphStyle = paragraphStyle,
        )
        val requiredWidth = result.lines.maxOfOrNull { line ->
            line.indent + line.visualWidth + line.hyphenAdvance
        } ?: 0f
        if (requiredWidth <= candidate + 0.5f) {
            upper = candidate
        } else {
            lower = candidate
        }
    }
    return upper
}

@Composable
fun DefaultMarkdownFootnoteDefinition(
    block: MarkdownFootnoteDefinition,
    style: MarkdownStyle,
    slots: MarkdownBlockSlots = DefaultMarkdownBlockSlots,
    onLinkClick: ((String) -> Unit)? = null,
    onFootnoteClick: ((String) -> Unit)? = null,
    inlineSlots: MarkdownInlineSlots = DefaultMarkdownInlineSlots,
    markerWidth: Dp? = null,
) {
    val footnoteNavigationState = LocalMarkdownFootnoteNavigationState.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    DisposableEffect(footnoteNavigationState, block.label, bringIntoViewRequester) {
        footnoteNavigationState?.registerDefinition(block.label, bringIntoViewRequester)
        onDispose {
            footnoteNavigationState?.unregisterDefinition(block.label, bringIntoViewRequester)
        }
    }
    val footnoteStyle = remember(style) {
        style.copy(body = style.footnoteContentTextStyle())
    }
    val density = LocalDensity.current
    val markerMeasurer = rememberMarkdownParagraphMeasurer()
    val markerTextStyle = remember(footnoteStyle.body) { footnoteStyle.body.toCjkTextStyle() }
    val coreMarkerTextStyle = remember(markerTextStyle, density) {
        markerTextStyle.toCoreTextStyle(density)
    }
    val markerParagraphStyle = remember {
        ParagraphStyle(
            firstLineIndent = 0.ic,
            lineLengthGrid = LineLengthGrid(enabled = false),
            lastLineAlignment = LastLineAlignment.Start,
        )
    }
    val singleMarkerWidth = remember(block.index, coreMarkerTextStyle, markerParagraphStyle, markerMeasurer, density) {
        val measuredWidth = markerMeasurer.measure(
            text = "[${block.index}]",
            constraints = LayoutConstraints(maxWidth = 100_000f),
            textStyle = coreMarkerTextStyle,
            paragraphStyle = markerParagraphStyle,
        ).size.width
        with(density) { measuredWidth.toDp() }
    }
    val resolvedMarkerWidth = markerWidth ?: singleMarkerWidth
    WholeCellContentMarkerRow(
        minimumMarkerRegionWidth = resolvedMarkerWidth,
        contentCellWidthPx = coreMarkerTextStyle.fontSize,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester),
    ) { markerModifier, contentModifier ->
        MarkdownSelectionScope(markdownSelectionKey(block.metadata.key, "marker")) {
            CjkText(
                text = "[${block.index}]",
                modifier = markerModifier,
                style = footnoteStyle.body,
                paragraphStyle = markerParagraphStyle,
                measurer = markerMeasurer,
            )
        }
        MarkdownBlocks(
            blocks = block.blocks,
            modifier = contentModifier,
            style = footnoteStyle,
            slots = slots,
            inlineSlots = inlineSlots,
            onLinkClick = onLinkClick,
            onFootnoteClick = onFootnoteClick,
            compact = true,
        )
    }
}

@Composable
private fun MarkdownStyle.captionSpacing() = bodyLineHeightSpOrNull()?.let { lineHeight ->
    with(LocalDensity.current) { (lineHeight * 1f / 4f).toDp() }
} ?: 6.dp

@Composable
private fun MarkdownStyle.captionHorizontalIndentDp() = with(LocalDensity.current) {
    val bodyFontSizePx = body.toCjkTextStyle().toCoreTextStyle(this).fontSize
    captionHorizontalIndent.toPx(bodyFontSizePx).toDp()
}

@Composable
private fun MarkdownStyle.figureCaptionMinimumWidthDp() = with(LocalDensity.current) {
    val bodyFontSizePx = body.toCjkTextStyle().toCoreTextStyle(this).fontSize
    figureCaptionMinimumWidth.toPx(bodyFontSizePx).toDp()
}

/** Shared caption presentation for host-owned figures and the built-in image/table renderers. */
@Composable
fun DefaultMarkdownCaption(
    caption: MarkdownText,
    style: MarkdownStyle,
    modifier: Modifier = Modifier,
    inlineSlots: MarkdownInlineSlots = DefaultMarkdownInlineSlots,
    onLinkClick: ((String) -> Unit)? = null,
    onFootnoteClick: ((String) -> Unit)? = null,
) {
    MarkdownTextBlock(
        text = caption,
        textStyle = style.caption,
        markdownStyle = style,
        inlineSlots = inlineSlots,
        onLinkClick = onLinkClick,
        onFootnoteClick = onFootnoteClick,
        modifier = modifier.padding(horizontal = style.captionHorizontalIndentDp()),
    )
}

/** Shared figure-caption rhythm for host-owned images and the built-in image renderer. */
@Composable
fun DefaultMarkdownFigureCaption(
    caption: MarkdownText,
    style: MarkdownStyle,
    modifier: Modifier = Modifier,
    inlineSlots: MarkdownInlineSlots = DefaultMarkdownInlineSlots,
    onLinkClick: ((String) -> Unit)? = null,
    onFootnoteClick: ((String) -> Unit)? = null,
) {
    Spacer(Modifier.height(style.captionSpacing()))
    DefaultMarkdownCaption(
        caption = caption,
        style = style,
        modifier = modifier,
        inlineSlots = inlineSlots,
        onLinkClick = onLinkClick,
        onFootnoteClick = onFootnoteClick,
    )
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
