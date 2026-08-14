package org.tiqian.markdown.compose

import android.graphics.Paint
import android.graphics.Path
import android.text.TextPaint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.GenericFontFamily
import org.tiqian.core.Rect
import org.tiqian.core.TextRange
import org.tiqian.core.TextStyle as TiqianTextStyle
import org.tiqian.font.FontCandidate
import org.tiqian.font.FontDecision
import org.tiqian.font.FontRole
import org.tiqian.math.core.*
import org.tiqian.math.font.android.AndroidMathFontFace
import org.tiqian.math.font.android.AndroidReplayCatalog
import org.tiqian.math.font.android.AndroidReplayFace
import org.tiqian.math.layout.*
import org.tiqian.shaping.ShapingInput
import org.tiqian.shaping.TextShaper
import org.tiqian.shaping.android.createAndroidTextShaper
import org.tiqian.shaping.android.AndroidTypefaceResolver
import org.tiqian.shaping.android.SystemAndroidTypefaceResolver
import org.tiqian.shaping.android.requiresHanShapingContext
import java.util.Locale
import kotlin.math.max

@Composable
internal actual fun rememberMarkdownMathTextRunProvider(hostTextStyle: TextStyle): MathTextRunProvider {
    val context = LocalContext.current.applicationContext
    return remember(context, hostTextStyle.fontFamily) {
        val resolver = SystemAndroidTypefaceResolver()
        AndroidMarkdownMathTextRunProvider(
            shaper = createAndroidTextShaper(resolver),
            typefaces = resolver,
            preferredFamilies = (hostTextStyle.fontFamily as? GenericFontFamily)
                ?.let { listOf(it.name) }
                .orEmpty(),
        )
    }
}

internal class AndroidMarkdownMathTextRunProvider(
    private val shaper: TextShaper,
    private val typefaces: AndroidTypefaceResolver = SystemAndroidTypefaceResolver(),
    private val preferredFamilies: List<String>,
) : MathTextRunProvider, AndroidReplayCatalog {
    private val replayFaces = LinkedHashMap<ReplayFaceKey, PlatformTextReplayFace>()

    @Synchronized
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
            val style = TiqianTextStyle(
                fontFamilies = preferredFamilies,
                fontSize = request.fontSizePx,
                locale = locale,
                fontWeight = request.requestedWeight.cssWeight,
                italic = false,
            )
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
                    style = style,
                    fontDecision = decision,
                    displayText = segmentText,
                ),
            )
            shaped.decisions.forEach { missingGlyph = missingGlyph || it.missingGlyphs > 0 }
            val bounds = shaped.glyphRuns.flatMap { it.glyphs }.mapNotNull { glyph ->
                glyph.bounds?.let { bound -> MathRect(glyph.x + bound.left, glyph.y + bound.top, glyph.x + bound.right, glyph.y + bound.bottom) }
            }.union()
            val advance = shaped.glyphRuns.sumOf { it.advance.toDouble() }.toFloat()
            val key = ReplayFaceKey(segment.role, style.fontFamilies, style.fontWeight, locale)
            val replay = replayFaces.getOrPut(key) {
                PlatformTextReplayFace(
                    faceId = MathFaceId("tiqian-host:${key.stableId()}"),
                    key = key,
                    typefaces = typefaces,
                    resolvedWeight = MathFontWeight.nearest(style.fontWeight),
                )
            }
            val glyphId = replay.register(segmentText)
            glyphs += MeasuredMathGlyph(
                glyphId = glyphId,
                x = penX,
                advance = advance,
                inkBounds = bounds,
                textCluster = segment.range.start,
                faceId = replay.faceId,
                fontClass = null,
                requestedWeight = request.requestedWeight,
                resolvedWeight = replay.resolvedWeight,
                fallbackReason = null,
                hostTextDecision = MathHostTextFaceDecision(
                    sourceRange = segment.range.toSourceRange(request.sourceRange.start),
                    clusterRangeUtf16 = segment.range.toMathSourceRange(),
                    hostRole = segment.role.name,
                    faceId = replay.faceId,
                    fontKey = replay.faceId.value,
                    requestedWeight = request.requestedWeight,
                    resolvedWeight = replay.resolvedWeight,
                    selectionReason = "TiqianAndroidPlatformText:${segment.role.name}",
                    substitutionReason = if (replay.resolvedWeight == request.requestedWeight) null
                    else "HostResolvedWeight${replay.resolvedWeight.cssWeight}",
                ),
            )
            ascent = max(ascent, -bounds.top)
            descent = max(descent, bounds.bottom)
            penX += advance
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

    @Synchronized
    override fun replayFace(faceId: MathFaceId): AndroidReplayFace? =
        replayFaces.values.firstOrNull { it.faceId == faceId }

    override fun constructionFace(faceId: MathFaceId): AndroidMathFontFace? = null
}

private data class ReplayFaceKey(
    val role: FontRole,
    val families: List<String>,
    val weight: Int,
    val locale: String,
) {
    fun stableId(): String = "${role.name}:${families.joinToString("+")}:$weight:$locale"
}

private class PlatformTextReplayFace(
    override val faceId: MathFaceId,
    private val key: ReplayFaceKey,
    private val typefaces: AndroidTypefaceResolver,
    override val resolvedWeight: MathFontWeight,
) : AndroidReplayFace {
    private val textByGlyph = LinkedHashMap<UShort, String>()
    private val glyphByText = LinkedHashMap<String, UShort>()
    private var nextGlyphId = 1

    @Synchronized
    fun register(text: String): UShort = glyphByText.getOrPut(text) {
        check(nextGlyphId <= 0xFFFF) { "Android host-text replay registry is full" }
        nextGlyphId++.toUShort().also { textByGlyph[it] = text }
    }

    @Synchronized
    override fun glyphPath(glyphId: UShort, fontSizePx: Float): Path? {
        val text = textByGlyph[glyphId] ?: return null
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSizePx
            textLocale = Locale.forLanguageTag(key.locale)
            typeface = typefaces.resolve(key.role, key.families, key.weight, italic = false)
            isSubpixelText = true
        }
        val path = Path()
        if (requiresHanShapingContext(text, key.role)) {
            val buffer = "中${text}中"
            val start = paint.getRunAdvance(buffer, 0, buffer.length, 0, buffer.length, false, 1)
            val end = paint.getRunAdvance(buffer, 0, buffer.length, 0, buffer.length, false, 1 + text.length)
            paint.getTextPath(buffer, 0, buffer.length, 0f, 0f, path)
            val clip = Path().apply { addRect(start, -fontSizePx * 2f, end, fontSizePx, Path.Direction.CW) }
            path.op(clip, Path.Op.INTERSECT)
            path.offset(-start, 0f)
        } else {
            paint.getTextPath(text, 0, text.length, 0f, 0f, path)
        }
        return path
    }
}

private fun List<MathRect>.union(): MathRect {
    if (isEmpty()) return MathRect(0f, 0f, 0f, 0f)
    return MathRect(minOf { it.left }, minOf { it.top }, maxOf { it.right }, maxOf { it.bottom })
}

private fun TextRange.toMathSourceRange(): SourceRange = SourceRange(start, end)

private fun TextRange.toSourceRange(atomSourceStart: Int): SourceRange =
    SourceRange(atomSourceStart + start, atomSourceStart + end)
