package org.tiqian.markdown.compose

import org.tiqian.markdown.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.use
import kotlinx.coroutines.runBlocking
import org.tiqian.compose.CjkInlineObject
import org.tiqian.compose.CjkInlineObjectBoundary
import org.tiqian.compose.CjkInlineObjectPreferredStretch
import org.tiqian.compose.CjkInlineObjectPreferredStretchKind
import org.tiqian.compose.CjkText
import org.tiqian.core.Ic
import org.tiqian.core.LayoutResult
import org.tiqian.core.LineLengthGrid
import org.tiqian.core.ParagraphStyle
import org.tiqian.core.TextRange as CoreTextRange
import org.tiqian.core.positionedClusters
import org.tiqian.markdown.compose.generated.resources.Res
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class MarkdownMathRenderingTest {
    @Test
    fun bundledLeteSansMathContainsOpenTypeMathTable() = runBlocking {
        val bytes = Res.readBytes("font/lete_sans_math_regular.otf")

        assertEquals("OTTO", bytes.decodeToString(0, 4))
        val tableCount = bytes.readUnsignedShort(4)
        val tags = (0 until tableCount).map { index ->
            val offset = 12 + index * 16
            bytes.decodeToString(offset, offset + 4)
        }
        assertTrue("MATH" in tags, "bundled Lete Sans Math is missing its OpenType MATH table")
        listOf(0x2A00, 0x2AFF, 0x1D49C).forEach { codePoint ->
            assertTrue(
                bytes.format12CmapContains(codePoint),
                "bundled Lete Sans Math is missing U+${codePoint.toString(16).uppercase()}",
            )
        }
    }

    @Test
    fun defaultInlineRendererRetainsTrueBaselineMetrics() {
        var capturedMetrics: MarkdownInlineMetrics? = null
        ImageComposeScene(width = 320, height = 120) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                val content = DefaultMarkdownMathInlineSlot(
                    MarkdownTextMark.InlineMath("\\frac{x_1}{y^2}"),
                    MarkdownStyle(),
                    TextStyle(fontSize = 24.sp),
                )
                capturedMetrics = content?.metrics
                content?.content?.invoke(content.alternateText)
            }
        }.use { scene ->
            scene.render(0L)
        }

        val metrics = assertNotNull(capturedMetrics)
        assertTrue(metrics.widthPx > 0f)
        assertTrue(metrics.ascentPx > 0f)
        assertTrue(metrics.descentPx > 0f)
        assertEquals(metrics.heightPx, metrics.ascentPx + metrics.descentPx, 0.001f)
    }

    @Test
    fun defaultInlineRendererConnectsHostTextForChineseLatinAndBoldTextCommands() {
        val expression = "x+\\text{中文 rate}+\\textbf{重点}"
        var content: MarkdownInlineContent? = null
        ImageComposeScene(width = 560, height = 140) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                content = DefaultMarkdownMathInlineSlot(
                    MarkdownTextMark.InlineMath(expression),
                    MarkdownStyle(),
                    TextStyle(fontSize = 24.sp),
                )
                content?.content?.invoke(content!!.alternateText)
            }
        }.use { scene -> scene.render(0L) }

        val rendered = assertNotNull(content)
        assertEquals(expression, rendered.alternateText)
        assertTrue(assertNotNull(rendered.metrics).widthPx > 0f)
    }

    @Test
    fun defaultMathLowersToOneTiqianInlineObjectInsteadOfComposeTextCenter() {
        val expression = "\\frac{x_1}{y^2}"
        val source = "前${expression}后"
        var resolved: ResolvedMarkdownText? = null

        ImageComposeScene(width = 320, height = 120) {
            resolved = resolveMarkdownText(
                text = MarkdownText(
                    value = source,
                    spans = listOf(
                        MarkdownTextSpan(
                            range = MarkdownTextRange(1, 1 + expression.length),
                            mark = MarkdownTextMark.InlineMath(expression),
                        ),
                    ),
                ),
                style = MarkdownStyle(),
                textStyle = TextStyle(fontSize = 24.sp),
                inlineSlots = DefaultMarkdownInlineSlots,
                onLinkClick = null,
                onFootnoteClick = null,
            )
        }.use { scene -> scene.render(0L) }

        val lowered = assertNotNull(resolved)
        assertEquals(source, lowered.annotated.text)
        val inlineObject = lowered.tiqianInlineObjects.single()
        assertEquals(1, inlineObject.start)
        assertEquals(1 + expression.length, inlineObject.endExclusive)
        assertTrue(inlineObject.content.leadingBoundary.participatesInUniformStretch)
        assertTrue(inlineObject.content.trailingBoundary.participatesInUniformStretch)
    }

    @Test
    fun inlineFormulaUsesExistingInterlineSpaceBeforeChangingTheBaselineGrid() {
        val expression = "\\sqrt{x^{n}}"
        val source = "甲\n$expression\n乙"
        var layoutResult: LayoutResult? = null

        ImageComposeScene(width = 480, height = 240) {
            val resolved = resolveMath(source, expression)
            val density = LocalDensity.current
            val objects = resolved.tiqianInlineObjects.map { inlineObject ->
                val metrics = assertNotNull(inlineObject.content.metrics)
                CjkInlineObject(
                    range = TextRange(inlineObject.start, inlineObject.endExclusive),
                    advance = with(density) { metrics.widthPx.toDp() },
                    ascent = with(density) { metrics.ascentPx.toDp() },
                    descent = with(density) { metrics.descentPx.toDp() },
                    leadingBoundary = inlineObject.content.leadingBoundary.toCjkBoundary(density),
                    trailingBoundary = inlineObject.content.trailingBoundary.toCjkBoundary(density),
                    content = { inlineObject.content.content(inlineObject.content.alternateText) },
                )
            }
            CjkText(
                text = resolved.annotated,
                modifier = Modifier.width(400.dp),
                style = TextStyle(fontSize = 24.sp, lineHeight = 36.sp),
                inlineObjects = objects,
                onTextLayout = { layoutResult = it },
            )
        }.use { scene -> scene.render(0L) }

        val result = assertNotNull(layoutResult)
        assertEquals(3, result.lines.size)
        assertEquals(36f, result.lines[1].baseline - result.lines[0].baseline, 0.01f)
        assertEquals(36f, result.lines[2].baseline - result.lines[1].baseline, 0.01f)
        val decision = assertNotNull(result.debug.inlineObjectLineHeightDecision)
        assertTrue(decision.lineExtras.all { it == 0f }, "unexpected line extras: ${decision.lineExtras}")
        // Ink-tight fragment metrics keep the formula within the existing interline space, so the
        // baseline grid is preserved without even redistributing the line-box boundary.
        assertTrue(
            decision.boundaryShiftsAfter.all { kotlin.math.abs(it) < 0.01f },
            "formula should fit existing interline space without boundary redistribution: $decision",
        )
        assertEquals("ExistingInterlineSpaceFitsInlineObjects", decision.reason)
    }

    // Pending: the engine keeps \left…\right as one auto-sized MathDelimited atom, so its baseline
    // content is not yet exposed as breakable fragments (ordinary (...) already are). Enabling this
    // needs per-line delimiter sizing across a break — tracked as its own slice.
    @Ignore
    @Test
    fun rendererOwnedFragmentsBreakInsideBaselineDelimitersButNotStackedStructures() {
        val expression = "x+(a+b)+\\left[c=d\\right]+\\frac{e+f}{g}+h^{i+j}"
        val source = "前${expression}）后"
        var resolved: ResolvedMarkdownText? = null

        ImageComposeScene(width = 640, height = 160) {
            resolved = resolveMath(source, expression)
        }.use { scene -> scene.render(0L) }

        val fragments = assertNotNull(resolved).tiqianInlineObjects.map { it.content.alternateText }
        assertEquals(expression, fragments.joinToString(separator = ""))
        assertTrue(
            fragments.windowed(3).any { it == listOf("(a", "+", "b)") },
            "ordinary delimiter content must expose both sides of its baseline +",
        )
        assertTrue(
            fragments.windowed(3).any { it == listOf("\\left[c", "=", "d\\right]") },
            "automatic delimiter content must expose both sides of its baseline =",
        )
        assertEquals(1, fragments.count { "\\frac{e+f}{g}" in it }, "fraction contents must stay atomic")
        assertEquals(1, fragments.count { "h^{i+j}" in it }, "script contents must stay atomic")
    }

    @Test
    fun defaultMathLowersTopLevelFragmentsToIndependentInlineObjects() {
        val expression = "a+b=c-d"
        val source = "前${expression}）后"
        var resolved: ResolvedMarkdownText? = null

        ImageComposeScene(width = 320, height = 120) {
            resolved = resolveMarkdownText(
                text = MarkdownText(
                    value = source,
                    spans = listOf(
                        MarkdownTextSpan(
                            range = MarkdownTextRange(1, 1 + expression.length),
                            mark = MarkdownTextMark.InlineMath(expression),
                        ),
                    ),
                ),
                style = MarkdownStyle(),
                textStyle = TextStyle(fontSize = 24.sp),
                inlineSlots = DefaultMarkdownInlineSlots,
                onLinkClick = null,
                onFootnoteClick = null,
            )
        }.use { scene -> scene.render(0L) }

        val lowered = assertNotNull(resolved)
        assertEquals(source, lowered.annotated.text)
        assertEquals(
            listOf("a", "+", "b", "=", "c", "-", "d"),
            lowered.tiqianInlineObjects.map { it.content.alternateText },
        )
        assertEquals(
            1 until 1 + expression.length,
            lowered.tiqianInlineObjects.first().start until lowered.tiqianInlineObjects.last().endExclusive,
        )
        assertTrue(lowered.tiqianInlineObjects.first().content.leadingBoundary.participatesInUniformStretch)
        assertTrue(lowered.tiqianInlineObjects.last().content.trailingBoundary.participatesInUniformStretch)
        lowered.tiqianInlineObjects.dropLast(1).forEach { fragment ->
            assertTrue(fragment.content.trailingBoundary.participatesInUniformStretch)
            assertTrue(fragment.content.trailingBoundary.shrinkCapacityPx > 0f)
        }
        val boundaries = lowered.tiqianInlineObjects.map { it.content.trailingBoundary }
        assertEquals(MarkdownInlinePreferredStretchKind.BinaryOperator, boundaries[0].preferredStretch?.kind)
        assertTrue(boundaries[0].preventsLineBreak, "the boundary before + is adjustment-only")
        assertEquals(0f, boundaries[0].lineEndDiscardableAdvancePx)
        assertEquals(MarkdownInlinePreferredStretchKind.BinaryOperator, boundaries[1].preferredStretch?.kind)
        assertTrue(!boundaries[1].preventsLineBreak, "the boundary after + is the real break")
        assertEquals(boundaries[1].shrinkCapacityPx, boundaries[1].lineEndDiscardableAdvancePx)
        assertEquals(MarkdownInlinePreferredStretchKind.Relation, boundaries[2].preferredStretch?.kind)
        assertTrue(boundaries[2].preventsLineBreak, "the boundary before = is adjustment-only")
        assertEquals(0f, boundaries[2].lineEndDiscardableAdvancePx)
        assertEquals(MarkdownInlinePreferredStretchKind.Relation, boundaries[3].preferredStretch?.kind)
        assertTrue(!boundaries[3].preventsLineBreak, "the boundary after = is the real break")
        assertEquals(boundaries[3].shrinkCapacityPx, boundaries[3].lineEndDiscardableAdvancePx)
    }

    @Test
    fun rendererClassifiesMeasuredMathSpacesForPreferredStretch() {
        val expression = "a,b=c+d"
        val source = "前${expression}后"
        var resolved: ResolvedMarkdownText? = null

        ImageComposeScene(width = 320, height = 120) {
            resolved = resolveMath(source, expression)
        }.use { scene -> scene.render(0L) }

        val objects = assertNotNull(resolved).tiqianInlineObjects
        assertEquals(listOf("a,", "b", "=", "c", "+", "d"), objects.map { it.content.alternateText })
        assertEquals(
            listOf(
                MarkdownInlinePreferredStretchKind.PunctuationTrailing,
                MarkdownInlinePreferredStretchKind.Relation,
                MarkdownInlinePreferredStretchKind.Relation,
                MarkdownInlinePreferredStretchKind.BinaryOperator,
                MarkdownInlinePreferredStretchKind.BinaryOperator,
            ),
            objects.dropLast(1).map { it.content.trailingBoundary.preferredStretch?.kind },
        )
        objects.dropLast(1).forEach {
            val boundary = it.content.trailingBoundary
            val preferred = boundary.preferredStretch!!
            val capacity = preferred.capacityPx
            assertTrue(capacity > 0f)
            assertEquals(
                boundary.shrinkCapacityPx,
                preferred.naturalWidthPx,
                0.001f,
                "the preferred natural width must come from the same measured math gap",
            )
            assertEquals(0.5f * 24f, preferred.targetWidthPx, 0.001f)
        }
        assertTrue(objects[0].content.trailingBoundary.preventsLineBreak, "comma trailing stretch is not a new break")
        assertTrue(objects[1].content.trailingBoundary.preventsLineBreak, "the boundary before = stays closed")
        assertTrue(!objects[2].content.trailingBoundary.preventsLineBreak, "the boundary after = remains breakable")
    }

    @Test
    fun narrowParagraphBreaksBetweenFormulaFragmentsWithoutOversizedLines() {
        val expression = "a+b=c-d=e+f"
        val source = "甲${expression}）乙"
        var layoutResult: LayoutResult? = null

        ImageComposeScene(width = 180, height = 240) {
            val resolved = resolveMath(source, expression)
            val density = LocalDensity.current
            val objects = resolved.tiqianInlineObjects.map { inlineObject ->
                val metrics = assertNotNull(inlineObject.content.metrics)
                CjkInlineObject(
                    range = TextRange(inlineObject.start, inlineObject.endExclusive),
                    advance = with(density) { metrics.widthPx.toDp() },
                    ascent = with(density) { metrics.ascentPx.toDp() },
                    descent = with(density) { metrics.descentPx.toDp() },
                    leadingBoundary = inlineObject.content.leadingBoundary.toCjkBoundary(density),
                    trailingBoundary = inlineObject.content.trailingBoundary.toCjkBoundary(density),
                    content = { inlineObject.content.content(inlineObject.content.alternateText) },
                )
            }
            CjkText(
                text = resolved.annotated,
                modifier = Modifier.width(96.dp),
                style = TextStyle(fontSize = 24.sp, lineHeight = 36.sp),
                inlineObjects = objects,
                onTextLayout = { layoutResult = it },
            )
        }.use { scene -> scene.render(0L) }

        val result = assertNotNull(layoutResult)
        assertTrue(result.lines.size > 1, "formula fragments should provide real line-break opportunities")
        assertTrue(
            result.lines.all { it.visualWidth <= 96.5f },
            "fragmented formula must not inflate a line past the paragraph measure: ${result.lines.map { it.visualWidth }}",
        )
        assertTrue(
            result.lines.drop(1).none {
                source.substring(it.range.start, it.range.end).firstOrNull() in setOf('+', '-', '=')
            },
            "binary and relation operators must remain on the preceding line",
        )
        assertTrue(
            result.lines.dropLast(1).any {
                source.substring(it.range.start, it.range.end).lastOrNull() in setOf('+', '-', '=')
            },
            "the regression paragraph should exercise a post-operator break",
        )
        assertTrue(
            result.debug.lineEdgeTrimDecisions.any {
                it.reason == "InlineObjectLineEndDiscardableGlue" && it.trimAmount > 0f
            },
            "a chosen post-operator break must discard the renderer's trailing math space: " +
                result.debug.lineEdgeTrimDecisions,
        )
        assertTrue(
            result.lines.drop(1).none { source.substring(it.range.start, it.range.end).startsWith("）") },
            "closing punctuation after a formula must retain kinsoku protection",
        )
        assertTrue(
            result.debug.justificationDecisions
                .flatMap { it.allocations }
                .any {
                    it.kind == "InlineObjectRelation" ||
                        it.kind == "InlineObjectBinaryOperator" ||
                        it.kind == "InlineObjectBoundary"
                },
            "wrapped inline formula must use its measured boundary stretch resources",
        )
    }

    @Test
    fun commaAttachedToInlineFormulaNeverStartsWrappedLine() {
        val expression = "a+b=c-d=e+f"
        for (comma in listOf('，', ',')) {
            val source = "甲$expression${comma}乙"
            var observedFormulaCompression = false
            for (width in listOf(72, 84, 96, 108, 120)) {
                var layoutResult: LayoutResult? = null
                ImageComposeScene(width = 240, height = 420) {
                    val resolved = resolveMath(source, expression)
                    val density = LocalDensity.current
                    val objects = resolved.tiqianInlineObjects.map { inlineObject ->
                        val metrics = assertNotNull(inlineObject.content.metrics)
                        CjkInlineObject(
                            range = TextRange(inlineObject.start, inlineObject.endExclusive),
                            advance = with(density) { metrics.widthPx.toDp() },
                            ascent = with(density) { metrics.ascentPx.toDp() },
                            descent = with(density) { metrics.descentPx.toDp() },
                            leadingBoundary = inlineObject.content.leadingBoundary.toCjkBoundary(density),
                            trailingBoundary = inlineObject.content.trailingBoundary.toCjkBoundary(density),
                            content = { inlineObject.content.content(inlineObject.content.alternateText) },
                        )
                    }
                    CjkText(
                        text = resolved.annotated,
                        modifier = Modifier.width(width.dp),
                        style = TextStyle(fontSize = 24.sp, lineHeight = 36.sp),
                        inlineObjects = objects,
                        onTextLayout = { layoutResult = it },
                    )
                }.use { scene -> scene.render(0L) }

                val result = assertNotNull(layoutResult)
                val lineTexts = result.lines.map { source.substring(it.range.start, it.range.end) }
                assertTrue(
                    lineTexts.none { it.startsWith(comma) },
                    "width=$width comma=$comma lines=$lineTexts",
                )
                assertTrue(
                    result.debug.contextualKinsokuDecisions.any {
                        it.sourceText == comma.toString() && it.reason == "InlineObjectAttachedKinsoku"
                    },
                )
                observedFormulaCompression = observedFormulaCompression ||
                    result.debug.lineDecisions
                        .mapNotNull { it.repairDecision }
                        .flatMap { it.pushInAllocations }
                        .any { allocation ->
                            allocation.shrink > 0f &&
                                resolvedFormulaRange(source, expression).let { formula ->
                                    allocation.clusterRange.start >= formula.start &&
                                        allocation.clusterRange.end <= formula.endExclusive
                                }
                        }
            }
            assertTrue(
                observedFormulaCompression,
                "the measured operator blank must participate in last-resort compression for comma=$comma",
            )
        }
    }

    @Test
    fun realArticleFormulaSeparatorSpaceCollapsesBeforeChineseComma() {
        val expression = "a(x)=2^x"
        val source = "比如，题目给出1,2,4,8,?，一个最直观的感觉是 $expression ，它是A000079，显然下一项是16喵。"
        val expressionStart = source.indexOf(expression)
        val separatorRange = CoreTextRange(expressionStart + expression.length, expressionStart + expression.length + 1)
        val commaRange = CoreTextRange(separatorRange.end, separatorRange.end + 1)

        for (width in listOf(96, 120, 144, 168)) {
            var layoutResult: LayoutResult? = null
            var lastFormulaRange: CoreTextRange? = null
            ImageComposeScene(width = 240, height = 720) {
                val resolved = resolveMath(source, expression)
                val density = LocalDensity.current
                val objects = resolved.tiqianInlineObjects.map { inlineObject ->
                    val metrics = assertNotNull(inlineObject.content.metrics)
                    CjkInlineObject(
                        range = TextRange(inlineObject.start, inlineObject.endExclusive),
                        advance = with(density) { metrics.widthPx.toDp() },
                        ascent = with(density) { metrics.ascentPx.toDp() },
                        descent = with(density) { metrics.descentPx.toDp() },
                        leadingBoundary = inlineObject.content.leadingBoundary.toCjkBoundary(density),
                        trailingBoundary = inlineObject.content.trailingBoundary.toCjkBoundary(density),
                        content = { inlineObject.content.content(inlineObject.content.alternateText) },
                    )
                }
                lastFormulaRange = resolved.tiqianInlineObjects.last().let {
                    CoreTextRange(it.start, it.endExclusive)
                }
                CjkText(
                    text = resolved.annotated,
                    modifier = Modifier.width(width.dp),
                    style = TextStyle(fontSize = 24.sp, lineHeight = 36.sp),
                    inlineObjects = objects,
                    onTextLayout = { layoutResult = it },
                )
            }.use { scene -> scene.render(0L) }

            val result = assertNotNull(layoutResult)
            val lineTexts = result.lines.map { source.substring(it.range.start, it.range.end) }
            assertTrue(
                lineTexts.none { it.trimStart().startsWith('，') },
                "width=$width lines=$lineTexts",
            )
            assertEquals(0f, result.clusters.single { it.range == separatorRange }.advance, 0.001f)
            assertEquals(1, result.debug.inlineObjectPunctuationAttachmentDecisions.size)

            val positioned = result.positionedClusters().associateBy { it.range }
            val formula = assertNotNull(positioned[assertNotNull(lastFormulaRange)])
            val comma = assertNotNull(positioned[commaRange])
            assertEquals(formula.lineIndex, comma.lineIndex)
            assertEquals(
                formula.right,
                comma.left,
                0.01f,
                "formula and attached punctuation must share one visual boundary at width=$width",
            )
        }
    }

    @Test
    fun relationWithOrdinaryTexWhitespaceStretchesOnBothSidesInFinalComposeGeometry() {
        assertOperatorStretchIsVisibleOnBothSides("a = b", "=")
    }

    @Test
    fun binaryOperatorWithOrdinaryTexWhitespaceStretchesOnBothSidesInFinalComposeGeometry() {
        assertOperatorStretchIsVisibleOnBothSides("4770.7917x^4 - 28624.5833x^3", "-")
    }

    private fun assertOperatorStretchIsVisibleOnBothSides(
        expression: String,
        operator: String,
    ) {
        val source = "${expression} EnglishText"
        var layoutResult: LayoutResult? = null
        var formulaObjects: List<ResolvedTiqianInlineObject> = emptyList()

        ImageComposeScene(width = 1600, height = 180) {
            val resolved = resolveMath(source, expression)
            formulaObjects = resolved.tiqianInlineObjects
            val density = LocalDensity.current
            val objects = formulaObjects.map { inlineObject ->
                val metrics = assertNotNull(inlineObject.content.metrics)
                CjkInlineObject(
                    range = TextRange(inlineObject.start, inlineObject.endExclusive),
                    advance = with(density) { metrics.widthPx.toDp() },
                    ascent = with(density) { metrics.ascentPx.toDp() },
                    descent = with(density) { metrics.descentPx.toDp() },
                    leadingBoundary = inlineObject.content.leadingBoundary.toCjkBoundary(density),
                    trailingBoundary = inlineObject.content.trailingBoundary.toCjkBoundary(density),
                    content = { inlineObject.content.content(inlineObject.content.alternateText) },
                )
            }
            val naturalFormulaWidth = formulaObjects.sumOf {
                assertNotNull(it.content.metrics).widthPx.toDouble()
            }.toFloat()
            val preferredHeadroom = formulaObjects.dropLast(1).sumOf {
                it.content.trailingBoundary.preferredStretch?.capacityPx?.toDouble() ?: 0.0
            }.toFloat()
            val lineWidthPx = naturalFormulaWidth + preferredHeadroom + 1f
            CjkText(
                text = resolved.annotated,
                modifier = Modifier.width(with(density) { lineWidthPx.toDp() }),
                style = TextStyle(fontSize = 24.sp, lineHeight = 36.sp),
                paragraphStyle = ParagraphStyle(
                    firstLineIndent = Ic.Zero,
                    lineLengthGrid = LineLengthGrid(enabled = false),
                ),
                inlineObjects = objects,
                onTextLayout = { layoutResult = it },
            )
        }.use { scene -> scene.render(0L) }

        val result = assertNotNull(layoutResult)
        assertTrue(result.lines.size > 1)
        val operatorIndex = formulaObjects.indexOfFirst { it.content.alternateText.trim() == operator }
        assertTrue(operatorIndex > 0 && operatorIndex < formulaObjects.lastIndex)
        val positioned = result.positionedClusters().associateBy { it.range }
        val objectRanges = formulaObjects.map { CoreTextRange(it.start, it.endExclusive) }
        val left = assertNotNull(positioned[objectRanges[operatorIndex - 1]])
        val operatorCluster = assertNotNull(positioned[objectRanges[operatorIndex]])
        val right = assertNotNull(positioned[objectRanges[operatorIndex + 1]])
        assertEquals(left.lineIndex, operatorCluster.lineIndex)
        assertEquals(operatorCluster.lineIndex, right.lineIndex)

        val beforeObject = formulaObjects[operatorIndex - 1]
        val operatorObject = formulaObjects[operatorIndex]
        val beforeBoundary = assertNotNull(beforeObject.content.trailingBoundary.preferredStretch)
        val afterBoundary = assertNotNull(operatorObject.content.trailingBoundary.preferredStretch)
        val leftBodyWidth = assertNotNull(beforeObject.content.metrics).widthPx - beforeBoundary.naturalWidthPx
        val operatorBodyWidth = assertNotNull(operatorObject.content.metrics).widthPx - afterBoundary.naturalWidthPx
        val beforeOperator = operatorCluster.drawX - (left.drawX + leftBodyWidth)
        val afterOperator = right.drawX - (operatorCluster.drawX + operatorBodyWidth)
        assertEquals(beforeOperator, afterOperator, 0.01f)
        assertTrue(
            beforeOperator > beforeBoundary.targetWidthPx,
            "operator=$operator before=$beforeOperator after=$afterOperator " +
                "target=${beforeBoundary.targetWidthPx} " +
                "lines=${result.lines.map { source.substring(it.range.start, it.range.end) }} " +
                "allocations=${result.debug.justificationDecisions.flatMap { it.allocations }}",
        )
    }

    @Composable
    private fun resolveMath(source: String, expression: String): ResolvedMarkdownText =
        resolveMarkdownText(
            text = MarkdownText(
                value = source,
                spans = listOf(
                    MarkdownTextSpan(
                        range = source.indexOf(expression).let { start ->
                            require(start >= 0)
                            MarkdownTextRange(start, start + expression.length)
                        },
                        mark = MarkdownTextMark.InlineMath(expression),
                    ),
                ),
            ),
            style = MarkdownStyle(),
            textStyle = TextStyle(fontSize = 24.sp),
            inlineSlots = DefaultMarkdownInlineSlots,
            onLinkClick = null,
            onFootnoteClick = null,
        )

    private fun resolvedFormulaRange(source: String, expression: String): MarkdownTextRange {
        val start = source.indexOf(expression)
        return MarkdownTextRange(start, start + expression.length)
    }

    private fun MarkdownInlineBoundaryAdjustment.toCjkBoundary(
        density: androidx.compose.ui.unit.Density,
    ): CjkInlineObjectBoundary = CjkInlineObjectBoundary(
        participatesInUniformStretch = participatesInUniformStretch,
        preferredStretch = preferredStretch?.let {
            CjkInlineObjectPreferredStretch(
                kind = when (it.kind) {
                    MarkdownInlinePreferredStretchKind.PunctuationTrailing ->
                        CjkInlineObjectPreferredStretchKind.PunctuationTrailing
                    MarkdownInlinePreferredStretchKind.Relation ->
                        CjkInlineObjectPreferredStretchKind.Relation
                    MarkdownInlinePreferredStretchKind.BinaryOperator ->
                        CjkInlineObjectPreferredStretchKind.BinaryOperator
                },
                naturalWidth = with(density) { it.naturalWidthPx.toDp() },
                targetWidth = with(density) { it.targetWidthPx.toDp() },
            )
        },
        shrinkCapacity = with(density) { shrinkCapacityPx.toDp() },
        lineEndDiscardableAdvance = with(density) { lineEndDiscardableAdvancePx.toDp() },
        preventsLineBreak = preventsLineBreak,
    )

    /**
     * A polynomial's superscript ink pokes only slightly above the body ascent, so a line that
     * carries formula fragments must stay near the host body line height. Reporting the math font's
     * full line box (its declared descent + leading) instead of ink propped every formula-bearing
     * line up by ~0.17em even when the ink fit the body line. Density is 1 in ImageComposeScene, so
     * `.sp` values are pixels: a 30px body line must not inflate toward the old ~34px math box.
     */
    @Test
    fun inlineFormulaLinesStayNearBodyLineHeight() {
        val expression = "x^{2}+2x^{3}+3x^{4}+4x^{5}+5x^{6}"
        val source = "甲${expression}乙"
        var layoutResult: LayoutResult? = null
        ImageComposeScene(width = 240, height = 400) {
            val resolved = resolveMath(source, expression)
            val density = LocalDensity.current
            val objects = resolved.tiqianInlineObjects.map { inlineObject ->
                val metrics = assertNotNull(inlineObject.content.metrics)
                CjkInlineObject(
                    range = TextRange(inlineObject.start, inlineObject.endExclusive),
                    advance = with(density) { metrics.widthPx.toDp() },
                    ascent = with(density) { metrics.ascentPx.toDp() },
                    descent = with(density) { metrics.descentPx.toDp() },
                    leadingBoundary = inlineObject.content.leadingBoundary.toCjkBoundary(density),
                    trailingBoundary = inlineObject.content.trailingBoundary.toCjkBoundary(density),
                    content = { inlineObject.content.content(inlineObject.content.alternateText) },
                )
            }
            CjkText(
                text = resolved.annotated,
                modifier = Modifier.width(130.dp),
                style = TextStyle(fontSize = 24.sp, lineHeight = 30.sp),
                inlineObjects = objects,
                onTextLayout = { layoutResult = it },
            )
        }.use { scene -> scene.render(0L) }
        val result = assertNotNull(layoutResult)
        assertTrue(result.lines.size > 1, "the polynomial must wrap so formula lines are exercised")
        val heights = result.lines.map { it.bottom - it.top }
        assertTrue(
            heights.all { it <= 32f },
            "formula-bearing lines must stay near the 30px body line height, not the math font box: $heights",
        )
    }
}

