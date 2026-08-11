package org.tiqian.markdown

import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.GenericFontFamily
import org.tiqian.core.Glyph
import org.tiqian.core.Rect
import org.tiqian.core.TextRange
import org.tiqian.core.TextStyle as TiqianTextStyle
import org.tiqian.font.FontCandidate
import org.tiqian.font.FontDecision
import org.tiqian.math.core.MathFaceId
import org.tiqian.math.core.MathFontWeight
import org.tiqian.math.core.MathHostTextCapabilityIssue
import org.tiqian.math.core.MathHostTextCapabilityIssueCode
import org.tiqian.math.core.MathHostTextFaceDecision
import org.tiqian.math.core.MathRect
import org.tiqian.math.core.SourceRange
import org.tiqian.math.font.android.AndroidMathFontFace
import org.tiqian.math.font.android.AndroidReplayCatalog
import org.tiqian.math.font.android.AndroidReplayFace
import org.tiqian.math.layout.MathGlyphBoundsSource
import org.tiqian.math.layout.MathTextRunProvider
import org.tiqian.math.layout.MathTextRunProviderResult
import org.tiqian.math.layout.MathTextRunRequest
import org.tiqian.math.layout.MeasuredMathGlyph
import org.tiqian.math.layout.MeasuredMathRun
import org.tiqian.math.layout.restrictedStandaloneTextCapabilityIssue
import org.tiqian.shaping.ShapingInput
import org.tiqian.shaping.nativefont.AndroidNativeGlyphReplay
import org.tiqian.shaping.nativefont.AndroidNativeTextShaper
import org.tiqian.shaping.nativefont.TiqianAndroidFontBackend
import kotlin.math.max

@Composable
internal actual fun rememberMarkdownMathTextRunProvider(
    hostTextStyle: TextStyle,
): MathTextRunProvider {
    val context = LocalContext.current.applicationContext
    return remember(context, hostTextStyle.fontFamily) {
        AndroidMarkdownMathTextRunProvider(
            shaper = AndroidNativeTextShaper(context),
            preferredFamilies = (hostTextStyle.fontFamily as? GenericFontFamily)
                ?.let { listOf(it.name) }
                .orEmpty(),
        )
    }
}

