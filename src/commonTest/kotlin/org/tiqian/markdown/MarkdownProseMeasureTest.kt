package org.tiqian.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownProseMeasureTest {
    private val policy = MarkdownProseMeasure()

    @Test
    fun narrowMeasureUsesAvailableIntegralCells() {
        assertEquals(20f, resolveMarkdownProseMeasureCells(20.75f, policy))
        assertEquals(32f, resolveMarkdownProseMeasureCells(32f, policy))
    }

    @Test
    fun eachDoublingAdmitsEightMoreCells() {
        assertEquals(40f, resolveMarkdownProseMeasureCells(64f, policy))
        assertEquals(48f, resolveMarkdownProseMeasureCells(128f, policy))
    }

    @Test
    fun intermediateAndExtremeWidthsStayWithinTheHardLimit() {
        assertEquals(36f, resolveMarkdownProseMeasureCells(48f, policy))
        assertEquals(44f, resolveMarkdownProseMeasureCells(96f, policy))
        assertEquals(48f, resolveMarkdownProseMeasureCells(512f, policy))
    }
}
