package org.tiqian.markdown

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class MaterialMarkdownStyleTest {
    @Test
    fun platformMappingUsesSemanticColoursWithoutReplacingEditorialGeometry() {
        val colors = lightColorScheme(
            primary = Color(0xFF123456),
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
        assertEquals(colors.surfaceContainerHigh, mapped.inlineCode.background)
        assertEquals(colors.tertiaryContainer, mapped.highlight.background)
        assertEquals(colors.outlineVariant, mapped.quoteBarColor)
        assertEquals(colors.onSurfaceVariant, mapped.quoteText.color)
        assertEquals(colors.surfaceContainer, mapped.codeBackground)
        assertEquals(colors.surfaceContainerLow, mapped.tableHeaderBackground)
    }
}
