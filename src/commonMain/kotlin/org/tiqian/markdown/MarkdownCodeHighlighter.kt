package org.tiqian.markdown

import com.gallatinapps.syntaxmp.role.SyntaxRole
import com.gallatinapps.syntaxmp.tokenizer.SyntaxTokenizer

internal data class MarkdownCodeLanguage(
    val rawLabel: String,
    val tokenizerLabel: String,
    val displayLabel: String,
)

private data class VersionedLanguageAlias(
    val tokenizerLabel: String,
)

private val versionedLanguageAliases = mapOf(
    "python2" to VersionedLanguageAlias("python"),
    "python-2" to VersionedLanguageAlias("python"),
    "py2" to VersionedLanguageAlias("python"),
    "python3" to VersionedLanguageAlias("python"),
    "python-3" to VersionedLanguageAlias("python"),
    "py3" to VersionedLanguageAlias("python"),
)

private val builtInSyntaxTokenizer = SyntaxTokenizer()

internal fun resolveMarkdownCodeLanguage(language: String?): MarkdownCodeLanguage? {
    val rawLabel = language?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val alias = versionedLanguageAliases[rawLabel.lowercase()]
    return MarkdownCodeLanguage(
        rawLabel = rawLabel,
        tokenizerLabel = alias?.tokenizerLabel
            ?: builtInSyntaxTokenizer.resolveLanguageId(rawLabel)?.value
            ?: rawLabel,
        displayLabel = rawLabel.uppercase(),
    )
}

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
    override fun highlight(code: String, language: String?): List<MarkdownCodeHighlight> =
        builtInSyntaxTokenizer.tokenize(
            code = code,
            languageLabel = resolveMarkdownCodeLanguage(language)?.tokenizerLabel,
        ).map { token ->
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
