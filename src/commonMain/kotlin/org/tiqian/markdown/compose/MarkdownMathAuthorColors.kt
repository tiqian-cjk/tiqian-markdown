package org.tiqian.markdown.compose

import org.tiqian.math.layout.MathAuthorColorAdapter
import org.tiqian.math.layout.MathAuthorColorRole
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Markdown default adapter for author-declared TeX colors (`\color`, `\bbox` fills, borders,
 * `\cancel`). Author colors are assumed chosen for a white page; this maps them into the host
 * theme through two named, deterministic policies while preserving the author's hue and alpha.
 *
 * Policy 1 — **AuthorColorLightnessFlipPreservesHue**: the author color is converted to OKLCH
 * (Björn Ottosson's OKLab, implemented locally). With `B` = the OKLab lightness of the backdrop
 * and `t = (1 - B)` clamped to `[0, 1]`, the new lightness is `lerp(L, 1 - L, t)`; hue and chroma
 * are left untouched. A white backdrop gives `t ≈ 0` (identity); a black backdrop gives a full
 * lightness flip and never a hue inversion.
 *
 * Policy 2 — **ThemeDistanceScaledContrastFloor**: after the flip, a minimum WCAG relative-luminance
 * contrast ratio against the backdrop is enforced. `targetByRole` is `4.5` for foreground-like
 * content (Foreground, ForegroundOnAuthorBackground, InheritedOnAuthorBackground, Cancellation) and
 * `1.5` for borders; author background fills carry no floor of their own (the on-background pass
 * guarantees their content's contrast). Against the **page** backdrop the target scales with theme
 * distance, `target = lerp(1.0, targetByRole, t)`, so a near-white theme — the author's intended
 * medium — forces nothing. But content and borders sitting on an **author fill** use the full role
 * target unscaled: the fill's lightness is not the theme's, so a dark fill flipped light on a dark
 * theme must not host its content at a near-1.0 floor. When the color is short of target, its OKLab
 * lightness is moved away from the backdrop — toward whichever endpoint has more contrast headroom —
 * by binary search until the target is met or lightness is clamped.
 *
 * Any lightness change that pushes the OKLCH color outside sRGB is gamut-mapped by binary-searching
 * chroma down (~12 iterations) until every channel lands in `[0, 1]`. The author alpha byte is
 * preserved verbatim, and the whole transform is a pure function of its inputs.
 */
fun markdownDefaultMathAuthorColorAdapter(): MathAuthorColorAdapter = MarkdownDefaultMathAuthorColorAdapter

/** Stable singleton so [MarkdownMathStyle] equality (hence composition keys) stays byte-stable. */
private val MarkdownDefaultMathAuthorColorAdapter = MathAuthorColorAdapter { authorArgb, role, backdropArgb ->
    adaptAuthorColor(authorArgb, role, backdropArgb)
}

/** Below this theme distance a light theme is treated as white: no flip and no forced contrast. */
private const val IdentityThemeDistance = 0.02

private const val GamutEpsilon = 1e-4
private const val ContrastSearchIterations = 24
private const val ChromaSearchIterations = 12

private fun adaptAuthorColor(authorArgb: Int, role: MathAuthorColorRole, backdropArgb: Int): Int {
    val alpha = authorArgb ushr 24 and 0xff

    val backdropLightness = argbToOklch(backdropArgb).lightness.coerceIn(0.0, 1.0)
    val themeDistance = (1.0 - backdropLightness).coerceIn(0.0, 1.0)
    val backdropLuminance = wcagLuminance(backdropArgb)

    val targetByRole = when (role) {
        // The fill's own content contrast is guaranteed by the on-background passes.
        MathAuthorColorRole.AuthorBackground -> null
        MathAuthorColorRole.Border -> 1.5
        MathAuthorColorRole.Foreground,
        MathAuthorColorRole.ForegroundOnAuthorBackground,
        MathAuthorColorRole.InheritedOnAuthorBackground,
        MathAuthorColorRole.Cancellation,
        -> 4.5
    }
    // Theme-distance scaling of the floor is only sound against the page: a near-white theme is the
    // author's medium, so their colors need no forced contrast. When the backdrop is a local author
    // fill, the fill's lightness is not the theme's, so these roles clear the full role target — else
    // a dark fill flipped light on a dark theme would host its content at a near-1.0 floor.
    val backdropIsAuthorFill = when (role) {
        MathAuthorColorRole.ForegroundOnAuthorBackground,
        MathAuthorColorRole.InheritedOnAuthorBackground,
        MathAuthorColorRole.Border,
        -> true
        else -> false
    }
    val contrastTarget = targetByRole?.let {
        if (backdropIsAuthorFill) it else 1.0 * (1.0 - themeDistance) + it * themeDistance
    }

    // Theme-inherited content is already theme-appropriate: never flip it.
    val flips = role != MathAuthorColorRole.InheritedOnAuthorBackground

    // A near-white backdrop is the author's intended medium — page or white fill alike — so the
    // color passes through untouched for every role, including ones held to a full on-fill floor.
    if (themeDistance < IdentityThemeDistance) return authorArgb

    // Theme-inherited content stays byte-identical while it still clears the floor against the
    // (non-near-white) fill it sits on.
    if (!flips) {
        val alreadyMet = contrastTarget == null ||
            wcagContrast(wcagLuminance(authorArgb), backdropLuminance) >= contrastTarget
        if (alreadyMet) return authorArgb
    }

    val author = argbToOklch(authorArgb)
    // Policy 1: AuthorColorLightnessFlipPreservesHue. Inherited content skips the flip.
    var lightness = if (flips) {
        author.lightness * (1.0 - themeDistance) + (1.0 - author.lightness) * themeDistance
    } else {
        author.lightness
    }

    // Policy 2: ThemeDistanceScaledContrastFloor.
    if (contrastTarget != null) {
        val current = wcagContrast(luminanceOf(lightness, author.chroma, author.hue), backdropLuminance)
        if (current < contrastTarget) {
            val contrastUp = wcagContrast(luminanceOf(1.0, author.chroma, author.hue), backdropLuminance)
            val contrastDown = wcagContrast(luminanceOf(0.0, author.chroma, author.hue), backdropLuminance)
            val endpoint = if (contrastUp >= contrastDown) 1.0 else 0.0
            if (maxOf(contrastUp, contrastDown) < contrastTarget) {
                // Unreachable target: clamp to the maximally contrasting endpoint.
                lightness = endpoint
            } else {
                var below = lightness
                var meeting = endpoint
                repeat(ContrastSearchIterations) {
                    val mid = (below + meeting) / 2.0
                    if (wcagContrast(luminanceOf(mid, author.chroma, author.hue), backdropLuminance) >= contrastTarget) {
                        meeting = mid
                    } else {
                        below = mid
                    }
                }
                lightness = meeting
            }
        }
    }

    return oklchToArgb(lightness, author.chroma, author.hue, alpha)
}

// region OKLab / OKLCH color model (Björn Ottosson)

private class Oklch(val lightness: Double, val chroma: Double, val hue: Double)

private fun channelToLinear(c: Double): Double =
    if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

private fun linearToChannel(c: Double): Double =
    if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055

private fun cubeRoot(x: Double): Double = if (x < 0.0) -((-x).pow(1.0 / 3.0)) else x.pow(1.0 / 3.0)

private fun argbToOklch(argb: Int): Oklch {
    val r = channelToLinear((argb shr 16 and 0xff) / 255.0)
    val g = channelToLinear((argb shr 8 and 0xff) / 255.0)
    val b = channelToLinear((argb and 0xff) / 255.0)

    val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    val l_ = cubeRoot(l)
    val m_ = cubeRoot(m)
    val s_ = cubeRoot(s)

    val lightness = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
    val a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
    val bb = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
    return Oklch(lightness, sqrt(a * a + bb * bb), atan2(bb, a))
}

/** OKLCH → linear sRGB; channels may fall outside `[0, 1]` when the color is out of gamut. */
private fun oklchToLinearRgb(lightness: Double, chroma: Double, hue: Double): DoubleArray {
    val a = chroma * cos(hue)
    val bb = chroma * sin(hue)
    val l_ = lightness + 0.3963377774 * a + 0.2158037573 * bb
    val m_ = lightness - 0.1055613458 * a - 0.0638541728 * bb
    val s_ = lightness - 0.0894841775 * a - 1.2914855480 * bb
    val l = l_ * l_ * l_
    val m = m_ * m_ * m_
    val s = s_ * s_ * s_
    return doubleArrayOf(
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    )
}

private fun inGamut(rgb: DoubleArray): Boolean =
    rgb.all { it >= -GamutEpsilon && it <= 1.0 + GamutEpsilon }

/** Largest chroma ≤ [chroma] that keeps [lightness]/[hue] inside sRGB, via binary search. */
private fun gamutMappedChroma(lightness: Double, chroma: Double, hue: Double): Double {
    if (inGamut(oklchToLinearRgb(lightness, chroma, hue))) return chroma
    var inside = 0.0
    var outside = chroma
    repeat(ChromaSearchIterations) {
        val mid = (inside + outside) / 2.0
        if (inGamut(oklchToLinearRgb(lightness, mid, hue))) inside = mid else outside = mid
    }
    return inside
}

/** Gamut-mapped, `[0, 1]`-clamped linear sRGB for a given OKLCH color. */
private fun renderLinearRgb(lightness: Double, chroma: Double, hue: Double): DoubleArray {
    val rgb = oklchToLinearRgb(lightness, gamutMappedChroma(lightness, chroma, hue), hue)
    return DoubleArray(3) { rgb[it].coerceIn(0.0, 1.0) }
}

private fun luminanceOf(lightness: Double, chroma: Double, hue: Double): Double {
    val rgb = renderLinearRgb(lightness, chroma, hue)
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]
}

private fun oklchToArgb(lightness: Double, chroma: Double, hue: Double, alpha: Int): Int {
    val rgb = renderLinearRgb(lightness, chroma, hue)
    val r = (linearToChannel(rgb[0]) * 255.0).roundToInt().coerceIn(0, 255)
    val g = (linearToChannel(rgb[1]) * 255.0).roundToInt().coerceIn(0, 255)
    val b = (linearToChannel(rgb[2]) * 255.0).roundToInt().coerceIn(0, 255)
    return (alpha shl 24) or (r shl 16) or (g shl 8) or b
}

// endregion

// region WCAG relative luminance / contrast

private fun wcagLuminance(argb: Int): Double {
    val r = channelToLinear((argb shr 16 and 0xff) / 255.0)
    val g = channelToLinear((argb shr 8 and 0xff) / 255.0)
    val b = channelToLinear((argb and 0xff) / 255.0)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

private fun wcagContrast(a: Double, b: Double): Double {
    val lighter = maxOf(a, b)
    val darker = minOf(a, b)
    return (lighter + 0.05) / (darker + 0.05)
}

// endregion

/** Retained for potential diagnostics; hue distance in degrees between two OKLCH hues. */
internal fun oklchHueDegrees(argb: Int): Double = argbToOklch(argb).hue * 180.0 / kotlin.math.PI

internal fun oklchHueDeltaDegrees(argbA: Int, argbB: Int): Double {
    val delta = abs(oklchHueDegrees(argbA) - oklchHueDegrees(argbB)) % 360.0
    return if (delta > 180.0) 360.0 - delta else delta
}
