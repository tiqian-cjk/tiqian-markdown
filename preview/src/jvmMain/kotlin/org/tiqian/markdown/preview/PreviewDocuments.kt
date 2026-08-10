package org.tiqian.markdown.preview

import org.tiqian.markdown.MarkdownBlock
import org.tiqian.markdown.MarkdownBlockQuote
import org.tiqian.markdown.MarkdownCodeBlock
import org.tiqian.markdown.MarkdownFootnoteDefinition
import org.tiqian.markdown.MarkdownHeading
import org.tiqian.markdown.MarkdownHtmlBlock
import org.tiqian.markdown.MarkdownImageBlock
import org.tiqian.markdown.MarkdownList
import org.tiqian.markdown.MarkdownListItem
import org.tiqian.markdown.MarkdownMathBlock
import org.tiqian.markdown.MarkdownNodeKey
import org.tiqian.markdown.MarkdownNodeMetadata
import org.tiqian.markdown.MarkdownParagraph
import org.tiqian.markdown.MarkdownRenderDocument
import org.tiqian.markdown.MarkdownSourceSpan
import org.tiqian.markdown.MarkdownTable
import org.tiqian.markdown.MarkdownTableAlignment
import org.tiqian.markdown.MarkdownTableCell
import org.tiqian.markdown.MarkdownTableRow
import org.tiqian.markdown.MarkdownTaskState
import org.tiqian.markdown.MarkdownText
import org.tiqian.markdown.MarkdownTextMark
import org.tiqian.markdown.MarkdownTextRange
import org.tiqian.markdown.MarkdownTextSpan
import org.tiqian.markdown.MarkdownThematicBreak
import org.tiqian.markdown.MarkdownUnsupportedBlock

internal object PreviewDocuments {
    val fullArticle: MarkdownRenderDocument = PreviewDocumentBuilder().buildFullArticle()
    val headings: MarkdownRenderDocument = PreviewDocumentBuilder().buildHeadings()
    val inlineStyles: MarkdownRenderDocument = PreviewDocumentBuilder().buildInlineStyles()
    val quoteAndLists: MarkdownRenderDocument = PreviewDocumentBuilder().buildQuoteAndLists()
    val codeMathAndTable: MarkdownRenderDocument = PreviewDocumentBuilder().buildCodeMathAndTable()
    val images: MarkdownRenderDocument = PreviewDocumentBuilder().buildImages()
}

private class PreviewDocumentBuilder {
    private var nextKey = 0

    fun buildFullArticle(): MarkdownRenderDocument {
        val footnoteText = markedText(
            segment("段落中还可以放置脚注引用"),
            segment("[1]", MarkdownTextMark.Footnote("style-note", 1)),
            segment("，用于观察正文基线和较小字号之间的关系。"),
        )
        return MarkdownRenderDocument(
            listOf(
                heading(1, "中文 Markdown 正文样式总览"),
                paragraph(richIntroduction()),
                paragraph(
                    "这是一段连续的中文正文，用来检查字号、行高、段落宽度以及中西文混排。" +
                        "Tiqian Markdown 不应把正文做成一组彼此孤立的卡片，而应保持稳定、连贯的阅读节奏。",
                ),
                heading(2, "标题与正文的关系"),
                paragraph(
                    "标题前后的距离需要表达结构，而不是所有块统一使用同一个间距。标题换行以后，" +
                        "下一段正文仍应自然接续，并且行内公式的字号要跟随标题。",
                ),
                quoteBlock(),
                heading(2, "列表、代码与表格"),
                unorderedList(),
                taskList(),
                orderedList(),
                codeBlock(),
                paragraph(footnoteText),
                heading(3, "展示公式"),
                mathBlock(),
                table(),
                imageBlock(1),
                MarkdownHtmlBlock("<aside>HTML block preview</aside>", 6, metadata("html")),
                MarkdownUnsupportedBlock("preview-extension", "宿主扩展块的可读占位内容", metadata("unsupported")),
                thematicBreak(),
                footnote(),
            ),
        )
    }

    fun buildHeadings(): MarkdownRenderDocument = MarkdownRenderDocument(
        buildList {
            add(paragraph("这段正文放在标题之前，用来观察正文到标题的一整行间距。"))
            (1..6).forEach { level ->
                val title = if (level == 2) {
                    markedText(
                        segment("第二级标题含公式，并故意写得更长以观察标题换行 "),
                        segment("E=mc^2", MarkdownTextMark.InlineMath("E=mc^2")),
                    )
                } else {
                    MarkdownText("第 $level 级标题：中文与 Typography")
                }
                add(heading(level, title))
                add(paragraph("标题后的正文用于比较层级、字重、行高和标题后间距。"))
            }
            add(heading(2, "相邻标题的共同间距"))
            add(heading(3, "这个三级标题紧跟在二级标题之后"))
            add(paragraph("相邻标题之间保留一整行正文行高，三级标题之后以四分之一行距离接续正文。"))
        },
    )

