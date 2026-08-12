package org.tiqian.markdown.compose

import org.tiqian.markdown.MarkdownMathBlock
import org.tiqian.markdown.MarkdownTextMark
import org.tiqian.markdown.MarkdownTextRange

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.tiqian.math.compose.TiqianMath
import org.tiqian.math.compose.TiqianMathFormula
import org.tiqian.math.compose.TiqianMathFormulaCanvas
import org.tiqian.math.compose.rememberLeteMathFontFace
import org.tiqian.math.compose.rememberMathFontFace
import org.tiqian.math.compose.rememberPackagedMathFontFamily
import org.tiqian.math.compose.rememberTiqianMathFormula
import org.tiqian.math.core.MathAdjustmentPriority
import org.tiqian.math.core.MathAtomClass
import org.tiqian.math.core.MathBreakKind
import org.tiqian.math.core.MathInlineFragment
import org.tiqian.math.core.MathMode
import org.tiqian.math.layout.MathComposeFontFace

/** Library-owned math-font selection. The concrete formula engine stays an implementation detail. */
sealed interface MarkdownMathFont {
    /** Bundled sans-serif OpenType MATH font. */
    data object LeteSansMath : MarkdownMathFont

    /** A family prebaked from the host application's `tiqianMathFonts` Gradle declaration. */
    data class Packaged(
        val familyId: String,
    ) : MarkdownMathFont

    /** A host-provided OpenType font containing a MATH table. */
    data class OpenTypeResource(
        val resource: FontResource,
    ) : MarkdownMathFont

    /** A preloaded OpenType font, useful for downloaded or user-selected fonts. */
    class LoadedOpenType(
        val bytes: ByteArray,
        val family: FontFamily,
    ) : MarkdownMathFont
}

data class MarkdownMathStyle(
    val font: MarkdownMathFont = MarkdownMathFont.LeteSansMath,
    val color: Color = Color.Unspecified,
    val inlineFontSize: TextUnit = TextUnit.Unspecified,
    val displayFontSize: TextUnit = TextUnit.Unspecified,
    val displayScale: Float = 1f,
    val blockCornerRadius: Dp = 8.dp,
) {
    init {
        require(displayScale > 0f) { "displayScale must be positive" }
    }
}

/** Physical inline-object metrics. Baseline is measured downwards from the object's top. */
data class MarkdownInlineMetrics(
    val widthPx: Float,
    val heightPx: Float,
    val baselineFromTopPx: Float,
) {
    val ascentPx: Float get() = baselineFromTopPx
    val descentPx: Float get() = heightPx - baselineFromTopPx

    init {
        require(widthPx >= 0f) { "widthPx must not be negative" }
        require(heightPx >= 0f) { "heightPx must not be negative" }
        require(baselineFromTopPx in 0f..heightPx) { "baseline must be inside the object" }
    }
}

/** Default library renderer for inline math. */
val DefaultMarkdownMathInlineSlot:
    @Composable (MarkdownTextMark.InlineMath, MarkdownStyle, TextStyle) -> MarkdownInlineContent? =
    { mark, style, hostTextStyle ->
        defaultMarkdownInlineMath(mark, style, hostTextStyle)
    }

@Composable
private fun defaultMarkdownInlineMath(
    mark: MarkdownTextMark.InlineMath,
    style: MarkdownStyle,
    hostTextStyle: TextStyle,
): MarkdownInlineContent? {
    val expression = mark.expression
    if (expression.isBlank()) return null

    val fontFace = rememberMarkdownMathFontFace(style.math.font)
    val textRunProvider = rememberMarkdownMathTextRunProvider(hostTextStyle)
    val fontSize = resolveFontSize(
        preferred = style.math.inlineFontSize,
        fallback = hostTextStyle.fontSize,
        lastFallback = style.body.fontSize,
    )
    val color = resolveMathColor(style.math.color, hostTextStyle.color, style.body.color)
    val formula = rememberTiqianMathFormula(
        source = expression,
        mode = MathMode.Inline,
        style = hostTextStyle.copy(fontSize = fontSize, color = color),
        fontFace = fontFace,
        textRunProvider = textRunProvider,
        textLocale = MarkdownMathTextLocale,
    )
    val layout = formula.layoutResult ?: return null
    val density = LocalDensity.current
    val fontSizePx = with(density) { fontSize.toPx() }
    val sourceRanges = layout.partitionSource(expression)
    val fragments = if (sourceRanges != null) groupFragmentIndices(layout.fragments).map { group ->
        val groupSource = MarkdownTextRange(sourceRanges[group.first].start, sourceRanges[group.last].endExclusive)
        val sourceFragment = expression.substring(groupSource.start, groupSource.endExclusive)
        MarkdownInlineFragment(
            sourceRange = groupSource,
            content = tiqianMathInlineContent(
                sourceExpression = sourceFragment,
                formula = formula,
                fragmentIndices = group,
                trailingFragment = layout.fragments[group.last],
                density = density,
                fontSizePx = fontSizePx,
            ),
        )
    } else emptyList()

    return tiqianMathInlineContent(
        sourceExpression = expression,
        formula = formula,
        fragmentIndices = null,
        trailingFragment = null,
        density = density,
        fontSizePx = fontSizePx,
        layoutFragments = fragments,
    )
}

