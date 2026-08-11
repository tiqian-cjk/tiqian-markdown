package org.tiqian.markdown

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.dp
import org.tiqian.compose.CjkInlineObject
import org.tiqian.compose.CjkInlineObjectBoundary
import org.tiqian.compose.CjkInlineObjectPreferredStretch
import org.tiqian.compose.CjkInlineObjectPreferredStretchKind
import org.tiqian.compose.CjkText
import org.tiqian.compose.ParagraphMeasurer
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
import org.tiqian.markdown.generated.resources.Res
import org.tiqian.markdown.generated.resources.code_copied
import org.tiqian.markdown.generated.resources.ic_check_box_20dp
import org.tiqian.markdown.generated.resources.ic_check_box_outline_blank_20dp
import org.tiqian.markdown.generated.resources.ic_check_16dp
import org.tiqian.markdown.generated.resources.ic_content_copy_16dp
import org.tiqian.markdown.generated.resources.copy_code
import kotlinx.coroutines.delay
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
    val footnoteNavigationState = rememberMarkdownFootnoteNavigationState()
    val placedBlocks = remember(document, footnotePlacement) {
        document.placeFootnotes(footnotePlacement)
    }
    val imageGallery = remember(placedBlocks) { placedBlocks.markdownImageGallery() }
    CompositionLocalProvider(
        LocalMarkdownFootnoteNavigationState provides footnoteNavigationState,
        LocalMarkdownImageGallery provides imageGallery,
        LocalMarkdownCodeHighlighter provides codeHighlighter,
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
            if (index > 0) {
                val previousBlock = blocks[index - 1]
                val headingGapBodyLines = style.headingGapBodyLines(previousBlock, block)
                val adjacentLists = previousBlock is MarkdownList && block is MarkdownList
                val adjacentDisplayBlock = previousBlock.isMarkdownDisplayBlock() ||
                    block.isMarkdownDisplayBlock()
                val spacing = when {
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
                Spacer(Modifier.height(spacing))
            }
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

private fun MarkdownBlock.isMarkdownDisplayBlock(): Boolean = when (this) {
    is MarkdownCodeBlock,
    is MarkdownImageBlock,
    is MarkdownMathBlock,
    is MarkdownTable,
    -> true
    else -> false
}

@Composable
private fun rememberFootnoteMarkerWidths(
    blocks: List<MarkdownBlock>,
    style: MarkdownStyle,
): Map<Int, Dp> {
    val density = LocalDensity.current
    val measurer = rememberParagraphMeasurer()
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
        is MarkdownParagraph -> MarkdownTextBlock(
            block.text,
            style.body,
            style,
            inlineSlots,
            onLinkClick,
            onFootnoteClick,
        )
        is MarkdownHeading -> MarkdownTextBlock(
            block.text,
            style.heading(block.level),
            style,
            inlineSlots,
            onLinkClick,
            onFootnoteClick,
        )
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
    val markerMeasurer = rememberParagraphMeasurer()
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
                            CjkText(
                                text = marker.value,
                                modifier = markerModifier,
                                style = style.body,
                                paragraphStyle = markerEndParagraphStyle,
                                measurer = markerMeasurer,
                            )
                        } else {
                            Row(markerModifier) {
                                Spacer(Modifier.width(markerLeadingSpace))
                                CjkText(
                                    text = marker.value,
                                    modifier = Modifier.width(markerWidth),
                                    style = style.body,
                                    paragraphStyle = markerStartParagraphStyle,
                                    measurer = markerMeasurer,
                                )
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
    )
    val density = LocalDensity.current
    val tiqianInlineObjects = remember(resolved.tiqianInlineObjects, density) {
        resolved.tiqianInlineObjects.map { resolvedObject ->
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
        )
    }
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
            block.caption?.let { caption ->
                DefaultMarkdownFigureCaption(
                    caption = caption,
                    style = style,
                    inlineSlots = inlineSlots,
                    onLinkClick = onLinkClick,
                    onFootnoteClick = null,
                )
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
    val tableMeasurer = rememberParagraphMeasurer()
    val borderWidthPx = with(density) { style.tableBorderWidth.toPx() }
    val columnCount = maxOf(
        block.columnAlignments.size,
        block.rows.maxOfOrNull { it.cells.size } ?: 0,
    )
    if (columnCount == 0) {
        block.caption?.let { caption ->
            DefaultMarkdownCaption(
                caption = caption,
                style = style,
                inlineSlots = inlineSlots,
                onLinkClick = onLinkClick,
                onFootnoteClick = onFootnoteClick,
            )
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
                        DefaultMarkdownCaption(
                            caption = caption,
                            style = style,
                            inlineSlots = inlineSlots,
                            onLinkClick = onLinkClick,
                            onFootnoteClick = onFootnoteClick,
                        )
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
    val markerMeasurer = rememberParagraphMeasurer()
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
        CjkText(
            text = "[${block.index}]",
            modifier = markerModifier,
            style = footnoteStyle.body,
            paragraphStyle = markerParagraphStyle,
            measurer = markerMeasurer,
        )
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
