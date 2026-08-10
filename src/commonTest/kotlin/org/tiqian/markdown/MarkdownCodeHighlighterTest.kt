package org.tiqian.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownCodeHighlighterTest {
    @Test
    fun kotlinUsesSourceOffsetsForLexicalKinds() {
        val code = """@Composable
fun render(document: MarkdownRenderDocument, count: Int = 2) {
    // Keep source offsets intact.
    val label = "item"
    TiqianMarkdown(document)
}"""

        val highlights = DefaultMarkdownCodeHighlighter.highlight(code, "kts")

        assertKind(code, highlights, "@Composable", MarkdownCodeHighlightKind.Annotation)
        assertKind(code, highlights, "fun", MarkdownCodeHighlightKind.Keyword)
        assertKind(code, highlights, "render", MarkdownCodeHighlightKind.Function)
        assertKind(code, highlights, "MarkdownRenderDocument", MarkdownCodeHighlightKind.Type)
        assertKind(code, highlights, "Int", MarkdownCodeHighlightKind.Type)
        assertKind(code, highlights, "2", MarkdownCodeHighlightKind.Number)
        assertKind(code, highlights, "// Keep source offsets intact.", MarkdownCodeHighlightKind.Comment)
        assertKind(code, highlights, "\"item\"", MarkdownCodeHighlightKind.String)
        assertKind(code, highlights, "TiqianMarkdown", MarkdownCodeHighlightKind.Function)
        assertNonOverlapping(highlights)
    }

    @Test
    fun jsonDistinguishesPropertyNamesFromStringValues() {
        val code = """{"name": "Tiqian", "enabled": true, "count": 3}"""

        val highlights = DefaultMarkdownCodeHighlighter.highlight(code, "json")

        assertKind(code, highlights, "\"name\"", MarkdownCodeHighlightKind.Property)
        assertKind(code, highlights, "\"Tiqian\"", MarkdownCodeHighlightKind.String)
        assertKind(code, highlights, "true", MarkdownCodeHighlightKind.Constant)
        assertKind(code, highlights, "3", MarkdownCodeHighlightKind.Number)
        assertNonOverlapping(highlights)
    }

    @Test
    fun markupClassifiesTagsAttributesAndQuotedValues() {
        val code = """<!-- note --><article lang="zh-CN">正文</article>"""

        val highlights = DefaultMarkdownCodeHighlighter.highlight(code, "html")

        assertKind(code, highlights, "<!-- note -->", MarkdownCodeHighlightKind.Comment)
        assertKind(code, highlights, "article", MarkdownCodeHighlightKind.Tag)
        assertKind(code, highlights, "lang", MarkdownCodeHighlightKind.Attribute)
        assertKind(code, highlights, "\"zh-CN\"", MarkdownCodeHighlightKind.String)
        assertNonOverlapping(highlights)
    }

    @Test
    fun unknownAndMissingLanguagesRemainPlain() {
        assertTrue(DefaultMarkdownCodeHighlighter.highlight("fun value() = 1", null).isEmpty())
        assertTrue(DefaultMarkdownCodeHighlighter.highlight("fun value() = 1", "unknown-language").isEmpty())
    }

    private fun assertKind(
        code: String,
        highlights: List<MarkdownCodeHighlight>,
        token: String,
        kind: MarkdownCodeHighlightKind,
    ) {
        val start = code.indexOf(token)
        assertTrue(start >= 0, "fixture token not found: $token")
        assertEquals(
            kind,
            highlights.firstOrNull { it.range.start == start && it.range.endExclusive == start + token.length }?.kind,
            "wrong or missing highlight for $token",
        )
    }

    private fun assertNonOverlapping(highlights: List<MarkdownCodeHighlight>) {
        highlights.zipWithNext().forEach { (left, right) ->
            assertTrue(left.range.endExclusive <= right.range.start, "$left overlaps $right")
        }
    }
}