/**
 * Binds engine fragments into inline units: a closing delimiter or punctuation joins the preceding
 * unit, and an atom following an opening delimiter joins it, so an inline object never starts with a
 * mark kinsoku forbids at a wrapped line head (`,` `)` `]`). Operators stay their own unit and carry
 * the break after them. The engine's per-atom fragment model is unchanged; grouping is consumer-side.
 */
private fun groupFragmentIndices(fragments: List<MathInlineFragment>): List<IntRange> {
    val groups = mutableListOf<MutableList<Int>>()
    fragments.forEachIndexed { index, fragment ->
        val previous = fragments.getOrNull(index - 1)
        val bindsToPrevious = groups.isNotEmpty() && (
            fragment.atomClass == MathAtomClass.Closing ||
                fragment.atomClass == MathAtomClass.Punctuation ||
                previous?.atomClass == MathAtomClass.Opening
        )
        if (bindsToPrevious) groups.last().add(index) else groups.add(mutableListOf(index))
    }
    return groups.map { it.first()..it.last() }
}

@Composable
private fun tiqianMathInlineContent(
    sourceExpression: String,
    formula: TiqianMathFormula,
    fragmentIndices: IntRange?,
    trailingFragment: MathInlineFragment?,
    density: androidx.compose.ui.unit.Density,
    fontSizePx: Float,
    layoutFragments: List<MarkdownInlineFragment> = emptyList(),
): MarkdownInlineContent {
    val presentation = checkNotNull(
        if (fragmentIndices == null) formula.presentationMetrics() else formula.presentationMetrics(fragmentIndices),
    )
    val metrics = MarkdownInlineMetrics(
        widthPx = presentation.widthPx,
        heightPx = presentation.heightPx,
        baselineFromTopPx = presentation.baselineFromTopPx,
    )
    val lastFragmentIndex = formula.layoutResult?.fragments?.lastIndex
    val isFirstFragment = fragmentIndices == null || fragmentIndices.first == 0
    val isLastFragment = fragmentIndices == null || fragmentIndices.last == lastFragmentIndex
    val uniformOuterBoundary = MarkdownInlineBoundaryAdjustment(
        participatesInUniformStretch = true,
    )
    return MarkdownInlineContent(
        alternateText = sourceExpression,
        placeholder = Placeholder(
            width = with(density) { metrics.widthPx.toSp() },
            height = with(density) { metrics.heightPx.toSp() },
            // Compose BasicText has no arbitrary-baseline placeholder. The exact baseline is retained
            // in metrics for the Tiqian inline-object path instead of being discarded or guessed.
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
        ),
        metrics = metrics,
        layoutFragments = layoutFragments,
        leadingBoundary = if (isFirstFragment) {
            uniformOuterBoundary
        } else {
            MarkdownInlineBoundaryAdjustment.Fixed
        },
        trailingBoundary = if (isLastFragment) {
            uniformOuterBoundary
        } else {
            checkNotNull(trailingFragment).toMarkdownBoundary(fontSizePx)
        },
    ) {
        if (fragmentIndices == null) {
            TiqianMathFormulaCanvas(formula = formula)
        } else {
            TiqianMathFormulaCanvas(formula = formula, fragmentIndices = fragmentIndices)
        }
    }
}

/** Uniform preferred stretch each inline-math operator space justifies toward, in em. */
private const val InlineMathStretchTargetEm = 0.5f

private fun MathInlineFragment.toMarkdownBoundary(fontSizePx: Float): MarkdownInlineBoundaryAdjustment {
    val opportunity = breakAfter
    val isPunctuationBreak = opportunity?.kind == MathBreakKind.PunctuationTrailing
    return MarkdownInlineBoundaryAdjustment(
        participatesInUniformStretch = trailingGlue != org.tiqian.math.core.MathGlueAdjustment.Zero,
        preferredStretch = trailingGlue.takeIf { it.stretchPx > 0f }?.let {
            MarkdownInlinePreferredStretch(
                kind = when (it.priority) {
                    MathAdjustmentPriority.Punctuation ->
                        MarkdownInlinePreferredStretchKind.PunctuationTrailing
                    MathAdjustmentPriority.Relation ->
                        MarkdownInlinePreferredStretchKind.Relation
                    MathAdjustmentPriority.BinaryOperator ->
                        MarkdownInlinePreferredStretchKind.BinaryOperator
                    else -> return@let null
                },
                naturalWidthPx = it.naturalPx,
                // Justify every operator space toward a uniform half-em, not the per-kind TeX maximum.
                targetWidthPx = maxOf(it.naturalPx + 0.01f, InlineMathStretchTargetEm * fontSizePx),
            )
        },
        shrinkCapacityPx = trailingGlue.shrinkPx,
        // A comma stretches and shrinks but is not a wrap point, so only relation/binary breaks
        // discard their trailing space and open a line end; punctuation stays closed.
        lineEndDiscardableAdvancePx = if (opportunity != null && !isPunctuationBreak) trailingGlue.naturalPx else 0f,
        preventsLineBreak = opportunity == null || isPunctuationBreak,
    )
}