internal class AndroidMarkdownMathTextRunProvider(
    private val shaper: AndroidNativeTextShaper,
    private val preferredFamilies: List<String>,
) : MathTextRunProvider, AndroidReplayCatalog {
    private val replayFaces = LinkedHashMap<MathFaceId, AndroidReplayFace>()

    override fun shapeTextAtom(request: MathTextRunRequest): MathTextRunProviderResult {
        restrictedStandaloneTextCapabilityIssue(request)?.let {
            return MathTextRunProviderResult.CapabilityIssue(it)
        }
        val locale = request.locale ?: MarkdownMathTextLocale
        val glyphs = mutableListOf<MeasuredMathGlyph>()
        var penX = 0f
        var ascent = 0f
        var descent = 0f
        var missingGlyph = false

        for (segment in markdownMathHostTextSegments(request.text, locale)) {
            val segmentText = request.text.substring(segment.range.start, segment.range.end)
            val decision = FontDecision(
                range = segment.range,
                candidate = FontCandidate(
                    key = "tiqian-host-${segment.role.name}",
                    family = preferredFamilies.firstOrNull() ?: "system",
                    role = segment.role,
                ),
                role = segment.role,
                reason = "MarkdownMathHostText:${segment.role.name}",
            )
            val shaped = shaper.shape(
                ShapingInput(
                    text = request.text,
                    range = segment.range,
                    style = TiqianTextStyle(
                        fontFamilies = preferredFamilies,
                        fontSize = request.fontSizePx,
                        locale = locale,
                        fontWeight = request.requestedWeight.cssWeight,
                        italic = false,
                    ),
                    fontDecision = decision,
                    displayText = segmentText,
                ),
            )
            if (shaped.glyphRuns.any { run -> run.glyphs.any { it.renderFontKey == null } }) {
                return capabilityIssue(
                    request,
                    segment.range,
                    MathHostTextCapabilityIssueCode.PlatformMultiFaceStringDraw,
                    "Tiqian selected a platform multi-face string run that math-compose cannot replay glyph by glyph",
                )
            }
            shaped.decisions.forEach { missingGlyph = missingGlyph || it.missingGlyphs > 0 }
            shaped.glyphRuns.forEach { run ->
                run.glyphs.forEach { glyph ->
                    val renderFontKey = checkNotNull(glyph.renderFontKey)
                    val descriptor = TiqianAndroidFontBackend.replayFaceDescriptor(renderFontKey)
                        ?: return capabilityIssue(
                            request,
                            segment.range,
                            MathHostTextCapabilityIssueCode.NonReplayableHostTextRun,
                            "Tiqian did not retain replay evidence for host face $renderFontKey",
                        )
                    val faceId = MathFaceId("tiqian-host:$renderFontKey")
                    val resolvedWeight = MathFontWeight.nearest(descriptor.weight)
                    replayFaces.getOrPut(faceId) {
                        TiqianAndroidMathReplayFace(faceId, renderFontKey, resolvedWeight)
                    }
                    val bounds = glyph.bounds.toMathRect()
                    val cluster = glyph.clusterRange.takeIf { it.start >= segment.range.start && it.end <= segment.range.end }
                        ?: segment.range
                    val baselineOffset = glyph.y
                    glyphs += MeasuredMathGlyph(
                        glyphId = glyph.id.toUShort(),
                        x = penX + glyph.x,
                        advance = glyph.advance,
                        inkBounds = bounds,
                        textCluster = cluster.start,
                        baselineOffsetPx = baselineOffset,
                        faceId = faceId,
                        fontClass = null,
                        requestedWeight = request.requestedWeight,
                        resolvedWeight = resolvedWeight,
                        fallbackReason = null,
                        hostTextDecision = MathHostTextFaceDecision(
                            sourceRange = cluster.toSourceRange(request.sourceRange.start),
                            clusterRangeUtf16 = cluster.toMathSourceRange(),
                            hostRole = segment.role.name,
                            faceId = faceId,
                            fontKey = renderFontKey,
                            requestedWeight = request.requestedWeight,
                            resolvedWeight = resolvedWeight,
                            selectionReason = "TiqianAndroidFontBackend:${descriptor.sourceLabel}",
                            substitutionReason = if (descriptor.weight == request.requestedWeight.cssWeight) null
                                else "HostResolvedWeight${descriptor.weight}",
                        ),
                    )
                    ascent = max(ascent, -(bounds.top + baselineOffset))
                    descent = max(descent, bounds.bottom + baselineOffset)
                }
                penX += run.advance
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

    override fun replayFace(faceId: MathFaceId): AndroidReplayFace? = replayFaces[faceId]

    override fun constructionFace(faceId: MathFaceId): AndroidMathFontFace? = null

    private fun capabilityIssue(
        request: MathTextRunRequest,
        range: TextRange,
        code: MathHostTextCapabilityIssueCode,
        message: String,
    ) = MathTextRunProviderResult.CapabilityIssue(
        MathHostTextCapabilityIssue(
            code = code,
            message = message,
            sourceRange = range.toSourceRange(request.sourceRange.start),
        ),
    )
}

private class TiqianAndroidMathReplayFace(
    override val faceId: MathFaceId,
    private val renderFontKey: String,
    override val resolvedWeight: MathFontWeight,
) : AndroidReplayFace {
    override fun glyphPath(glyphId: UShort, fontSizePx: Float): Path? =
        AndroidNativeGlyphReplay.glyphPath(
            glyphs = listOf(
                Glyph(
                    id = glyphId.toUInt(),
                    clusterRange = TextRange(0, 1),
                    advance = 0f,
                    renderFontKey = renderFontKey,
                ),
            ),
            originX = 0f,
            originY = 0f,
            fontSize = fontSizePx,
        )
}

private fun Rect?.toMathRect(): MathRect = this?.let {
    MathRect(it.left, it.top, it.right, it.bottom)
} ?: MathRect(0f, 0f, 0f, 0f)

private fun TextRange.toMathSourceRange(): SourceRange = SourceRange(start, end)

private fun TextRange.toSourceRange(atomSourceStart: Int): SourceRange =
    SourceRange(atomSourceStart + start, atomSourceStart + end)
