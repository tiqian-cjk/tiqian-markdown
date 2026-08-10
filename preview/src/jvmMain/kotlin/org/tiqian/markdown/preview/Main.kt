package org.tiqian.markdown.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.tiqian.markdown.MarkdownRenderDocument
import org.tiqian.markdown.MarkdownCustomInlinePresentation
import org.tiqian.markdown.MarkdownInlineDecoration
import org.tiqian.markdown.MarkdownInlineSlots
import org.tiqian.markdown.MarkdownImageContent
import org.tiqian.markdown.MarkdownImageLoadState
import org.tiqian.markdown.MarkdownImageProvider
import org.tiqian.markdown.MarkdownImageViewerHost
import org.tiqian.markdown.TiqianMarkdown

private data class PreviewCase(
    val name: String,
    val document: MarkdownRenderDocument,
    val width: Int,
    val horizontalPadding: Int = 24,
)

private val PreviewCases = listOf(
    PreviewCase("完整文章", PreviewDocuments.fullArticle, width = 600, horizontalPadding = 32),
    PreviewCase("标题层级", PreviewDocuments.headings, width = 420),
    PreviewCase("行内装饰", PreviewDocuments.inlineStyles, width = 420),
    PreviewCase("引用与列表", PreviewDocuments.quoteAndLists, width = 420),
    PreviewCase("代码、公式与表格", PreviewDocuments.codeMathAndTable, width = 600),
    PreviewCase("图片与查看器", PreviewDocuments.images, width = 600),
)

private val PreviewWidths = listOf(360, 420, 600, 840)

@Composable
private fun rememberPreviewInlineSlots(): MarkdownInlineSlots {
    val decorationColor = MaterialTheme.colorScheme.tertiary
    return remember(decorationColor) {
        MarkdownInlineSlots(
            custom = { mark, _, _ ->
                if (mark.kind == "dashed-underline") {
                    MarkdownCustomInlinePresentation(
                        decoration = MarkdownInlineDecoration.DashedUnderline(decorationColor),
                    )
                } else {
                    null
                }
            },
        )
    }
}

@Composable
private fun rememberPreviewImageProvider(): MarkdownImageProvider {
    val background = MaterialTheme.colorScheme.primaryContainer
    val foreground = MaterialTheme.colorScheme.onPrimaryContainer
    return remember(background, foreground) {
        { block ->
            MarkdownImageContent(
                intrinsicSize = IntSize(block.widthPixels ?: 1200, block.heightPixels ?: 800),
                loadState = MarkdownImageLoadState.Success,
            ) { modifier ->
                Canvas(modifier.background(background)) {
                    val gridColor = foreground.copy(alpha = 0.18f)
                    repeat(7) { index ->
                        val x = size.width * index / 6f
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 2f)
                    }
                    repeat(5) { index ->
                        val y = size.height * index / 4f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 2f)
                    }
                    drawCircle(
                        color = foreground.copy(alpha = 0.82f),
                        radius = size.minDimension * 0.18f,
                        center = center,
                        style = Stroke(width = size.minDimension * 0.035f),
                    )
                    drawLine(
                        color = foreground,
                        start = Offset(size.width * 0.2f, size.height * 0.72f),
                        end = Offset(size.width * 0.8f, size.height * 0.28f),
                        strokeWidth = size.minDimension * 0.025f,
                    )
                }
            }
        }
    }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Tiqian Markdown 样式预览",
        state = rememberWindowState(size = DpSize(1260.dp, 900.dp)),
    ) {
        MarkdownPreviewApp()
    }
}

@Composable
private fun MarkdownPreviewApp() {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var previewWidth by remember { mutableIntStateOf(PreviewCases.first().width) }
    var dark by remember { mutableStateOf(false) }
    val selected = PreviewCases[selectedIndex]
    val shellColors = if (dark) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = shellColors) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            PreviewControls(
                selectedIndex = selectedIndex,
                previewWidth = previewWidth,
                dark = dark,
                onSelect = { index ->
                    selectedIndex = index
                    previewWidth = PreviewCases[index].width
                },
                onWidthChange = { previewWidth = it },
                onDarkChange = { dark = it },
            )
            MarkdownViewport(
                previewCase = selected,
                previewWidth = previewWidth,
            )
        }
    }
}

@Composable
private fun PreviewControls(
    selectedIndex: Int,
    previewWidth: Int,
    dark: Boolean,
    onSelect: (Int) -> Unit,
    onWidthChange: (Int) -> Unit,
    onDarkChange: (Boolean) -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Column(
            Modifier
                .width(244.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Markdown 样张", style = MaterialTheme.typography.titleLarge)
            Text(
                "样张直接调用正式渲染器；这里不维护另一套样式。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            PreviewCases.forEachIndexed { index, previewCase ->
                if (index == selectedIndex) {
                    Button(
                        onClick = { onSelect(index) },
                        modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(previewCase.name)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelect(index) },
                        modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(previewCase.name)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("版心宽度", style = MaterialTheme.typography.titleMedium)
            PreviewWidths.chunked(2).forEach { widths ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    widths.forEach { width ->
                        if (width == previewWidth) {
                            Button(
                                onClick = { onWidthChange(width) },
                                modifier = Modifier.weight(1f).pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Text("$width")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onWidthChange(width) },
                                modifier = Modifier.weight(1f).pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Text("$width")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("暗色样式", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = dark,
                    onCheckedChange = onDarkChange,
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                )
            }
        }
    }
}

@Composable
private fun MarkdownViewport(
    previewCase: PreviewCase,
    previewWidth: Int,
) {
    val inlineSlots = rememberPreviewInlineSlots()
    val imageProvider = rememberPreviewImageProvider()
    val workbenchColor = MaterialTheme.colorScheme.surfaceContainer
    Box(
        Modifier
            .fillMaxSize()
            .background(workbenchColor)
            .horizontalScroll(rememberScrollState())
            .padding(28.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .width(previewWidth.dp)
                .fillMaxHeight(),
            shadowElevation = 4.dp,
        ) {
            MarkdownImageViewerHost(
                imageProvider = imageProvider,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .verticalScroll(rememberScrollState()),
                ) {
                    TiqianMarkdown(
                        document = previewCase.document,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = previewCase.horizontalPadding.dp,
                                vertical = 28.dp,
                            ),
                        inlineSlots = inlineSlots,
                    )
                }
            }
        }
    }
}
