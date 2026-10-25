package tech.stpnk.unidocs.feature.markdown.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `heading with inline formatting`() {
        val blocks = MarkdownParser.parse("# Заголовок\n\nЖирный **текст** и *курсив*")
        val heading = blocks[0] as MdBlock.Heading
        assertEquals(1, heading.level)
        assertEquals("Заголовок", heading.spans.joinToString("") { it.text })

        val paragraph = blocks[1] as MdBlock.Paragraph
        val bold = paragraph.spans.first { MdStyle.BOLD in it.styles }
        assertEquals("текст", bold.text)
        val italic = paragraph.spans.first { MdStyle.ITALIC in it.styles }
        assertEquals("курсив", italic.text)
    }

    @Test
    fun `fenced code block keeps language`() {
        val blocks = MarkdownParser.parse("```kotlin\nval x = 1\n```")
        val code = blocks.single() as MdBlock.Code
        assertEquals("kotlin", code.language)
        assertTrue(code.code.contains("val x = 1"))
    }

    @Test
    fun `task list items carry checked state`() {
        val blocks = MarkdownParser.parse("- [x] сделано\n- [ ] не сделано")
        val list = blocks.single() as MdBlock.ListBlock
        assertEquals(2, list.items.size)
        assertEquals(true, list.items[0].checked)
        assertEquals(false, list.items[1].checked)
        assertEquals("сделано", list.items[0].spans.joinToString("") { it.text })
    }

    @Test
    fun `ordered list keeps start number`() {
        val blocks = MarkdownParser.parse("3. три\n4. четыре")
        val list = blocks.single() as MdBlock.ListBlock
        assertTrue(list.ordered)
        assertEquals(3, list.startNumber)
        assertEquals(2, list.items.size)
    }

    @Test
    fun `gfm table parses header and rows`() {
        val blocks = MarkdownParser.parse("| a | b |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |")
        val table = blocks.single() as MdBlock.Table
        assertEquals(2, table.header.size)
        assertEquals("a", table.header[0].joinToString("") { it.text })
        assertEquals(2, table.rows.size)
        assertEquals("4", table.rows[1][1].joinToString("") { it.text })
    }

    @Test
    fun `strikethrough span`() {
        val blocks = MarkdownParser.parse("~~удалено~~")
        val paragraph = blocks.single() as MdBlock.Paragraph
        assertTrue(paragraph.spans.any { MdStyle.STRIKETHROUGH in it.styles })
    }

    @Test
    fun `block quote nests blocks`() {
        val blocks = MarkdownParser.parse("> цитата\n> **важно**")
        val quote = blocks.single() as MdBlock.Quote
        assertTrue(quote.blocks.isNotEmpty())
        val text = quote.blocks.filterIsInstance<MdBlock.Paragraph>()
            .flatMap { it.spans }
            .joinToString("") { it.text }
        assertTrue(text.contains("цитата"))
    }

    @Test
    fun `link keeps destination`() {
        val blocks = MarkdownParser.parse("[сайт](https://example.com)")
        val paragraph = blocks.single() as MdBlock.Paragraph
        val link = paragraph.spans.first { MdStyle.LINK in it.styles }
        assertEquals("сайт", link.text)
        assertEquals("https://example.com", link.linkUrl)
    }

    @Test
    fun `inline code and nested bold-italic`() {
        val blocks = MarkdownParser.parse("Код `x()` и ***оба***")
        val paragraph = blocks.single() as MdBlock.Paragraph
        assertTrue(paragraph.spans.any { MdStyle.INLINE_CODE in it.styles && it.text == "x()" })
        val both = paragraph.spans.first { it.text == "оба" }
        assertTrue(MdStyle.BOLD in both.styles)
        assertTrue(MdStyle.ITALIC in both.styles)
    }

    @Test
    fun `outline extracts headings with block targets`() {
        val blocks = MarkdownParser.parse("# Первый\n\nтекст\n\n## Второй")
        val outline = MarkdownParser.outline(blocks)
        assertEquals(2, outline.size)
        assertEquals("Первый", outline[0].title)
        assertEquals(1, outline[0].level)
        assertEquals(2, outline[1].level)
        assertEquals(2, outline[1].target)
    }

    @Test
    fun `plain paragraph without formatting`() {
        val blocks = MarkdownParser.parse("Просто текст без разметки.")
        val paragraph = blocks.single() as MdBlock.Paragraph
        assertEquals(1, paragraph.spans.size)
        assertNull(paragraph.spans[0].linkUrl)
        assertTrue(paragraph.spans[0].styles.isEmpty())
    }

    @Test
    fun `horizontal rule and empty document`() {
        assertTrue(MarkdownParser.parse("---").single() is MdBlock.Rule)
        assertTrue(MarkdownParser.parse("").isEmpty())
        assertNotNull(MarkdownParser.parse("# H").single())
    }
}
