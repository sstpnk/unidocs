package tech.stpnk.unidocs.feature.office

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

/** RTF → HTML: кодировка cp1251, форматирование, unicode, пропуск групп-приёмников. */
class RtfToHtmlTest {

    private val charset = Charset.forName("windows-1251")

    private fun rtf(body: String): ByteArray {
        val header = "{\\rtf1\\ansi\\ansicpg1251\\deff0\n"
        val footer = "\n}"
        return (header + body + footer).toByteArray(charset)
    }

    @Test
    fun `bold and plain text with cyrillic`() {
        // {\b Жирный} текст \par Кириллица: КИР
        val body = "{\\b \\'c6\\'e8\\'f0\\'ed\\'fb\\'e9} текст \\par \\'ca\\'c8\\'d0"
        val html = RtfToHtml.convert(rtf(body))
        println("RTF DEBUG BOLD: " + html)
        assertTrue("нет bold-спана", html.contains("font-weight:bold"))
        assertTrue("нет слова «Жирный»", html.contains("Жирный"))
        assertTrue("нет абзаца", html.contains("<p>"))
        assertTrue("нет «текст»", html.contains("текст"))
    }

    @Test
    fun `cyrillic bytes decode via ansicpg`() {
        val body = "\\'ca\\'c8\\'d0\\'c8\\'cb\\'cb\\'c8\\'d6\\'c0"
        val html = RtfToHtml.convert(rtf(body))
        val bodyText = html.substringAfter("<p>").substringBefore("</p>")
        println("CODEPOINTS: " + bodyText.map { Integer.toHexString(it.code) }.joinToString(" "))
        println("TEXT: " + bodyText)
        assertTrue("кириллица не декодирована: $html", bodyText == "КИРИЛЛИЦА")
    }

    @Test
    fun `italic underline and font size`() {
        val body = "{\\i курсив} {\\ul подчерк} {\\fs40 крупный}"
        val html = RtfToHtml.convert(rtf(body))
        assertTrue("нет курсива", html.contains("font-style:italic"))
        assertTrue("нет подчёркивания", html.contains("text-decoration:underline"))
        assertTrue("нет размера", html.contains("font-size:20.0pt"))
    }

    @Test
    fun `unicode escape decodes and skips fallback`() {
        // \u1055? — юникод «П», затем один fallback-байт '?', который должен быть пропущен
        val body = "\\u1055?текст"
        val html = RtfToHtml.convert(rtf(body))
        assertTrue("нет «П»", html.contains("П"))
        assertFalse("fallback '?' должен быть пропущен", html.contains("?текст"))
    }

    @Test
    fun `destination groups are skipped`() {
        val body = "{\\*\\generator Riched20}visible text{\\*\\themedata x}"
        val html = RtfToHtml.convert(rtf(body))
        assertTrue("visible text потерян", html.contains("visible text"))
        assertFalse("generator попал в вывод", html.contains("Riched20"))
        assertFalse("themedata попал в вывод", html.contains("themedata"))
    }

    @Test
    fun `non-rtf input fails clearly`() {
        val result = runCatching { RtfToHtml.convert("не rtf".toByteArray()) }
        assertTrue(result.isFailure)
    }

    @Test
    fun `fonttbl and colortbl without star are skipped`() {
        val body = "{\\fonttbl{\\f0 Arial;}}{\\colortbl;\\red255\\green0\\blue0;}текст после служебных таблиц"
        val html = RtfToHtml.convert(rtf(body))
        assertTrue("текст потерян", html.contains("текст после служебных таблиц"))
        assertFalse("fonttbl попал в вывод", html.contains("Arial"))
        assertFalse("colortbl попал в вывод", html.contains("red255"))
    }

}
