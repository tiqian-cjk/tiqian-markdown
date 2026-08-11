package org.tiqian.markdown

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.tiqian.math.core.MathFontWeight
import org.tiqian.math.core.MathTextOrigin
import org.tiqian.math.core.SourceRange
import org.tiqian.math.font.android.AndroidMathFontFamily
import org.tiqian.math.font.android.androidFormulaCapabilityEngine
import org.tiqian.math.layout.MathFormulaCapabilityResult
import org.tiqian.math.layout.MathLayoutOptions
import org.tiqian.math.layout.MathTextRunProviderResult
import org.tiqian.math.layout.MathTextRunRequest
import org.tiqian.shaping.nativefont.AndroidNativeTextShaper
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MarkdownMathHostTextDeviceTest {
    @Test
    fun chineseAndLatinTextAtomsUseReplayableTiqianHostFaces() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val provider = AndroidMarkdownMathTextRunProvider(
            shaper = AndroidNativeTextShaper(context),
            preferredFamilies = emptyList(),
        )
        val text = "中文 rate"
        val ready = assertIs<MathTextRunProviderResult.Ready>(
            provider.shapeTextAtom(
                MathTextRunRequest(
                    text = text,
                    sourceRange = SourceRange(10, 10 + text.length),
                    fontSizePx = 32f,
                    requestedWeight = MathFontWeight.Regular,
                    locale = "zh-Hans",
                    origin = MathTextOrigin.TextCommand,
                ),
            ),
        )

        assertTrue(ready.run.glyphs.isNotEmpty())
        assertTrue(!ready.run.missingGlyph)
        assertTrue(ready.run.width > 0f)
        assertEquals(setOf("CjkText", "LatinText"), ready.run.glyphs.map {
            assertNotNull(it.hostTextDecision).hostRole
        }.toSet())
        ready.run.glyphs.filter { it.glyphId.toInt() != 0 }.forEach { glyph ->
            val face = assertNotNull(provider.replayFace(glyph.faceId))
            assertNotNull(face.glyphPath(glyph.glyphId, 32f))
        }

        AndroidMathFontFamily.loadBundledLete(context).use { math ->
            val formula = assertIs<MathFormulaCapabilityResult.Ready>(
                math.androidFormulaCapabilityEngine(provider).evaluate(
                    "x+\\text{中文 rate}+\\textbf{重点}",
                    MathLayoutOptions(fontSizePx = 32f),
                ),
            )
            assertTrue(formula.layoutResult.box.glyphs.any { it.hostTextDecision != null })
        }
    }
}
