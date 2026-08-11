package org.tiqian.markdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import org.tiqian.core.TextRange
import org.tiqian.font.CjkFontRoleClassifier
import org.tiqian.font.FontRole
import org.tiqian.font.FontRoleContext
import org.tiqian.math.layout.MathTextRunProvider

/** The formula text commands inherit the same Simplified-Chinese host context as prose. */
internal const val MarkdownMathTextLocale = "zh-Hans"

@Composable
internal expect fun rememberMarkdownMathTextRunProvider(
    hostTextStyle: TextStyle,
): MathTextRunProvider

internal data class MarkdownMathHostTextSegment(
    val range: TextRange,
    val role: FontRole,
)

/**
 * Keeps Latin shaping runs intact for kerning and ligatures, while giving every non-Latin
 * grapheme an independent fallback decision. This is font/shaping segmentation only; TeX owns
 * script style, placement and formula breaking.
 */
internal fun markdownMathHostTextSegments(
    text: String,
    locale: String,
): List<MarkdownMathHostTextSegment> {
    if (text.isEmpty()) return emptyList()
    val classifier = CjkFontRoleClassifier()
    val context = FontRoleContext(locale = locale)
    val segments = mutableListOf<MarkdownMathHostTextSegment>()
    var index = 0
    while (index < text.length) {
        val start = index
        val codePoint = text.codePointAtCompat(index)
        index += codePoint.charCount()
        val role = classifier.classify(text, TextRange(start, index), context)
        if (role == FontRole.LatinText) {
            while (index < text.length) {
                val next = text.codePointAtCompat(index)
                val end = index + next.charCount()
                val nextRole = classifier.classify(text, TextRange(index, end), context)
                if (nextRole != FontRole.LatinText && !next.isGraphemeExtender()) break
                index = end
            }
        } else {
            while (index < text.length) {
                val next = text.codePointAtCompat(index)
                if (!next.isGraphemeExtender()) break
                index += next.charCount()
            }
        }
        segments += MarkdownMathHostTextSegment(TextRange(start, index), role)
    }
    return segments
}

private fun String.codePointAtCompat(index: Int): Int {
    val high = this[index].code
    if (high !in 0xD800..0xDBFF || index + 1 >= length) return high
    val low = this[index + 1].code
    if (low !in 0xDC00..0xDFFF) return high
    return 0x10000 + ((high - 0xD800) shl 10) + (low - 0xDC00)
}

private fun Int.charCount(): Int = if (this > 0xFFFF) 2 else 1

private fun Int.isGraphemeExtender(): Boolean =
    this in 0xFE00..0xFE0F || this in 0xE0100..0xE01EF ||
        (this in 0..0xFFFF && toChar().category in CombiningMarkCategories)

private val CombiningMarkCategories = setOf(
    CharCategory.NON_SPACING_MARK,
    CharCategory.COMBINING_SPACING_MARK,
    CharCategory.ENCLOSING_MARK,
)
