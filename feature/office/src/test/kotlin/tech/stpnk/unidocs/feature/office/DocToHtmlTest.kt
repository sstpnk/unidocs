package tech.stpnk.unidocs.feature.office

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocToHtmlTest {

    @Test
    fun `sample doc extracts text from both pieces`() {
        val bytes = javaClass.getResourceAsStream("/sample.doc")
            ?.readBytes() ?: error("sample.doc не найден в test resources")
        val html = DocToHtml.convert(bytes)
        assertTrue("нет cp1251-куска", html.contains("Привет из тестового документа"))
        assertTrue("нет utf-16-куска", html.contains("Хвост в шестнадцати битах"))
        assertFalse("нет кракозябр", html.contains("????"))
    }

    @Test
    fun `two paragraphs from carriage returns`() {
        val bytes = javaClass.getResourceAsStream("/sample.doc")
            ?.readBytes() ?: error("sample.doc не найден")
        val html = DocToHtml.convert(bytes)
        val pCount = "<p>".toRegex().findAll(html).count()
        assertTrue("должно быть 2 абзаца, было $pCount", pCount == 2)
    }

    @Test
    fun `non-doc input fails clearly`() {
        val bytes = ByteArray(600) { 0x41 }
        try {
            DocToHtml.convert(bytes)
            error("должно было упасть")
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.contains("OLE") || e.message!!.contains("Word"))
        }
    }
}
