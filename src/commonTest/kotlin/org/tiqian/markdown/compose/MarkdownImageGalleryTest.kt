package org.tiqian.markdown.compose

import org.tiqian.markdown.*

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownImageGalleryTest {
    @Test
    fun collectsNestedBlockImagesInDocumentOrder() {
        val first = image(1)
        val second = image(2)
        val third = image(3)
        val document = MarkdownRenderDocument(
            blocks = listOf(
                first,
                MarkdownBlockQuote(listOf(second), metadata(20)),
                MarkdownList(
                    ordered = false,
                    startNumber = 1,
                    tight = true,
                    items = listOf(MarkdownListItem(listOf(third), metadata = metadata(30))),
                    metadata = metadata(29),
                ),
            ),
        )

        assertEquals(listOf(first, second, third), document.markdownImageGallery())
    }

    @Test
    fun viewerSessionStartsAtOpenedImageAndChangesSelection() {
        val first = image(1)
        val second = image(2)
        val state = MarkdownImageViewerState()

        state.show(second, listOf(first, second))
        assertEquals(second, state.activeImage)

        state.activeSession!!.select(0)
        assertEquals(first, state.activeImage)
    }

    @Test
    fun pagerKeyIsStableAndAndroidBundleSaveable() {
        assertEquals("31:7.2", MarkdownNodeKey(31, listOf(7, 2)).imageViewerSaveableKey())
    }

    @Test
    fun scrimSeparatesSystemBarTopBarPlatformAndGradientRegions() {
        assertEquals(
            0.3f,
            markdownImageViewerScrimFlatFraction(
                scrimHeightPx = 400f,
                systemBarHeightPx = 100f,
                topBarHeightPx = 200f,
            ),
        )
        assertEquals(0.05f, markdownImageViewerScrimFlatFraction(400f, 0f, 200f))
        assertEquals(1f, markdownImageViewerScrimFlatFraction(400f, 500f, 200f))
    }

    @Test
    fun consecutiveGesturesAdvanceFromSecondToFourthImageOnePageAtATime() {
        val thirdImage = resolveMarkdownImagePagerTarget(
            anchorPage = 1,
            dragDistancePx = -420f,
            pointerVelocityPxPerSecond = -2_000f,
            minimumFlingVelocityPxPerSecond = 1_000f,
            pageSizePx = 1_080,
            pageCount = 6,
        )
        val fourthImage = resolveMarkdownImagePagerTarget(
            anchorPage = thirdImage,
            dragDistancePx = -380f,
            pointerVelocityPxPerSecond = -1_800f,
            minimumFlingVelocityPxPerSecond = 1_000f,
            pageSizePx = 1_080,
            pageCount = 6,
        )

        assertEquals(2, thirdImage)
        assertEquals(3, fourthImage)
    }

    @Test
    fun oneGestureCanNeverSkipMoreThanOnePage() {
        assertEquals(
            3,
            resolveMarkdownImagePagerTarget(
                anchorPage = 2,
                dragDistancePx = -5_000f,
                pointerVelocityPxPerSecond = -50_000f,
                minimumFlingVelocityPxPerSecond = 1_000f,
                pageSizePx = 1_080,
                pageCount = 8,
            ),
        )
    }

    @Test
    fun slowShortDragReturnsToGestureAnchor() {
        assertEquals(
            2,
            resolveMarkdownImagePagerTarget(
                anchorPage = 2,
                dragDistancePx = -400f,
                pointerVelocityPxPerSecond = -500f,
                minimumFlingVelocityPxPerSecond = 1_000f,
                pageSizePx = 1_080,
                pageCount = 6,
            ),
        )
    }

    @Test
    fun slowDragPastHalfPageAdvancesAndGalleryBoundsClamp() {
        assertEquals(
            3,
            resolveMarkdownImagePagerTarget(
                anchorPage = 2,
                dragDistancePx = -541f,
                pointerVelocityPxPerSecond = -500f,
                minimumFlingVelocityPxPerSecond = 1_000f,
                pageSizePx = 1_080,
                pageCount = 6,
            ),
        )
        assertEquals(
            0,
            resolveMarkdownImagePagerTarget(0, 700f, 2_000f, 1_000f, 1_080, 6),
        )
        assertEquals(
            5,
            resolveMarkdownImagePagerTarget(5, -700f, -2_000f, 1_000f, 1_080, 6),
        )
    }

    private fun image(index: Int) = MarkdownImageBlock(
        destination = "image-$index",
        description = "Image $index",
        title = null,
        widthPixels = 100,
        heightPixels = 100,
        metadata = metadata(index),
    )

    private fun metadata(index: Int) = MarkdownNodeMetadata(
        key = MarkdownNodeKey(index, listOf(index)),
        sourceSpan = MarkdownSourceSpan(index, index + 1, 1, index, 1, index + 1),
    )
}
