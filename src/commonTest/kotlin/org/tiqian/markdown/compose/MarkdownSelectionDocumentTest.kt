package org.tiqian.markdown.compose

import org.tiqian.markdown.MarkdownCodeBlock
import org.tiqian.markdown.MarkdownFootnoteDefinition
import org.tiqian.markdown.MarkdownList
import org.tiqian.markdown.MarkdownListItem
import org.tiqian.markdown.MarkdownNodeKey
import org.tiqian.markdown.MarkdownNodeMetadata
import org.tiqian.markdown.MarkdownParagraph
import org.tiqian.markdown.MarkdownSourceSpan
import org.tiqian.markdown.MarkdownTaskState
import org.tiqian.markdown.MarkdownText
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownSelectionDocumentTest {
    @Test
    fun projectsVirtualizedContentInReadingOrderWithoutComposingIt() {
        val listItem = MarkdownListItem(
            blocks = listOf(paragraph(3, "列表正文")),
            metadata = metadata(2),
        )
        val taskItem = MarkdownListItem(
            blocks = listOf(paragraph(9, "任务正文")),
            task = MarkdownTaskState.Checked,
            metadata = metadata(8),
        )
        val blocks = listOf(
            paragraph(1, "首段"),
            MarkdownList(false, 1, true, listOf(listItem, taskItem), metadata(4)),
            MarkdownCodeBlock("val answer = 42", "kotlin", null, metadata(5)),
            MarkdownFootnoteDefinition("note", 1, listOf(paragraph(7, "脚注正文")), metadata(6)),
        )

        val fragments = blocks.markdownSelectionFragments()

        assertEquals(
            listOf("首段", "•", "列表正文", "[x]", "任务正文", "val answer = 42", "[1]", "脚注正文"),
            fragments.map { it.text.text },
        )
        assertEquals(listOf("\n", " ", "\n", " ", "\n", "\n", " ", "\n"), fragments.map { it.separatorAfter })
    }

    private fun paragraph(key: Int, text: String) = MarkdownParagraph(MarkdownText(text), metadata(key))

    private fun metadata(key: Int) = MarkdownNodeMetadata(
        key = MarkdownNodeKey(key, listOf(key)),
        sourceSpan = MarkdownSourceSpan(0, 0, 0, 0, 0, 0),
    )
}
