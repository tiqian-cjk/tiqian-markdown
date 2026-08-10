package org.tiqian.markdown

import com.gallatinapps.syntaxmp.role.SyntaxRole
import com.gallatinapps.syntaxmp.tokenizer.SyntaxTokenizer

/**
 * Converts source code into non-overlapping semantic ranges without exposing a tokenizer model to
 * the renderer. Implementations must preserve the original UTF-16 source offsets exactly.
 */
fun interface MarkdownCodeHighlighter {
    fun highlight(code: String, language: String?): List<MarkdownCodeHighlight>
}

/**
 * The built-in KMP highlighter. It recognizes SyntaxMP's fenced-language labels and intentionally
 * leaves missing or unknown languages plain instead of guessing.
 */
object DefaultMarkdownCodeHighlighter : MarkdownCodeHighlighter {
    private val tokenizer = SyntaxTokenizer()

    override fun highlight(code: String, language: String?): List<MarkdownCodeHighlight> =
        tokenizer.tokenize(code, language).map { token ->
            MarkdownCodeHighlight(
                range = MarkdownTextRange(token.start, token.endExclusive),
                kind = token.role.toMarkdownKind(),
            )
        }
}

private fun SyntaxRole.toMarkdownKind(): MarkdownCodeHighlightKind = when (value.substringBefore('.')) {
    "keyword" -> MarkdownCodeHighlightKind.Keyword
    "string" -> MarkdownCodeHighlightKind.String
    "number" -> MarkdownCodeHighlightKind.Number
    "comment" -> MarkdownCodeHighlightKind.Comment
    "function" -> MarkdownCodeHighlightKind.Function
    "type" -> MarkdownCodeHighlightKind.Type
    "property" -> MarkdownCodeHighlightKind.Property
    "variable" -> MarkdownCodeHighlightKind.Variable
    "operator" -> MarkdownCodeHighlightKind.Operator
    "punctuation" -> MarkdownCodeHighlightKind.Punctuation
    "annotation" -> MarkdownCodeHighlightKind.Annotation
    "tag" -> MarkdownCodeHighlightKind.Tag
    "attribute" -> MarkdownCodeHighlightKind.Attribute
    "constant" -> MarkdownCodeHighlightKind.Constant
    "escape" -> MarkdownCodeHighlightKind.Escape
    "markup" -> MarkdownCodeHighlightKind.Markup
    else -> MarkdownCodeHighlightKind.Markup
}
