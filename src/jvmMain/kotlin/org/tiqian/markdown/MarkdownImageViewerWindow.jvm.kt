package org.tiqian.markdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
internal actual fun MarkdownImageViewerPlatformWindow(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
        content = content,
    )
}

@Composable
internal actual fun MarkdownImageViewerSystemBarsEffect(visible: Boolean) = Unit
