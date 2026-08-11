package org.tiqian.markdown.compose


import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.GenericFontFamily
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Path
import org.jetbrains.skia.Point
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.shaper.RunHandler
import org.jetbrains.skia.shaper.RunInfo
import org.jetbrains.skia.shaper.Shaper
import org.jetbrains.skia.shaper.ShapingOptions
import org.jetbrains.skia.shaper.TrivialBidiRunIterator
import org.jetbrains.skia.shaper.TrivialFontRunIterator
import org.jetbrains.skia.shaper.TrivialLanguageRunIterator
import org.jetbrains.skia.shaper.TrivialScriptRunIterator
import org.tiqian.font.usesLatinFace
import org.tiqian.math.core.MathFaceId
import org.tiqian.math.core.MathFontWeight
import org.tiqian.math.core.MathHostTextCapabilityIssue
import org.tiqian.math.core.MathHostTextCapabilityIssueCode
import org.tiqian.math.core.MathHostTextFaceDecision
import org.tiqian.math.core.MathRect
import org.tiqian.math.core.SourceRange
import org.tiqian.math.font.skia.SkiaMathFontFace
import org.tiqian.math.font.skia.SkiaReplayCatalog
import org.tiqian.math.font.skia.SkiaReplayFace
import org.tiqian.math.layout.MathGlyphBoundsSource
import org.tiqian.math.layout.MathTextRunProvider
import org.tiqian.math.layout.MathTextRunProviderResult
import org.tiqian.math.layout.MathTextRunRequest
import org.tiqian.math.layout.MeasuredMathGlyph
import org.tiqian.math.layout.MeasuredMathRun
import org.tiqian.math.layout.restrictedStandaloneTextCapabilityIssue
import org.tiqian.shaping.skia.SkiaSystemTypefaces
import kotlin.math.max

@Composable
internal actual fun rememberMarkdownMathTextRunProvider(
    hostTextStyle: TextStyle,
): MathTextRunProvider {
    val preferredFamily = (hostTextStyle.fontFamily as? GenericFontFamily)?.name
    val provider = remember(preferredFamily) { SkiaMarkdownMathTextRunProvider(preferredFamily) }
    DisposableEffect(provider) { onDispose(provider::close) }
    return provider
}

