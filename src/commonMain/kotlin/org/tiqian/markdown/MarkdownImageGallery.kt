package org.tiqian.markdown

/** Returns block images in document order, including images nested in structural blocks. */
internal fun MarkdownRenderDocument.markdownImageGallery(): List<MarkdownImageBlock> =
    blocks.markdownImageGallery()

internal fun List<MarkdownBlock>.markdownImageGallery(): List<MarkdownImageBlock> = buildList {
    this@markdownImageGallery.forEach { block -> addMarkdownImages(block) }
}

private fun MutableList<MarkdownImageBlock>.addMarkdownImages(block: MarkdownBlock) {
    when (block) {
        is MarkdownImageBlock -> add(block)
        is MarkdownBlockQuote -> block.blocks.forEach(::addMarkdownImages)
        is MarkdownList -> block.items.forEach { item -> item.blocks.forEach(::addMarkdownImages) }
        is MarkdownFootnoteDefinition -> block.blocks.forEach(::addMarkdownImages)
        else -> Unit
    }
}
