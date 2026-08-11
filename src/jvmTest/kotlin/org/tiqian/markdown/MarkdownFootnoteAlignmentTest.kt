package org.tiqian.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun markerRegionAbsorbsTheRemainderAfterContentRoundsDown() {
        assertEquals(
            WholeCellListGeometry(markerRegionWidthPx = 75, contentWidthPx = 525),
            wholeCellListGeometryPx(
                bodyMeasurePx = 600,
                minimumMarkerRegionWidthPx = 64,
                contentCellWidthPx = 21f,
            ),
            "the 64 px marker stays unrounded; the 11 px remainder belongs to its region",
        )
        assertEquals(
            WholeCellListGeometry(markerRegionWidthPx = 0, contentWidthPx = 20),
            wholeCellListGeometryPx(
                bodyMeasurePx = 20,
                minimumMarkerRegionWidthPx = 0,
                contentCellWidthPx = 24f,
            ),
            "a narrower-than-one-cell measure must remain usable",
        )
    }

    @Test
    fun footnoteGroupHasNoOuterOrInterItemGap() {
        val pixels = ImageComposeScene(width = 80, height = 96) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TiqianMarkdown(
                    document = MarkdownRenderDocument(
                        blocks = listOf(
                            MarkdownCustomBlock(
                                kind = "owner",
                                metadata = metadata(-1, listOf(-1)),
                            ),
                            footnote(index = 1, stableKey = 0),
                            footnote(index = 2, stableKey = 2),
                        ),
                    ),
                    style = MarkdownStyle(
                        body = TextStyle(color = Color.Black, fontSize = 24.sp, lineHeight = 36.sp),
                    ),
                    slots = MarkdownBlockSlots(
                        customBlock = { _, _ ->
                            Box(Modifier.fillMaxWidth().height(12.dp).background(Color.Black))
                        },
                        footnoteDefinition = { _, _ ->
                            Box(Modifier.fillMaxWidth().height(12.dp).background(Color.Black))
                        },
                    ),
                )
            }
        }.use { scene ->
            scene.render(0L).toComposeImageBitmap().toPixelMap()
        }

        val inkRows = (0 until pixels.height).filter { y ->
            (0 until pixels.width).any { x -> pixels[x, y] != Color.White }
        }
        assertTrue(inkRows.size > 24, "the owning block and both footnote rows must render")
        assertTrue(
            inkRows.zipWithNext().all { (first, second) -> second == first + 1 },
            "the footnote group must touch its owning block and have no inter-item spacer",
        )
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
