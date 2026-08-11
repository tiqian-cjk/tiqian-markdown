package org.tiqian.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MarkdownFootnotePlacementTest {
    @Test
    fun blockPlacementUsesFirstReferenceOrderAndEmitsEachDefinitionOnce() {
        val document = MarkdownRenderDocument(
            blocks = listOf(
                paragraph("甲", "b", "a"),
                paragraph("乙", "a"),
                definition("a", 1),
                definition("b", 2),
                definition("unused", 3),
            ),
        )

        val placed = document.placeFootnotes(MarkdownFootnotePlacement.AfterBlock)

        assertEquals(
            listOf("paragraph", "b", "a", "paragraph", "unused"),
            placed.map { (it as? MarkdownFootnoteDefinition)?.label ?: "paragraph" },
        )
    }

    @Test
    fun blockPlacementKeepsListItemFootnotesOutsideTheList() {
        val list = MarkdownList(
            ordered = false,
            startNumber = 1,
            tight = true,
            items = listOf(
                MarkdownListItem(
                    blocks = listOf(paragraph("列表", "note")),
                    metadata = metadata(2),
                ),
            ),
            metadata = metadata(1),
        )
        val placed = MarkdownRenderDocument(
            blocks = listOf(list, definition("note", 1)),
        ).placeFootnotes(MarkdownFootnotePlacement.AfterBlock)

        val placedList = assertIs<MarkdownList>(placed[0])
        assertEquals(1, placedList.items.single().blocks.size)
        assertIs<MarkdownParagraph>(placedList.items.single().blocks.single())
        assertEquals("note", assertIs<MarkdownFootnoteDefinition>(placed[1]).label)
    }

    @Test
    fun blockPlacementKeepsQuoteFootnotesOutsideTheQuote() {
        val quote = MarkdownBlockQuote(
            blocks = listOf(
                paragraph("引文甲", "first"),
                paragraph("引文乙", "second"),
            ),
            metadata = metadata(1),
        )
        val placed = MarkdownRenderDocument(
            blocks = listOf(
                quote,
                paragraph("后文"),
                definition("first", 1),
                definition("second", 2),
            ),
        ).placeFootnotes(MarkdownFootnotePlacement.AfterBlock)

        val placedQuote = assertIs<MarkdownBlockQuote>(placed[0])
        assertEquals(2, placedQuote.blocks.size)
        assertEquals("first", assertIs<MarkdownFootnoteDefinition>(placed[1]).label)
        assertEquals("second", assertIs<MarkdownFootnoteDefinition>(placed[2]).label)
        assertIs<MarkdownParagraph>(placed[3])
    }

    @Test
    fun sectionPlacementClosesNestedHeadingSectionsDeterministically() {
        val placed = MarkdownRenderDocument(
            blocks = listOf(
                heading(1, "章"),
                paragraph("章引", "chapter"),
                heading(2, "第一节"),
                paragraph("节引", "section"),
                heading(2, "第二节"),
                paragraph("正文"),
                definition("chapter", 1),
                definition("section", 2),
            ),
        ).placeFootnotes(MarkdownFootnotePlacement.AfterSection)

        assertEquals(
            listOf("h1", "p", "h2", "p", "section", "h2", "p", "chapter"),
            placed.map { block ->
                when (block) {
                    is MarkdownHeading -> "h${block.level}"
                    is MarkdownFootnoteDefinition -> block.label
                    else -> "p"
                }
            },
        )
    }

    @Test
    fun articlePlacementKeepsDefinitionOrderAndRemovesOriginalPositions() {
        val placed = MarkdownRenderDocument(
            blocks = listOf(
                definition("a", 1),
                paragraph("正文", "b"),
                definition("b", 2),
            ),
        ).placeFootnotes(MarkdownFootnotePlacement.EndOfArticle)

        assertIs<MarkdownParagraph>(placed[0])
        assertEquals(listOf("a", "b"), placed.drop(1).map { assertIs<MarkdownFootnoteDefinition>(it).label })
    }

    private fun paragraph(value: String, vararg labels: String): MarkdownParagraph {
        val suffix = labels.joinToString(separator = "") { "[$it]" }
        var offset = value.length
        val spans = labels.mapIndexed { index, label ->
            val marker = "[$label]"
            val span = MarkdownTextSpan(
                range = MarkdownTextRange(offset, offset + marker.length),
                mark = MarkdownTextMark.Footnote(label, index + 1),
            )
            offset += marker.length
            span
        }
        return MarkdownParagraph(MarkdownText(value + suffix, spans), metadata(value.hashCode()))
    }

    private fun heading(level: Int, value: String) = MarkdownHeading(
        level = level,
        id = null,
        text = MarkdownText(value),
        metadata = metadata(value.hashCode()),
    )

    private fun definition(label: String, index: Int) = MarkdownFootnoteDefinition(
        label = label,
        index = index,
        blocks = listOf(paragraph("脚注 $label")),
        metadata = metadata(10_000 + index),
    )

    private fun metadata(key: Int) = MarkdownNodeMetadata(
        key = MarkdownNodeKey(key, listOf(key)),
        sourceSpan = MarkdownSourceSpan(0, 0, 0, 0, 0, 0),
    )
}