private fun ByteArray.readUnsignedShort(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

private fun ByteArray.readUnsignedInt(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 24) or
        ((this[offset + 1].toInt() and 0xFF) shl 16) or
        ((this[offset + 2].toInt() and 0xFF) shl 8) or
        (this[offset + 3].toInt() and 0xFF)

private fun ByteArray.format12CmapContains(codePoint: Int): Boolean {
    val tableCount = readUnsignedShort(4)
    val cmapRecord = (0 until tableCount).firstOrNull { index ->
        val record = 12 + index * 16
        decodeToString(record, record + 4) == "cmap"
    } ?: return false
    val cmapOffset = readUnsignedInt(12 + cmapRecord * 16 + 8)
    val encodingCount = readUnsignedShort(cmapOffset + 2)
    for (index in 0 until encodingCount) {
        val record = cmapOffset + 4 + index * 8
        val subtable = cmapOffset + readUnsignedInt(record + 4)
        if (readUnsignedShort(subtable) != 12) continue
        val groupCount = readUnsignedInt(subtable + 12)
        for (groupIndex in 0 until groupCount) {
            val group = subtable + 16 + groupIndex * 12
            if (codePoint in readUnsignedInt(group)..readUnsignedInt(group + 4)) return true
        }
    }
    return false
}