internal class SkiaMarkdownMathTextRunProvider(
    private val preferredFamily: String?,
) : MathTextRunProvider, SkiaReplayCatalog, AutoCloseable {
    private val shaper = Shaper.makeShaperDrivenWrapper()
    private val replayFaces = LinkedHashMap<MathFaceId, TiqianSkiaMathReplayFace>()

    override fun shapeTextAtom(request: MathTextRunRequest): MathTextRunProviderResult {
        restrictedStandaloneTextCapabilityIssue(request)?.let {
            return MathTextRunProviderResult.CapabilityIssue(it)
        }
        val locale = request.locale ?: MarkdownMathTextLocale
        val skiaStyle = FontStyle(
            request.requestedWeight.cssWeight,
            FontStyle.NORMAL.width,
            FontSlant.UPRIGHT,
        )
        val glyphs = mutableListOf<MeasuredMathGlyph>()
        var penX = 0f
        var ascent = 0f
        var descent = 0f
        var missingGlyph = false

        for (segment in markdownMathHostTextSegments(request.text, locale)) {
            val segmentText = request.text.substring(segment.range.start, segment.range.end)
            val typeface = SkiaSystemTypefaces.typeface(
                isLatin = segment.role.usesLatinFace(),
                family = preferredFamily,
                style = skiaStyle,
            ) ?: return MathTextRunProviderResult.CapabilityIssue(
                MathHostTextCapabilityIssue(
                    code = MathHostTextCapabilityIssueCode.NonReplayableHostTextRun,
                    message = "No replayable Tiqian host typeface is available for ${segment.role}",
                    sourceRange = SourceRange(
                        request.sourceRange.start + segment.range.start,
                        request.sourceRange.start + segment.range.end,
                    ),
                ),
            )
            val resolvedWeight = MathFontWeight.nearest(typeface.fontStyle.weight)
            val faceId = MathFaceId(
                "tiqian-host-skia:${typeface.familyName}:${typeface.fontStyle.weight}:${typeface.fontStyle.slant}",
            )
            replayFaces.getOrPut(faceId) {
                TiqianSkiaMathReplayFace(faceId, typeface, resolvedWeight)
            }
            val font = Font(typeface, request.fontSizePx).apply { isSubpixel = true }
            val collector = HostTextCollector()
            try {
                shaper.shape(
                    segmentText,
                    TrivialFontRunIterator(segmentText, font),
                    TrivialBidiRunIterator(segmentText, 0),
                    TrivialScriptRunIterator(
                        segmentText,
                        if (segment.role.usesLatinFace()) "Latn" else "Hani",
                    ),
                    TrivialLanguageRunIterator(segmentText, locale),
                    ShapingOptions.DEFAULT,
                    Float.MAX_VALUE,
                    collector,
                )
                val ids = collector.ids.toShortArray()
                val widths = font.getWidths(ids)
                val fallbackBounds = font.getBounds(ids)
                val clusterBoundaries = (collector.clusters + segmentText.length).distinct().sorted()
                ids.indices.forEach { index ->
                    val clusterStart = collector.clusters.getOrElse(index) { 0 }
                        .coerceIn(0, segmentText.lastIndex.coerceAtLeast(0))
                    val clusterEnd = clusterBoundaries.firstOrNull { it > clusterStart } ?: segmentText.length
                    val clusterRange = SourceRange(
                        segment.range.start + clusterStart,
                        segment.range.start + clusterEnd,
                    )
                    val pathBounds = font.getPath(ids[index])?.use { path ->
                        if (path.isEmpty) null else path.computeTightBounds()
                    }
                    val bounds = pathBounds ?: fallbackBounds[index]
                    val mathBounds = MathRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
                    val baselineOffset = collector.y[index]
                    glyphs += MeasuredMathGlyph(
                        glyphId = ids[index].toUShort(),
                        x = penX + collector.x[index],
                        advance = widths[index],
                        inkBounds = mathBounds,
                        textCluster = clusterRange.start,
                        baselineOffsetPx = baselineOffset,
                        faceId = faceId,
                        fontClass = null,
                        requestedWeight = request.requestedWeight,
                        resolvedWeight = resolvedWeight,
                        fallbackReason = null,
                        hostTextDecision = MathHostTextFaceDecision(
                            sourceRange = SourceRange(
                                request.sourceRange.start + clusterRange.start,
                                request.sourceRange.start + clusterRange.endExclusive,
                            ),
                            clusterRangeUtf16 = clusterRange,
                            hostRole = segment.role.name,
                            faceId = faceId,
                            fontKey = typeface.familyName,
                            requestedWeight = request.requestedWeight,
                            resolvedWeight = resolvedWeight,
                            selectionReason = "TiqianSkiaSystemTypeface",
                            substitutionReason = if (resolvedWeight == request.requestedWeight) null
                                else "RequestedWeightUnavailableInHostTypeface",
                        ),
                    )
                    ascent = max(ascent, -(mathBounds.top + baselineOffset))
                    descent = max(descent, mathBounds.bottom + baselineOffset)
                    missingGlyph = missingGlyph || ids[index].toInt() == 0
                }
                penX += collector.advance
            } finally {
                font.close()
            }
        }

        return MathTextRunProviderResult.Ready(
            MeasuredMathRun(
                glyphs = glyphs,
                width = penX,
                ascent = ascent.coerceAtLeast(0f),
                descent = descent.coerceAtLeast(0f),
                missingGlyph = missingGlyph,
                boundsSource = MathGlyphBoundsSource.Outline,
            ),
        )
    }

    override fun replayFace(faceId: MathFaceId): SkiaReplayFace? = replayFaces[faceId]

    override fun constructionFace(faceId: MathFaceId): SkiaMathFontFace? = null

    override fun close() = shaper.close()
}

private class TiqianSkiaMathReplayFace(
    override val faceId: MathFaceId,
    private val typeface: Typeface,
    override val resolvedWeight: MathFontWeight,
) : SkiaReplayFace {
    override fun font(fontSizePx: Float): Font = Font(typeface, fontSizePx).apply { isSubpixel = true }

    override fun glyphPath(glyphId: UShort, fontSizePx: Float): Path? =
        font(fontSizePx).use { it.getPath(glyphId.toShort()) }

    override fun canReplayGlyph(glyphId: UShort): Boolean =
        glyphId.toInt() != 0 && glyphId.toInt() < typeface.glyphsCount
}

private class HostTextCollector : RunHandler {
    val ids = mutableListOf<Short>()
    val x = mutableListOf<Float>()
    val y = mutableListOf<Float>()
    val clusters = mutableListOf<Int>()
    var advance = 0f
        private set
    private var pen = 0f

    override fun beginLine() = Unit
    override fun runInfo(info: RunInfo?) = Unit
    override fun commitRunInfo() = Unit
    override fun runOffset(info: RunInfo?): Point = Point(pen, 0f)

    override fun commitRun(
        info: RunInfo?,
        glyphs: ShortArray?,
        positions: Array<Point?>?,
        clusters: IntArray?,
    ) {
        if (info == null || glyphs == null || positions == null) return
        glyphs.forEachIndexed { index, glyph ->
            ids += glyph
            x += positions[index]?.x ?: pen
            y += positions[index]?.y ?: 0f
            this.clusters += clusters?.getOrElse(index) { 0 } ?: 0
        }
        pen += info.advanceX
        advance = pen
    }

    override fun commitLine() = Unit
}