    fun buildInlineStyles(): MarkdownRenderDocument = MarkdownRenderDocument(
        listOf(
            heading(2, "行内样式"),
            paragraph(richIntroduction()),
            paragraph(
                markedText(
                    segment("高亮范围之外｜"),
                    segment("中文 A B 与标点，保持连续", MarkdownTextMark.Highlight),
                    segment("｜高亮范围之外"),
                ),
            ),
            paragraph(
                markedText(
                    segment("同类相邻避让：高亮 "),
                    segment("甲", MarkdownTextMark.Highlight),
                    segment("乙", MarkdownTextMark.Highlight),
                    segment("；下划线 "),
                    segment("甲", MarkdownTextMark.Inserted),
                    segment("乙", MarkdownTextMark.Inserted),
                    segment("。分界均在甲、乙之间，当前总间隙 1 dp。"),
                ),
            ),
            paragraph(
                markedText(
                    segment("上标 x"),
                    segment("2", MarkdownTextMark.Superscript),
                    segment("、下标 H"),
                    segment("2", MarkdownTextMark.Subscript),
                    segment("O、插入内容 "),
                    segment("新增", MarkdownTextMark.Inserted),
                    segment("、虚线标记 "),
                    segment("范围", MarkdownTextMark.Custom("dashed-underline")),
                    segment("、删除内容 "),
                    segment("旧稿", MarkdownTextMark.Strikethrough),
                    segment("。"),
                ),
            ),
        ),
    )

    fun buildQuoteAndLists(): MarkdownRenderDocument = MarkdownRenderDocument(
        listOf(
            heading(2, "引用与列表"),
            quoteBlock(),
            unorderedList(),
            taskList(),
            orderedList(),
        ),
    )

    fun buildCodeMathAndTable(): MarkdownRenderDocument = MarkdownRenderDocument(
        listOf(
            heading(2, "代码、公式与表格"),
            codeBlock(),
            mathBlock(),
            table(),
            thematicBreak(),
        ),
    )

    fun buildImages(): MarkdownRenderDocument = MarkdownRenderDocument(
        listOf(
            heading(2, "图片与查看器"),
            paragraph("正文中的图片保持原始比例，小图不会被强制铺满；打开后可左右切换三张图片，并检查双击、拖动、缩放和返回行为。"),
            imageBlock(1, widthPixels = 1200, heightPixels = 800),
            imageBlock(2, widthPixels = 900, heightPixels = 1200),
            imageBlock(3, widthPixels = 800, heightPixels = 1800),
        ),
    )

    private fun richIntroduction(): MarkdownText = markedText(
        segment("同一段落里包含"),
        segment("粗体", MarkdownTextMark.Strong),
        segment("、"),
        segment("斜体", MarkdownTextMark.Emphasis),
        segment("、"),
        segment("链接", MarkdownTextMark.Link("https://example.com", "示例链接")),
        segment("、"),
        segment("inlineCode()", MarkdownTextMark.InlineCode),
        segment("、"),
        segment("高", MarkdownTextMark.Highlight),
        segment("亮", MarkdownTextMark.Highlight),
        segment("、键盘键 "),
        segment("⌘K", MarkdownTextMark.KeyboardInput),
        segment("、注音"),
        segment("汉字", MarkdownTextMark.Ruby("hàn zì")),
        segment("、缩写 "),
        segment("CLREQ", MarkdownTextMark.Abbreviation("Requirements for Chinese Text Layout")),
        segment("，以及行内公式 "),
        segment("E=mc^2", MarkdownTextMark.InlineMath("E=mc^2")),
        segment("。"),
    )

    private fun quoteBlock() = MarkdownBlockQuote(
        blocks = listOf(
            paragraph("引用不是提示卡片，而是正文中的另一层声音。它需要和正文有所区别，但不应抢走内容本身的注意力。"),
            paragraph(markedText(segment("引用内部也可能有"), segment("强调", MarkdownTextMark.Strong), segment("和链接。"))),
        ),
        metadata = metadata("blockquote"),
    )

    private fun unorderedList() = MarkdownList(
        ordered = false,
        startNumber = 1,
        tight = true,
        items = listOf(
            listItem("无序列表的第一项，用较长文字检查悬挂缩进和换行后的正文起点。"),
            listItem("第二项确认同类圆点标记仍共用一格正文起点。"),
        ),
        metadata = metadata("unordered-list"),
    )

