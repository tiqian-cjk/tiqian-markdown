package org.tiqian.markdown.compose

import org.tiqian.markdown.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.use
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class MarkdownTableRenderingTest {
    @Test
    fun naturallyFittingCellsStayOnOneLineAndKeepEveryAlignmentVisible() {
        val document = MarkdownRenderDocument(
            blocks = listOf(
                MarkdownTable(
                    columnAlignments = listOf(
                        MarkdownTableAlignment.Start,
                        MarkdownTableAlignment.Center,
                        MarkdownTableAlignment.End,
                    ),
                    rows = listOf(
                        MarkdownTableRow(
                            cells = listOf(
                                MarkdownTableCell(
                                    text = MarkdownText("E=mc²"),
                                    alignment = MarkdownTableAlignment.Start,
                                    header = false,
                                    metadata = metadata(2),
                                ),
                                MarkdownTableCell(
                                    text = MarkdownText("正常"),
                                    alignment = MarkdownTableAlignment.Center,
                                    header = false,
                                    metadata = metadata(3),
                                ),
                                MarkdownTableCell(
                                    text = MarkdownText("100%"),
                                    alignment = MarkdownTableAlignment.End,
                                    header = false,
                                    metadata = metadata(4),
                                ),
                            ),
                            header = false,
                            metadata = metadata(1),
                        ),
                    ),
                    metadata = metadata(0),
                ),
            ),
        )
        val style = MarkdownStyle(
            body = TextStyle(color = Color.Black, fontSize = 24.sp, lineHeight = 36.sp),
            tableBorderColor = Color.White,
        )

        val rendered = ImageComposeScene(width = 320, height = 120) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TiqianMarkdown(document = document, style = style)
            }
        }.use { scene ->
            val pixels = scene.render(0L).toComposeImageBitmap().toPixelMap()
            val maxInkY = (0 until pixels.height).last { y ->
                (0 until pixels.width).any { x -> pixels[x, y] != Color.White }
            }
            val inkColumns = (0 until pixels.width).filter { x ->
                (0..maxInkY).any { y -> pixels[x, y] != Color.White }
            }
            val separatedTextRuns = inkColumns.zipWithNext().count { (left, right) -> right - left > 8 } + 1
            maxInkY to separatedTextRuns
        }

        assertTrue(rendered.first < 50, "a fitting cell wrapped to a second text line (max ink y=${rendered.first})")
        assertTrue(rendered.second >= 3, "not all three table alignments remained visible")
    }

    private fun metadata(key: Int) = MarkdownNodeMetadata(
        key = MarkdownNodeKey(key, listOf(key)),
        sourceSpan = MarkdownSourceSpan(0, 0, 0, 0, 0, 0),
    )
}
