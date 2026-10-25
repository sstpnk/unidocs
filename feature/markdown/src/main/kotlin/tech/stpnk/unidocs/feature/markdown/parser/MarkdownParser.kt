package tech.stpnk.unidocs.feature.markdown.parser

import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import tech.stpnk.unidocs.core.model.OutlineNode

/**
 * Markdown → List<MdBlock>. Расширения: GFM-таблицы, зачёркивание, списки задач.
 */
object MarkdownParser {

    private val parser: Parser = Parser.builder()
        .extensions(
            listOf(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListItemsExtension.create(),
            ),
        )
        .build()

    fun parse(markdown: String): List<MdBlock> {
        val document = parser.parse(markdown)
        return childrenOf(document).mapNotNull { toBlock(it) }
    }

    private fun childrenOf(node: Node): List<Node> = buildList {
        var child = node.firstChild
        while (child != null) {
            add(child)
            child = child.next
        }
    }

    /** Оглавление по заголовкам: target — индекс блока в списке. */
    fun outline(blocks: List<MdBlock>): List<OutlineNode> =
        blocks.mapIndexedNotNull { index, block ->
            (block as? MdBlock.Heading)?.let {
                OutlineNode(
                    title = it.spans.joinToString("") { span -> span.text },
                    level = it.level,
                    target = index,
                )
            }
        }

    private fun toBlock(node: Node): MdBlock? = when (node) {
        is Heading -> MdBlock.Heading(node.level, inlineSpans(node))
        is Paragraph -> MdBlock.Paragraph(inlineSpans(node))
        is FencedCodeBlock -> MdBlock.Code(
            code = node.literal.orEmpty(),
            language = node.info?.trim()?.takeIf { it.isNotEmpty() },
        )
        is IndentedCodeBlock -> MdBlock.Code(node.literal.orEmpty(), null)
        is BlockQuote -> MdBlock.Quote(childrenOf(node).mapNotNull { toBlock(it) })
        is BulletList -> MdBlock.ListBlock(false, 1, childrenOf(node).filterIsInstance<ListItem>().map { toItem(it) })
        is OrderedList -> MdBlock.ListBlock(true, node.startNumber, childrenOf(node).filterIsInstance<ListItem>().map { toItem(it) })
        is ThematicBreak -> MdBlock.Rule
        is TableBlock -> toTable(node)
        is HtmlBlock -> MdBlock.Code(node.literal.orEmpty(), "html")
        else -> null
    }

    private fun toItem(node: ListItem): MdBlock.ListBlock.Item {
        var checked: Boolean? = null
        val children = childrenOf(node).toMutableList()

        // GFM task item: маркер — прямой дочерний узел ListItem перед Paragraph.
        if (children.firstOrNull() is TaskListItemMarker) {
            checked = (children.removeAt(0) as TaskListItemMarker).isChecked
        }

        val first = children.firstOrNull()
        val spans: List<MdSpan> = if (first is Paragraph) inlineSpans(first) else emptyList()

        val nested = children.drop(1).mapNotNull { toBlock(it) }
        return MdBlock.ListBlock.Item(spans, checked, nested)
    }

    private fun toTable(node: TableBlock): MdBlock.Table {
        val header = mutableListOf<List<MdSpan>>()
        val rows = mutableListOf<List<List<MdSpan>>>()

        var section = node.firstChild
        while (section != null) {
            when (section) {
                is TableHead -> childrenOf(section).filterIsInstance<TableRow>().forEach { row -> header += cells(row) }
                is TableBody -> childrenOf(section).filterIsInstance<TableRow>().forEach { row -> rows += cells(row) }
            }
            section = section.next
        }
        return MdBlock.Table(header, rows)
    }

    private fun cells(row: TableRow): List<List<MdSpan>> =
        childrenOf(row).filterIsInstance<TableCell>().map { inlineSpans(it) }

    private fun inlineSpans(node: Node): List<MdSpan> {
        val out = mutableListOf<MdSpan>()
        collectInline(node, emptySet(), null, out)
        return mergeAdjacent(out)
    }

    private fun collectInline(node: Node, styles: Set<MdStyle>, linkUrl: String?, out: MutableList<MdSpan>) {
        var child = node.firstChild
        while (child != null) {
            when (child) {
                is Text -> out += MdSpan(child.literal, styles, linkUrl)
                is Code -> out += MdSpan(child.literal, styles + MdStyle.INLINE_CODE, linkUrl)
                is StrongEmphasis -> collectInline(child, styles + MdStyle.BOLD, linkUrl, out)
                is Emphasis -> collectInline(child, styles + MdStyle.ITALIC, linkUrl, out)
                is Strikethrough -> collectInline(child, styles + MdStyle.STRIKETHROUGH, linkUrl, out)
                is Link -> collectInline(child, styles + MdStyle.LINK, child.destination, out)
                is Image -> out += MdSpan(
                    text = "[🖼 ${altText(child).ifBlank { "изображение" }}]",
                    styles = styles,
                    linkUrl = linkUrl,
                )
                is SoftLineBreak, is HardLineBreak -> out += MdSpan("\n", styles, linkUrl)
                else -> collectInline(child, styles, linkUrl, out)
            }
            child = child.next
        }
    }

    private fun altText(image: Image): String = buildString {
        var child = image.firstChild
        while (child != null) {
            if (child is Text) append(child.literal)
            child = child.next
        }
    }

    private fun mergeAdjacent(spans: List<MdSpan>): List<MdSpan> {
        if (spans.isEmpty()) return spans
        val result = mutableListOf<MdSpan>()
        for (span in spans) {
            val last = result.lastOrNull()
            if (last != null && last.styles == span.styles && last.linkUrl == span.linkUrl) {
                result[result.lastIndex] = last.copy(text = last.text + span.text)
            } else {
                result += span
            }
        }
        return result
    }
}
