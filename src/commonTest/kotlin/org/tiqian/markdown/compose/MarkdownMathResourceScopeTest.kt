package org.tiqian.markdown.compose

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.tiqian.markdown.MarkdownBlockQuote
import org.tiqian.markdown.MarkdownCodeBlock
import org.tiqian.markdown.MarkdownList
import org.tiqian.markdown.MarkdownListItem
import org.tiqian.markdown.MarkdownNodeKey
import org.tiqian.markdown.MarkdownNodeMetadata
import org.tiqian.markdown.MarkdownParagraph
import org.tiqian.markdown.MarkdownSourceSpan
import org.tiqian.markdown.MarkdownText
import org.tiqian.markdown.MarkdownTextMark
import org.tiqian.markdown.MarkdownTextRange
import org.tiqian.markdown.MarkdownTextSpan

class MarkdownMathResourceScopeTest {
    @Test
    fun nestedInlineMathRequestsDocumentScopedResources() {
        val expression = "x+y"
        val paragraph = MarkdownParagraph(
            text = MarkdownText(
                value = "前${expression}后",
                spans = listOf(
                    MarkdownTextSpan(
                        range = MarkdownTextRange(1, 1 + expression.length),
                        mark = MarkdownTextMark.InlineMath(expression),
                    ),
                ),
            ),
            metadata = metadata(3),
        )
        val blocks = listOf(
            MarkdownBlockQuote(
                blocks = listOf(
                    MarkdownList(
                        ordered = false,
                        startNumber = 1,
                        tight = true,
                        items = listOf(MarkdownListItem(listOf(paragraph), metadata = metadata(2))),
                        metadata = metadata(1),
                    ),
                ),
                metadata = metadata(0),
            ),
        )

        assertTrue(blocks.containsMarkdownMath())
    }

    @Test
    fun ordinaryDocumentDoesNotLoadMathResources() {
        val blocks = listOf(
            MarkdownParagraph(MarkdownText("只有正文。"), metadata(0)),
            MarkdownCodeBlock("x+y", "text", null, metadata(1)),
        )

        assertFalse(blocks.containsMarkdownMath())
    }

    private fun metadata(key: Int) = MarkdownNodeMetadata(
        key = MarkdownNodeKey(key, emptyList()),
        sourceSpan = MarkdownSourceSpan(0, 0, 0, 0, 0, 0),
    )
}
