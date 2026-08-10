package org.tiqian.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownTableWidthsTest {
    @Test
    fun narrowTableShrinksMeasuredWhitespaceBeforeOverflowing() {
        val result = resolveMarkdownTableWidths(
            preferredWidths = listOf(160f, 100f, 80f),
            minimumWidths = listOf(40f, 40f, 40f),
            availableWidth = 240f,
        )

        assertEquals(240f, result.tableWidth)
        assertWidthsClose(listOf(105.454544f, 72.72727f, 61.81818f), result.columnWidths)
    }

    @Test
    fun tableOverflowsOnlyWhenOneCellMinimumsCannotFit() {
        val result = resolveMarkdownTableWidths(
            preferredWidths = listOf(160f, 100f, 80f),
            minimumWidths = listOf(40f, 40f, 40f),
            availableWidth = 100f,
        )

        assertEquals(120f, result.tableWidth)
        assertEquals(listOf(40f, 40f, 40f), result.columnWidths)
    }

    @Test
    fun wideContainerEqualizesColumnsOnlyUpToTheWidestNaturalColumn() {
        val result = resolveMarkdownTableWidths(
            preferredWidths = listOf(100f, 80f),
            minimumWidths = listOf(40f, 40f),
            availableWidth = 240f,
        )

        assertEquals(200f, result.tableWidth)
        assertEquals(listOf(100f, 100f), result.columnWidths)
    }

    @Test
    fun limitedSpareWidthRaisesTheNarrowestColumnsFirst() {
        val result = resolveMarkdownTableWidths(
            preferredWidths = listOf(160f, 100f, 80f),
            minimumWidths = listOf(40f, 40f, 40f),
            availableWidth = 400f,
        )

        assertEquals(400f, result.tableWidth)
        assertEquals(listOf(160f, 120f, 120f), result.columnWidths)
    }

    @Test
    fun columnsCanHaveDifferentMeasuredMinimums() {
        val result = resolveMarkdownTableWidths(
            preferredWidths = listOf(140f, 80f),
            minimumWidths = listOf(90f, 40f),
            availableWidth = 160f,
        )

        assertEquals(160f, result.tableWidth)
        assertWidthsClose(listOf(106.66667f, 53.333332f), result.columnWidths)
    }

    private fun assertWidthsClose(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedWidth, actualWidth) ->
            assertTrue(
                kotlin.math.abs(expectedWidth - actualWidth) < 0.001f,
                "Expected $expectedWidth but was $actualWidth",
            )
        }
    }
}
