package org.tiqian.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownAnnotatedStringTest {
    @Test
    fun lowersOverlappingStylesAndLinks() {
        val text = MarkdownText(
            value = "粗体链接",
            spans = listOf(
                MarkdownTextSpan(MarkdownTextRange(0, 4), MarkdownTextMark.Strong),
                MarkdownTextSpan(
                    MarkdownTextRange(2, 4),
                    MarkdownTextMark.Link("https://example.com"),
                ),
            ),
        )

        val annotated = text.toAnnotatedString(
            MarkdownStyle(link = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline)),
        )

        assertEquals("粗体链接", annotated.text)
        assertTrue(annotated.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        val link = annotated.getLinkAnnotations(0, annotated.length).single().item
        assertEquals("https://example.com", (link as LinkAnnotation.Url).url)
    }

    @Test
    fun rubyKeepsBaseTextAndTiqianAnnotation() {
        val text = MarkdownText(
            value = "漢字",
            spans = listOf(
                MarkdownTextSpan(MarkdownTextRange(0, 2), MarkdownTextMark.Ruby("かんじ")),
            ),
        )

        val annotated = text.toAnnotatedString(MarkdownStyle())

        assertEquals("漢字", annotated.text)
        assertEquals("かんじ", annotated.getStringAnnotations(0, annotated.length).single().item)
    }

    @Test
    fun abbreviationDoesNotInventAnUnconsumedStringAnnotation() {
        val text = MarkdownText(
            value = "CLREQ",
            spans = listOf(
                MarkdownTextSpan(
                    MarkdownTextRange(0, 5),
                    MarkdownTextMark.Abbreviation("Requirements for Chinese Text Layout"),
                ),
            ),
        )

        val annotated = text.toAnnotatedString(MarkdownStyle())

        assertTrue(annotated.getStringAnnotations(0, annotated.length).isEmpty())
    }

    @Test
    fun keyboardAndFootnoteUseDedicatedStyles() {
        val keyboardStyle = SpanStyle(color = Color.Red, fontWeight = FontWeight.Medium)
        val footnoteStyle = SpanStyle(color = Color.Green)
        val text = MarkdownText(
            value = "Ctrl[1]",
            spans = listOf(
                MarkdownTextSpan(MarkdownTextRange(0, 4), MarkdownTextMark.KeyboardInput),
                MarkdownTextSpan(MarkdownTextRange(4, 7), MarkdownTextMark.Footnote("1", 1)),
            ),
        )

        val annotated = text.toAnnotatedString(
            MarkdownStyle(
                keyboardInput = keyboardStyle,
                footnoteReference = footnoteStyle,
            ),
        )

        assertTrue(annotated.spanStyles.any { it.start == 0 && it.end == 4 && it.item.color == Color.Red })
        assertTrue(
            annotated.spanStyles.any {
                    it.start == 4 && it.end == 7 &&
                    it.item.color == Color.Green &&
                    it.item.fontSize == androidx.compose.ui.unit.TextUnit.Unspecified &&
                    it.item.baselineShift == BaselineShift.Superscript
            },
        )
        assertTrue(annotated.getLinkAnnotations(4, 7).single().item is LinkAnnotation.Clickable)
        val attachment = annotated.getStringAnnotations(4, 7).single()
        assertEquals("Previous", attachment.item)
        assertEquals(4, attachment.start)
        assertEquals(7, attachment.end)
    }

    @Test
    fun repeatedFootnoteReferencesEachLinkToTheSharedDefinition() {
        val activatedLabels = mutableListOf<String>()
        val text = MarkdownText(
            value = "甲[1]乙[1]",
            spans = listOf(
                MarkdownTextSpan(MarkdownTextRange(1, 4), MarkdownTextMark.Footnote("same", 1)),
                MarkdownTextSpan(MarkdownTextRange(5, 8), MarkdownTextMark.Footnote("same", 1)),
            ),
        )

        val annotated = text.toAnnotatedString(
            style = MarkdownStyle(),
            onFootnoteClick = { activatedLabels += it },
        )
        val links = annotated.getLinkAnnotations(0, annotated.length)

        assertEquals(2, links.size)
        links.forEach { link -> link.item.linkInteractionListener?.onClick(link.item) }
        assertEquals(listOf("same", "same"), activatedLabels)
    }

    @Test
    fun superAndSubscriptUseOpticallyCompensatedEditorialGeometry() {
        val text = MarkdownText(
            value = "x2H2",
            spans = listOf(
                MarkdownTextSpan(MarkdownTextRange(1, 2), MarkdownTextMark.Superscript),
                MarkdownTextSpan(MarkdownTextRange(3, 4), MarkdownTextMark.Subscript),
            ),
        )

        val annotated = text.toAnnotatedString(MarkdownStyle())
        val superscript = annotated.spanStyles.single { it.start == 1 && it.end == 2 }.item
        val subscript = annotated.spanStyles.single { it.start == 3 && it.end == 4 }.item

        assertEquals(0.75.em, superscript.fontSize)
        assertEquals(FontWeight.Medium, superscript.fontWeight)
        assertEquals(BaselineShift.Superscript, superscript.baselineShift)
        assertEquals(0.75.em, subscript.fontSize)
        assertEquals(FontWeight.Medium, subscript.fontWeight)
        assertEquals(BaselineShift(-0.25f), subscript.baselineShift)
    }

    @Test
    fun defaultFootnoteReferenceUsesSuperscriptOpticalCompensation() {
        val text = MarkdownText(
            value = "[1]",
            spans = listOf(
                MarkdownTextSpan(MarkdownTextRange(0, 3), MarkdownTextMark.Footnote("1", 1)),
            ),
        )

        val annotated = text.toAnnotatedString(MarkdownStyle())
        val reference = annotated.spanStyles.single { it.start == 0 && it.end == 3 }.item

        assertEquals(0.75.em, reference.fontSize)
        assertEquals(FontWeight.Medium, reference.fontWeight)
        assertEquals(BaselineShift.Superscript, reference.baselineShift)
    }
}
