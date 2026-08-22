package org.tiqian.markdown.compose

import org.tiqian.math.layout.MathAuthorColorRole
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownMathAuthorColorsTest {
    private val adapter = markdownDefaultMathAuthorColorAdapter()

    private fun channelToLinear(byte: Int): Double {
        val c = byte / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /** WCAG relative luminance, computed independently of the adapter's internals. */
    private fun luminance(argb: Int): Double {
        val r = channelToLinear(argb shr 16 and 0xff)
        val g = channelToLinear(argb shr 8 and 0xff)
        val b = channelToLinear(argb and 0xff)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun alpha(argb: Int): Int = argb ushr 24 and 0xff

    @Test
    fun whiteBackdropReturnsAuthorColorVerbatim() {
        val white = 0xFFFFFFFF.toInt()
        for (author in listOf(0xFF00008B.toInt(), 0xFFCC0000.toInt(), 0xFF228B22.toInt(), 0xFF000000.toInt())) {
            for (role in MathAuthorColorRole.entries) {
                assertEquals(
                    author,
                    adapter.adapt(author, role, white),
                    "author color must pass through unchanged on a white backdrop",
                )
            }
        }
    }

    @Test
    fun darkForegroundBecomesLighterHuePreservedAndContrastMet() {
        val backdrop = 0xFF121212.toInt()
        val author = 0xFF00008B.toInt() // pure dark blue, chosen for a white page
        val adapted = adapter.adapt(author, MathAuthorColorRole.Foreground, backdrop)

        assertTrue(
            luminance(adapted) > luminance(author),
            "on a dark theme a dark author color must gain luminance",
        )
        assertTrue(
            oklchHueDeltaDegrees(author, adapted) <= 2.0,
            "the lightness flip must preserve hue (within ~2°), got ${oklchHueDeltaDegrees(author, adapted)}",
        )
        assertTrue(
            contrast(adapted, backdrop) >= 4.4,
            "foreground must clear the WCAG 4.5 floor, got ${contrast(adapted, backdrop)}",
        )
    }

    @Test
    fun authorBackgroundFillDarkensOnDarkTheme() {
        val backdrop = 0xFF121212.toInt()
        val author = 0xFFFFFF99.toInt() // light yellow highlight fill
        val adapted = adapter.adapt(author, MathAuthorColorRole.AuthorBackground, backdrop)

        assertTrue(
            luminance(adapted) < luminance(author),
            "a light author fill must lose luminance against a dark backdrop",
        )
        assertTrue(
            oklchHueDeltaDegrees(author, adapted) <= 8.0,
            "the fill must keep its hue (within ~8°), got ${oklchHueDeltaDegrees(author, adapted)}",
        )
    }

    @Test
    fun alphaBytePreservedThroughAdaptation() {
        val backdrop = 0xFF121212.toInt()
        val author = 0x8000008B.toInt() // half-opaque dark blue
        val adapted = adapter.adapt(author, MathAuthorColorRole.Foreground, backdrop)
        assertEquals(0x80, alpha(adapted), "author alpha byte must survive verbatim")
    }

    @Test
    fun adaptationIsDeterministic() {
        val backdrop = 0xFF121212.toInt()
        val author = 0xFF00008B.toInt()
        val first = adapter.adapt(author, MathAuthorColorRole.Foreground, backdrop)
        val second = adapter.adapt(author, MathAuthorColorRole.Foreground, backdrop)
        assertEquals(first, second, "a pure adapter must return identical ints for identical inputs")
    }

    @Test
    fun defaultAdapterIsAStableSingleton() {
        assertEquals(
            markdownDefaultMathAuthorColorAdapter(),
            markdownDefaultMathAuthorColorAdapter(),
            "composition keying on MarkdownMathStyle needs a stable adapter identity",
        )
    }

    @Test
    fun inheritedContentOnFillIsVerbatimWhenContrastHolds() {
        // Theme white on a mid-dark fill: already readable, must stay byte-identical (no flip).
        val adapter = markdownDefaultMathAuthorColorAdapter()
        val white = 0xFFFFFFFF.toInt()
        val darkFill = 0xFF5C5C00.toInt()
        assertEquals(
            white,
            adapter.adapt(white, MathAuthorColorRole.InheritedOnAuthorBackground, darkFill),
        )
    }

    @Test
    fun onFillContentEnforcesFullFloorEvenOnALightFill() {
        // A dark \bbox fill on a dark theme flips light; content over it must still clear the full
        // role floor. The fill's lightness must not dilute the target the way a near-white page does,
        // or near-white content would be left barely legible on the light fill.
        val lightFill = 0xFFCCCCAA.toInt()
        val content = 0xFFEEEEEE.toInt() // near-white content: ~1.5:1 raw contrast on the light fill
        for (role in listOf(
            MathAuthorColorRole.ForegroundOnAuthorBackground,
            MathAuthorColorRole.InheritedOnAuthorBackground,
        )) {
            val adapted = adapter.adapt(content, role, lightFill)
            assertTrue(
                contrast(adapted, lightFill) >= 4.4,
                "$role on a light author fill must reach the 4.5 floor, got ${contrast(adapted, lightFill)}",
            )
        }
    }

    @Test
    fun inheritedContentOnFillOnlyMovesWhenContrastFails() {
        // Theme dark-gray on a similar dark fill: keeps hue (gray), gains luminance contrast.
        val adapter = markdownDefaultMathAuthorColorAdapter()
        val gray = 0xFF303030.toInt()
        val darkFill = 0xFF404040.toInt()
        val adapted = adapter.adapt(gray, MathAuthorColorRole.InheritedOnAuthorBackground, darkFill)
        assertTrue(adapted != gray, "insufficient contrast must adjust")
        assertTrue(contrast(adapted, darkFill) > contrast(gray, darkFill))
    }

    @Test
    fun harmonizeRotatesAuthorHueTowardTargetCappedAt15Degrees() {
        val target = 0xFF3050FF.toInt() // a blue "theme primary"
        val author = 0xFFCC0000.toInt() // red, far from blue in hue
        val harmonized = harmonizeHueTowards(author, target)
        val before = oklchHueDeltaDegrees(author, target)
        val after = oklchHueDeltaDegrees(harmonized, target)
        assertTrue(after < before, "hue must move toward the target ($before -> $after)")
        assertTrue(before - after <= 15.0 + 1e-6, "rotation is capped at 15°, moved ${before - after}")
    }

    @Test
    fun harmonizeLeavesAchromaticColorsAlone() {
        val target = 0xFF3050FF.toInt()
        val gray = 0xFF808080.toInt()
        assertEquals(gray, harmonizeHueTowards(gray, target), "a gray's noise hue must not be rotated")
    }

    @Test
    fun m3AdapterHarmonizesAuthorColorButNotInheritedContent() {
        val primary = 0xFF3050FF.toInt()
        val adapter = markdownMathAuthorColorAdapter(harmonizeTowardArgb = primary)
        val white = 0xFFFFFFFF.toInt()
        // On a white backdrop the flip/floor are identity, so any change is the harmonize alone.
        val author = 0xFFCC0000.toInt()
        assertTrue(
            adapter.adapt(author, MathAuthorColorRole.Foreground, white) != author,
            "author color must be harmonized toward the primary",
        )
        assertEquals(
            white,
            adapter.adapt(white, MathAuthorColorRole.InheritedOnAuthorBackground, white),
            "theme-inherited content is never harmonized",
        )
    }

    @Test
    fun m3AdapterIsValueEqualOnItsTarget() {
        assertEquals(
            markdownMathAuthorColorAdapter(0xFF3050FF.toInt()),
            markdownMathAuthorColorAdapter(0xFF3050FF.toInt()),
            "equal targets must produce equal adapters so MarkdownMathStyle keys stay stable",
        )
    }

}
