package org.tiqian.markdown.compose


import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

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
        footnote = footnote.copy(color = colorScheme.outline),
        quoteBarColor = colorScheme.outlineVariant,
        quoteText = quoteText.copy(color = colorScheme.onSurfaceVariant),
        codeBackground = colorScheme.surfaceContainer,
        codeMetaBackground = colorScheme.surfaceContainerHigh,
        codeLineNumberColor = colorScheme.outline,
        codeHighlight = codeHighlight.copy(
            comment = codeHighlight.comment.copy(color = colorScheme.outline),
            keyword = codeHighlight.keyword.copy(
                color = colorScheme.primary,
                fontWeight = FontWeight.Medium,
            ),
            string = codeHighlight.string.copy(color = colorScheme.tertiary),
            number = codeHighlight.number.copy(
                color = colorScheme.secondary,
                fontWeight = FontWeight.Medium,
            ),
            type = codeHighlight.type.copy(
                color = colorScheme.tertiary,
                fontWeight = FontWeight.Medium,
            ),
            function = codeHighlight.function.copy(color = colorScheme.secondary),
            property = codeHighlight.property.copy(color = colorScheme.secondary),
            annotation = codeHighlight.annotation.copy(
                color = colorScheme.tertiary,
                fontWeight = FontWeight.Medium,
            ),
            variable = codeHighlight.variable.copy(color = colorScheme.onSurface),
            operator = codeHighlight.operator.copy(color = colorScheme.onSurfaceVariant),
            punctuation = codeHighlight.punctuation.copy(color = colorScheme.onSurfaceVariant),
            tag = codeHighlight.tag.copy(
                color = colorScheme.primary,
                fontWeight = FontWeight.Medium,
            ),
            attribute = codeHighlight.attribute.copy(color = colorScheme.secondary),
            constant = codeHighlight.constant.copy(
                color = colorScheme.secondary,
                fontWeight = FontWeight.Medium,
            ),
            escape = codeHighlight.escape.copy(
                color = colorScheme.primary,
                fontWeight = FontWeight.Medium,
            ),
            markup = codeHighlight.markup.copy(color = colorScheme.onSurface),
        ),
        mathBackground = Color.Unspecified,
        // Markdown body sits on the M3 surface (onSurface text), so author TeX colors adapt against
        // surface by default. Without a backdrop the adapter is a no-op, leaving author \color/\bbox
        // unadapted — and often unreadable — on dark themes.
        math = math.copy(authorColorBackdrop = colorScheme.surface),
        tableBorderColor = colorScheme.outlineVariant,
        tableHeaderBackground = colorScheme.surfaceContainerLow,
        tableText = tableText.copy(color = colorScheme.onSurface),
        imageOutlineColor = colorScheme.outline.copy(alpha = 0.15f),
        caption = caption.copy(color = colorScheme.outline),
        thematicBreakColor = colorScheme.outlineVariant,
    )
}
