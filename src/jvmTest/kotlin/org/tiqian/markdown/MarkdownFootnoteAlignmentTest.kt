package org.tiqian.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.use
import org.tiqian.core.ic
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalComposeUiApi::class)
class MarkdownFootnoteAlignmentTest {
    @Test
    fun consecutiveFootnotesShareALeftAlignedWholeCellMarkerGutter() {
        val pixels = render(
            MarkdownRenderDocument(
                blocks = listOf(
                    footnote(index = 9, stableKey = 0),
                    footnote(index = 10, stableKey = 2),
                ),
            ),
        )

        val firstMarkerStart = pixels.inkMinX(0 until 42, 0 until 32)
        val secondMarkerStart = pixels.inkMinX(0 until 42, 32 until 64)
        val firstContentStart = pixels.inkMinX(42 until 120, 0 until 32)
        val secondContentStart = pixels.inkMinX(42 until 120, 32 until 64)

        assertEquals(firstMarkerStart, secondMarkerStart)
        assertEquals(firstContentStart, secondContentStart)
    }

    @Test
    fun markerGutterRoundsUpToWholeCharacterCells() {
        assertEquals(1.ic, wholeCellMarkerGutter(widestMarker = 24f, fontSize = 24f))
        assertEquals(2.ic, wholeCellMarkerGutter(widestMarker = 24.01f, fontSize = 24f))
    }

    private fun render(document: MarkdownRenderDocument): PixelMap =
        ImageComposeScene(width = 600, height = 96) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TiqianMarkdown(
                    document = document,
                    style = MarkdownStyle(
                        body = TextStyle(color = Color.Black, fontSize = 24.sp, lineHeight = 36.sp),
                        blockSpacingBodyLines = 0f,
                        compactBlockSpacingBodyLines = 0f,
                    ),
                )
            }
        }.use { scene ->
            scene.render(0L).toComposeImageBitmap().toPixelMap()
        }

    private fun footnote(index: Int, stableKey: Int) = MarkdownFootnoteDefinition(
        label = index.toString(),
        index = index,
        blocks = listOf(
            MarkdownParagraph(
                text = MarkdownText("正文"),
                metadata = metadata(stableKey + 1, listOf(stableKey, 0)),
            ),
        ),
        metadata = metadata(stableKey, listOf(stableKey)),
    )

    private fun metadata(stableKey: Int, path: List<Int>) = MarkdownNodeMetadata(
        key = MarkdownNodeKey(stableKey, path),
        sourceSpan = MarkdownSourceSpan(0, 0, 0, 0, 0, 0),
    )

    private fun PixelMap.inkMinX(xRange: IntRange, yRange: IntRange): Int =
        xRange.first { x -> yRange.any { y -> this[x, y] != Color.White } }
}
