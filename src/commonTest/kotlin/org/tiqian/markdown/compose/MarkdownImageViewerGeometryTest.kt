package org.tiqian.markdown.compose

import org.tiqian.markdown.*

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarkdownImageViewerGeometryTest {
    @Test
    fun ordinaryImageFitsAndCentersInViewport() {
        val layout = assertNotNull(
            calculateMarkdownImageViewerLayout(IntSize(1000, 800), IntSize(1200, 800), 600f),
        )
        assertFalse(layout.isLongImage)
        assertEquals(5f / 6f, layout.resetScale, 0.0001f)
        assertEquals(0f, layout.baseOrigin.x, 0.0001f)
        assertEquals(200f / 3f, layout.baseOrigin.y, 0.0001f)
    }

    @Test
    fun longImageIsTopAlignedAndWidthLimited() {
        val layout = assertNotNull(
            calculateMarkdownImageViewerLayout(IntSize(1200, 900), IntSize(800, 4000), 600f),
        )
        assertTrue(layout.isLongImage)
        assertEquals(0.75f, layout.resetScale)
        assertEquals(Offset(300f, 0f), layout.baseOrigin)
    }

    @Test
    fun anchoredZoomKeepsContentPointUnderFinger() {
        val translated = calculateMarkdownImageViewerScaledTranslation(
            currentTranslation = Offset(100f, 50f),
            currentScale = 1f,
            targetScale = 2f,
            anchor = Offset(300f, 250f),
            destinationAnchor = Offset(300f, 250f),
        )
        assertEquals(Offset(-100f, -150f), translated)
    }

    @Test
    fun clampCentersSmallContentAndStopsLargeContentAtEdges() {
        val layout = assertNotNull(
            calculateMarkdownImageViewerLayout(IntSize(1000, 800), IntSize(500, 400), 600f),
        )
        assertEquals(Offset.Zero, clampMarkdownImageViewerTranslation(Offset(90f, -70f), layout, 2f))
        assertEquals(
            Offset(-500f, -400f),
            clampMarkdownImageViewerTranslation(Offset(-900f, -900f), layout, 3f),
        )
    }

    @Test
    fun imageSmallerThanViewportDoesNotCreateAnInvertedScaleRange() {
        val layout = assertNotNull(
            calculateMarkdownImageViewerLayout(IntSize(1000, 800), IntSize(100, 100), 600f),
        )
        val minimumScale = calculateMarkdownImageViewerMinimumScale(layout)
        assertEquals(8f, minimumScale)
        assertEquals(8f, coerceMarkdownImageViewerSettledScale(4f, minimumScale))
        assertEquals(8f, coerceMarkdownImageViewerGestureScale(9f, minimumScale))
    }
}
