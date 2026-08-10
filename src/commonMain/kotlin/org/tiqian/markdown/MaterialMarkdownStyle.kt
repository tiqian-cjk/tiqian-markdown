package org.tiqian.markdown

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Resolves [MarkdownPalette.Platform] from the current Material 3 theme.
 * Tiqian's heading ratios, paragraph rhythm and marker geometry remain unchanged.
 */
@Composable
fun rememberMarkdownStyle(
    palette: MarkdownPalette = MarkdownPalette.Platform,
    defaultStyle: MarkdownStyle = MarkdownStyle(),
): MarkdownStyle {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    return remember(palette, defaultStyle, colorScheme, typography) {
        when (palette) {
            MarkdownPalette.Default -> defaultStyle
            MarkdownPalette.Platform -> defaultStyle.mapMaterial3(colorScheme, typography)
        }
    }
}

internal fun MarkdownStyle.mapMaterial3(
    colorScheme: ColorScheme,
    typography: Typography,
): MarkdownStyle {
    val platformBody = typography.bodyLarge.merge(body).copy(color = colorScheme.onSurface)
    val platformCodeBlock = typography.bodyMedium.merge(codeBlock).copy(color = colorScheme.onSurface)
    return copy(
        body = platformBody,
        codeBlock = platformCodeBlock,
        codeMeta = codeMeta.copy(color = colorScheme.onSurfaceVariant),
        codeLanguage = codeLanguage.copy(color = colorScheme.onSurfaceVariant),
        link = link.copy(color = colorScheme.primary),
        inlineCode = inlineCode.copy(
            color = colorScheme.onSurface,
            background = colorScheme.surfaceContainerHigh,
        ),
        highlight = highlight.copy(
            color = colorScheme.onTertiaryContainer,
            background = colorScheme.tertiaryContainer,
        ),
        keyboardInput = keyboardInput.copy(
            color = colorScheme.onSurface,
            background = Color.Unspecified,
        ),
        keyboardInputBorderColor = colorScheme.outline,
        footnoteReference = footnoteReference.copy(color = colorScheme.primary),
        footnote = footnote.copy(color = colorScheme.onSurfaceVariant),
        quoteBarColor = colorScheme.outlineVariant,
        quoteText = quoteText.copy(color = colorScheme.onSurfaceVariant),
        codeBackground = colorScheme.surfaceContainer,
        codeMetaBackground = colorScheme.surfaceContainerHigh,
        codeLineNumberColor = colorScheme.onSurfaceVariant,
        codeHighlight = codeHighlight.copy(
            comment = codeHighlight.comment.copy(color = colorScheme.onSurfaceVariant),
            keyword = codeHighlight.keyword.copy(color = colorScheme.primary),
            string = codeHighlight.string.copy(color = colorScheme.tertiary),
            number = codeHighlight.number.copy(color = colorScheme.secondary),
            type = codeHighlight.type.copy(color = colorScheme.tertiary),
            function = codeHighlight.function.copy(color = colorScheme.primary),
            property = codeHighlight.property.copy(color = colorScheme.secondary),
            annotation = codeHighlight.annotation.copy(color = colorScheme.tertiary),
            variable = codeHighlight.variable.copy(color = colorScheme.onSurface),
            operator = codeHighlight.operator.copy(color = colorScheme.onSurface),
            punctuation = codeHighlight.punctuation.copy(color = colorScheme.onSurface),
            tag = codeHighlight.tag.copy(color = colorScheme.tertiary),
            attribute = codeHighlight.attribute.copy(color = colorScheme.secondary),
            constant = codeHighlight.constant.copy(color = colorScheme.secondary),
            escape = codeHighlight.escape.copy(color = colorScheme.primary),
            markup = codeHighlight.markup.copy(color = colorScheme.onSurface),
        ),
        mathBackground = Color.Unspecified,
        tableBorderColor = colorScheme.outlineVariant,
        tableHeaderBackground = colorScheme.surfaceContainerLow,
        tableText = tableText.copy(color = colorScheme.onSurface),
        caption = caption.copy(color = colorScheme.onSurfaceVariant),
        thematicBreakColor = colorScheme.outlineVariant,
    )
}