    private fun taskList() = MarkdownList(
        ordered = false,
        startNumber = 1,
        tight = true,
        items = listOf(
            listItem("已完成的任务项。", MarkdownTaskState.Checked),
            listItem("尚未完成的任务项。", MarkdownTaskState.Unchecked),
        ),
        metadata = metadata("task-list"),
    )

    private fun orderedList() = MarkdownList(
        ordered = true,
        startNumber = 9,
        tight = false,
        items = listOf(
            listItem("第九项用于观察一位数序号。"),
            listItem("第十项用于观察序号变宽以后，正文是否仍然对齐。"),
        ),
        metadata = metadata("ordered-list"),
    )

    private fun listItem(text: String, task: MarkdownTaskState? = null) = MarkdownListItem(
        blocks = listOf(paragraph(text)),
        task = task,
        metadata = metadata("list-item"),
    )

    private fun codeBlock(): MarkdownCodeBlock {
        val code = """fun render(document: MarkdownRenderDocument) {
    TiqianMarkdown(document)
}"""
        return MarkdownCodeBlock(
            code = code,
            language = "Kotlin",
            info = "kotlin",
            metadata = metadata("code"),
            fileName = "ArticleRenderer.kt",
        )
    }

    private fun mathBlock() = MarkdownMathBlock(
        expression = "\\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}=x",
        metadata = metadata("math"),
    )

    private fun table(): MarkdownTable {
        val alignments = listOf(
            MarkdownTableAlignment.Start,
            MarkdownTableAlignment.Center,
            MarkdownTableAlignment.End,
        )
        fun row(header: Boolean, vararg values: MarkdownText) = MarkdownTableRow(
            cells = values.mapIndexed { index, value ->
                MarkdownTableCell(
                    text = value,
                    alignment = alignments[index],
                    header = header,
                    metadata = metadata("table-cell"),
                )
            },
            header = header,
            metadata = metadata("table-row"),
        )
        return MarkdownTable(
            columnAlignments = alignments,
            rows = listOf(
                row(true, MarkdownText("项目"), MarkdownText("状态"), MarkdownText("数值")),
                row(false, MarkdownText("中文正文"), MarkdownText("正常"), MarkdownText("100%")),
                row(
                    false,
                    MarkdownText("Inline math"),
                    MarkdownText("检查"),
                    markedText(segment("E=mc^2", MarkdownTextMark.InlineMath("E=mc^2"))),
                ),
            ),
            metadata = metadata("table"),
            caption = MarkdownText("表 1　正文渲染能力检查"),
        )
    }

    private fun imageBlock(
        index: Int,
        widthPixels: Int = 1200,
        heightPixels: Int = 800,
    ) = MarkdownImageBlock(
        destination = "https://example.com/image-$index.png",
        description = "可点击打开、缩放和切换的示例图片 $index",
        title = "示例图片 $index",
        widthPixels = widthPixels,
        heightPixels = heightPixels,
        metadata = metadata("image"),
        caption = MarkdownText("图 $index　宿主提供图片内容，正文渲染器负责尺寸、图注与查看器。"),
    )

    private fun footnote() = MarkdownFootnoteDefinition(
        label = "style-note",
        index = 1,
        blocks = listOf(paragraph("脚注正文需要保持可读，但在视觉上弱于主文。")),
        metadata = metadata("footnote"),
    )

    private fun heading(level: Int, value: String) = heading(level, MarkdownText(value))

    private fun heading(level: Int, value: MarkdownText) = MarkdownHeading(
        level = level,
        id = "preview-heading-$nextKey",
        text = value,
        metadata = metadata("heading-$level"),
    )

    private fun paragraph(value: String) = paragraph(MarkdownText(value))

    private fun paragraph(value: MarkdownText) = MarkdownParagraph(value, metadata("paragraph"))

    private fun thematicBreak() = MarkdownThematicBreak(metadata("thematic-break"))

    private fun metadata(source: String): MarkdownNodeMetadata {
        val key = nextKey++
        return MarkdownNodeMetadata(
            key = MarkdownNodeKey(key, listOf(key)),
            sourceSpan = MarkdownSourceSpan(key, key + source.length, key, 0, key, source.length),
            sourceMarkdown = source,
        )
    }
}

private data class PreviewSegment(
    val value: String,
    val marks: List<MarkdownTextMark>,
)

private fun segment(value: String, vararg marks: MarkdownTextMark) = PreviewSegment(value, marks.toList())

private fun markedText(vararg segments: PreviewSegment): MarkdownText {
    val value = buildString { segments.forEach { append(it.value) } }
    var offset = 0
    val spans = buildList {
        segments.forEach { segment ->
            val range = MarkdownTextRange(offset, offset + segment.value.length)
            segment.marks.forEach { mark -> add(MarkdownTextSpan(range, mark)) }
            offset = range.endExclusive
        }
    }
    return MarkdownText(value, spans)
}
