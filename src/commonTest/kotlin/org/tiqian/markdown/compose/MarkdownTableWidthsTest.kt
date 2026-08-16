package org.tiqian.markdown.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownTableWidthsTest {
    @Test
    fun waterlineCompressesOnlyColumnsAboveTheLevel() {
        val result = resolveMarkdownTableWidths(
            preferredWidths = listOf(160f, 100f, 80f),
            minimumWidths = listOf(40f, 40f, 40f),
            availableWidth = 240f,
        )

        assertWidthsClose(listOf(80f, 80f, 80f), result.columnWidths)
        assertWidthsClose(listOf(240f), listOf(result.tableWidth))
    }

    @Test
    fun tableOverflowsOnlyWhenColumnMinimumsCannotFit() {
        val result = resolveMarkdownTableWidths(
            preferredWidths = listOf(160f, 100f, 80f),
            minimumWidths = listOf(40f, 40f, 40f),
            availableWidth = 100f,
        )

        assertEquals(120f, result.tableWidth)
        assertEquals(listOf(40f, 40f, 40f), result.columnWidths)
    }

    @Test
    fun compactTableFillsTheTargetProportionally() {
        val result = resolveMarkdownTableWidths(
            preferredWidths = listOf(100f, 80f),
            minimumWidths = listOf(40f, 40f),
            availableWidth = 240f,
        )

        // `TableFluidFill`: proportions are preserved (5:4), not equalized.
        assertWidthsClose(listOf(133.33333f, 106.66667f), result.columnWidths)
        assertWidthsClose(listOf(240f), listOf(result.tableWidth))
    }

    @Test
    fun tableWiderThanFillTargetKeepsItsNaturalWidth() {
        val result = resolveMarkdownTableWidths(
            preferredWidths = listOf(100f, 80f),
            minimumWidths = listOf(40f, 40f),
            availableWidth = 240f,
            fillTargetWidth = 150f,
        )

        assertEquals(listOf(100f, 80f), result.columnWidths)
        assertEquals(180f, result.tableWidth)
    }

    @Test
    fun waterlineRespectsPerColumnMinimums() {
        val result = resolveMarkdownTableWidths(
            preferredWidths = listOf(140f, 80f),
            minimumWidths = listOf(90f, 40f),
            availableWidth = 160f,
        )

        // The widest column bears compression first but stops at its own minimum.
        assertWidthsClose(listOf(90f, 70f), result.columnWidths)
        assertWidthsClose(listOf(160f), listOf(result.tableWidth))
    }

    @Test
    fun quantizedColumnsKeepContentOnTheEmGridWhileFilling() {
        val emPx = 16f
        val padPx = 8f
        val result = resolveMarkdownTableWidths(
            preferredWidths = listOf(103f, 63f),
            minimumWidths = listOf(24f, 24f),
            availableWidth = 400f,
            emPx = emPx,
            fillTargetWidth = 200f,
            horizontalPaddingPx = padPx,
        )

        assertEquals(listOf(120f, 72f), result.columnWidths)
        assertEquals(192f, result.tableWidth)
        result.columnWidths.forEach { width ->
            val contentEms = (width - padPx) / emPx
            assertTrue(
                kotlin.math.abs(contentEms - kotlin.math.round(contentEms)) < 0.001f,
                "Content width ${width - padPx} is not an em multiple",
            )
        }
    }

    private fun assertWidthsClose(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedWidth, actualWidth) ->
            assertTrue(
                kotlin.math.abs(expectedWidth - actualWidth) < 0.01f,
                "Expected $expectedWidth but was $actualWidth",
            )
        }
    }
}
