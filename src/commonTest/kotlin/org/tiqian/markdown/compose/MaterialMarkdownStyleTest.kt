package org.tiqian.markdown.compose

import org.tiqian.markdown.*

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class MaterialMarkdownStyleTest {
    @Test
    fun platformMappingUsesSemanticColoursWithoutReplacingEditorialGeometry() {
        val colors = lightColorScheme(
            primary = Color(0xFF123456),
            secondary = Color(0xFF234567),
            tertiary = Color(0xFF345678),
            onSurface = Color(0xFF223344),
            onSurfaceVariant = Color(0xFF334455),
            tertiaryContainer = Color(0xFF445566),
            onTertiaryContainer = Color(0xFF556677),
            outline = Color(0xFF667788),
            outlineVariant = Color(0xFF778899),
            surfaceContainer = Color(0xFF8899AA),
            surfaceContainerLow = Color(0xFF99AABB),
            surfaceContainerHigh = Color(0xFFAABBCC),
        )
        val platformFamily = FontFamily.Serif
        val typography = Typography(
            bodyLarge = TextStyle(
                fontFamily = platformFamily,
                fontSize = 20.sp,
                lineHeight = 30.sp,
            ),
        )
        val mapped = MarkdownStyle().mapMaterial3(colors, typography)

        assertEquals(16.sp, mapped.body.fontSize)
        assertEquals(26.sp, mapped.body.lineHeight)
        assertEquals(platformFamily, mapped.body.fontFamily)
        assertEquals(colors.onSurface, mapped.body.color)
        assertEquals(colors.onSurfaceVariant, mapped.codeLanguage.color)
        assertEquals(colors.primary, mapped.link.color)
        assertEquals(colors.primary, mapped.footnoteReference.color)
        assertEquals(colors.outline, mapped.footnote.color)
        assertEquals(colors.outline, mapped.caption.color)
        assertEquals(colors.surfaceContainerHigh, mapped.inlineCode.background)
        assertEquals(colors.tertiaryContainer, mapped.highlight.background)
        assertEquals(colors.outlineVariant, mapped.quoteBarColor)
        assertEquals(colors.onSurfaceVariant, mapped.quoteText.color)
        assertEquals(colors.surfaceContainer, mapped.codeBackground)
        assertEquals(colors.outline, mapped.codeLineNumberColor)
        assertEquals(colors.surfaceContainerLow, mapped.tableHeaderBackground)
        assertEquals(Dp.Hairline, mapped.imageOutlineWidth)
        assertEquals(colors.outline.copy(alpha = 0.15f), mapped.imageOutlineColor)
        // Author TeX colors adapt against the surface the body text sits on, so dark themes are legible.
        assertEquals(colors.surface, mapped.math.authorColorBackdrop)

        with(mapped.codeHighlight) {
            assertEquals(colors.outline, comment.color)
            assertEquals(colors.primary, keyword.color)
            assertEquals(FontWeight.Medium, keyword.fontWeight)
            assertEquals(colors.tertiary, string.color)
            assertEquals(colors.secondary, number.color)
            assertEquals(FontWeight.Medium, number.fontWeight)
            assertEquals(colors.tertiary, type.color)
            assertEquals(FontWeight.Medium, type.fontWeight)
            assertEquals(colors.secondary, function.color)
            assertEquals(colors.secondary, property.color)
            assertEquals(colors.tertiary, annotation.color)
            assertEquals(FontWeight.Medium, annotation.fontWeight)
            assertEquals(colors.onSurface, variable.color)
            assertEquals(colors.onSurfaceVariant, operator.color)
            assertEquals(colors.onSurfaceVariant, punctuation.color)
            assertEquals(colors.primary, tag.color)
            assertEquals(FontWeight.Medium, tag.fontWeight)
            assertEquals(colors.secondary, attribute.color)
            assertEquals(colors.secondary, constant.color)
            assertEquals(FontWeight.Medium, constant.fontWeight)
            assertEquals(colors.primary, escape.color)
            assertEquals(FontWeight.Medium, escape.fontWeight)
            assertEquals(colors.onSurface, markup.color)
        }
    }
}
