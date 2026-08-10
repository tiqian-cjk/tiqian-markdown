package org.tiqian.markdown

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