private fun org.tiqian.math.core.MathLayoutResult.partitionSource(
    expression: String,
): List<MarkdownTextRange>? {
    if (fragments.isEmpty()) return null
    val starts = buildList {
        add(0)
        fragments.drop(1).forEach { fragment ->
            add(fragment.sourceRange.start.coerceIn(0, expression.length))
        }
        add(expression.length)
    }
    if (starts.zipWithNext().any { (start, end) -> start >= end }) return null
    return starts.zipWithNext { start, end -> MarkdownTextRange(start, end) }
}

/** Default library renderer for display math. Oversized formulas remain horizontally scrollable. */
@Composable
fun DefaultMarkdownMathBlock(
    block: MarkdownMathBlock,
    style: MarkdownStyle,
) {
    val expression = block.expression.trim()
    if (expression.isEmpty()) return

    val fontFace = rememberMarkdownMathFontFace(style.math.font)
    val textRunProvider = rememberMarkdownMathTextRunProvider(style.body)
    val baseFontSize = resolveFontSize(
        preferred = style.math.displayFontSize,
        fallback = style.body.fontSize,
        lastFallback = 16.sp,
    )
    val fontSize = if (style.math.displayFontSize == TextUnit.Unspecified) {
        baseFontSize * style.math.displayScale
    } else {
        baseFontSize
    }
    val color = resolveMathColor(style.math.color, style.body.color, Color.Black)
    val containerModifier = if (style.mathBackground.isSpecified) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(style.math.blockCornerRadius))
            .background(style.mathBackground)
            .padding(style.codePadding)
    } else {
        Modifier.fillMaxWidth()
    }
    BoxWithConstraints(modifier = containerModifier) {
        val viewportWidth = maxWidth
        Box(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier.widthIn(min = viewportWidth),
                contentAlignment = Alignment.Center,
            ) {
                TiqianMath(
                    source = expression,
                    mode = MathMode.Display,
                    style = TextStyle(fontSize = fontSize, color = color),
                    softWrap = false,
                    fontFace = fontFace,
                    textRunProvider = textRunProvider,
                    textLocale = MarkdownMathTextLocale,
                )
            }
        }
    }
}

@Composable
private fun rememberMarkdownMathFontFace(font: MarkdownMathFont): MathComposeFontFace = when (font) {
    MarkdownMathFont.LeteSansMath ->
        rememberLeteMathFontFace()
    is MarkdownMathFont.Packaged -> rememberPackagedMathFontFamily(font.familyId)
    is MarkdownMathFont.OpenTypeResource -> rememberResolvedMarkdownMathResource(font.resource)
    is MarkdownMathFont.LoadedOpenType -> rememberMathFontFace(font.bytes)
}

/**
 * Resolve a resource font once at the Markdown renderer boundary.
 *
 * Passing an unresolved [MathFont.OTF] to both `rememberLatexMeasurer` and `Latex` makes those two
 * consumers start independent asynchronous loads. During either transition the predicted box can
 * come from one font while the canvas paints another. Keeping the resolved value here gives both
 * sides the same immutable font contract on every composition.
 */
@Composable
private fun rememberResolvedMarkdownMathResource(resource: FontResource): MathComposeFontFace {
    var resolved by remember(resource) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(resource) {
        val bytes = getFontResourceBytes(getSystemResourceEnvironment(), resource)
        if (bytes.isNotEmpty()) resolved = bytes
    }
    return resolved?.let { rememberMathFontFace(it) } ?: rememberLeteMathFontFace()
}

private fun resolveFontSize(
    preferred: TextUnit,
    fallback: TextUnit,
    lastFallback: TextUnit,
): TextUnit = listOf(preferred, fallback, lastFallback).firstOrNull { it.type == TextUnitType.Sp } ?: 16.sp

private fun resolveMathColor(
    preferred: Color,
    fallback: Color,
    lastFallback: Color,
): Color = when {
    preferred.isSpecified -> preferred
    fallback.isSpecified -> fallback
    else -> lastFallback
}
