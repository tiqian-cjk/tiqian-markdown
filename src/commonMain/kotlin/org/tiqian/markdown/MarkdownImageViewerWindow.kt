package org.tiqian.markdown

import androidx.compose.runtime.Composable

/** Platform window used when the Markdown host does not itself occupy the viewport. */
@Composable
internal expect fun MarkdownImageViewerPlatformWindow(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
)

/** Keeps platform system bars in step with the viewer controls and restores them on disposal. */
@Composable
internal expect fun MarkdownImageViewerSystemBarsEffect(visible: Boolean)
