package tech.stpnk.unidocs.feature.office

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.stpnk.unidocs.core.model.ByteSequence

class OfficeMatcherTest {

    @Test
    fun `docx by mime and extension`() {
        assertEquals(
            "docx",
            OfficeMatcher.typeFor(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "file",
                ByteSequence(ByteArray(0)),
            ),
        )
        assertEquals("docx", OfficeMatcher.typeFor(null, "Договор.docx", ByteSequence(ByteArray(0))))
    }

    @Test
    fun `xlsx and xlsm map to xlsx renderer`() {
        assertEquals(
            "xlsx",
            OfficeMatcher.typeFor(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "file",
                ByteSequence(ByteArray(0)),
            ),
        )
        assertEquals("xlsx", OfficeMatcher.typeFor(null, "бюджет.xlsm", ByteSequence(ByteArray(0))))
    }

    @Test
    fun `odt and ods by extension`() {
        assertEquals("odt", OfficeMatcher.typeFor(null, "заявление.odt", ByteSequence(ByteArray(0))))
        assertEquals("odt", OfficeMatcher.typeFor(null, "таблица.ods", ByteSequence(ByteArray(0))))
    }

    @Test
    fun `pptx by mime`() {
        assertEquals(
            "pptx",
            OfficeMatcher.typeFor(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "презентация",
                ByteSequence(ByteArray(0)),
            ),
        )
    }

    @Test
    fun `xls legacy routes to xlsx renderer`() {
        val head = ByteSequence(ByteArray(0))
        assertEquals("xlsx", OfficeMatcher.typeFor(null, "таблица.xls", head))
        assertEquals("xlsx", OfficeMatcher.typeFor("application/vnd.ms-excel", "таблица", head))
    }

    @Test
    fun `rtf routes to rtf converter`() {
        val head = ByteSequence(ByteArray(0))
        assertEquals("rtf", OfficeMatcher.typeFor(null, "записка.rtf", head))
        assertEquals("rtf", OfficeMatcher.typeFor("application/rtf", "записка", head))
    }

    @Test
    fun `odf family routes to own converter`() {
        val head = ByteSequence(ByteArray(4) { 0 })
        assertEquals("odt", OfficeMatcher.typeFor(null, "презентация.odp", head))
        assertEquals("odt", OfficeMatcher.typeFor(null, "чертёж.odg", head))
        assertEquals("odt", OfficeMatcher.typeFor(null, "таблица.ods", head))
        assertEquals("odt", OfficeMatcher.typeFor(null, "документ.fodt", head))
        assertEquals("odt", OfficeMatcher.typeFor(null, "таблица.fods", head))
        assertEquals("odt", OfficeMatcher.typeFor(null, "слайды.fodp", head))
        assertEquals("odt", OfficeMatcher.typeFor("application/vnd.oasis.opendocument.presentation", "файл", head))
        assertEquals("odt", OfficeMatcher.typeFor("application/vnd.oasis.opendocument.graphics", "файл", head))
        assertEquals("odt", OfficeMatcher.typeFor("application/vnd.oasis.opendocument.text-flat-xml", "файл", head))
        assertEquals("odt", OfficeMatcher.typeFor(null, "письмо.xodt", head))
        assertEquals("odt", OfficeMatcher.typeFor(null, "таблица.xods", head))
        assertEquals("odt", OfficeMatcher.typeFor(null, "слайды.xodp", head))
    }

    @Test
    fun `legacy doc routes to own converter`() {
        val head = ByteSequence(ByteArray(4) { 0 })
        assertEquals("doc", OfficeMatcher.typeFor(null, "старый.doc", head))
        assertEquals("doc", OfficeMatcher.typeFor("application/msword", "файл", head))
        assertTrue(OfficeMatcher.supports(null, "старый.doc", head))
        assertFalse(OfficeMatcher.supports(null, "нечто.xyz", head))
    }

    @Test
    fun `unsupported types route nowhere`() {
        val head = ByteSequence(ByteArray(0))
        assertNull(OfficeMatcher.typeFor(null, "archive.zip", head))
        assertNull(OfficeMatcher.typeFor("application/zip", "файл", head))
    }

    @Test
    fun `interchange and archive preview formats route to a renderer`() {
        val head = ByteSequence(ByteArray(0))
        assertEquals("html", OfficeMatcher.typeFor(null, "страница.html", head))
        assertEquals("mhtml", OfficeMatcher.typeFor(null, "архив.mht", head))
        assertEquals("eml", OfficeMatcher.typeFor(null, "письмо.eml", head))
        assertEquals("ics", OfficeMatcher.typeFor(null, "календарь.ics", head))
        assertEquals("csv", OfficeMatcher.typeFor(null, "данные.csv", head))
        assertEquals("xmind", OfficeMatcher.typeFor(null, "карта.xmind", head))
        assertEquals("xmind", OfficeMatcher.typeFor(null, "доска.xboard", head))
        assertEquals("iwork", OfficeMatcher.typeFor(null, "лист.numbers", head))
        assertEquals("iwork", OfficeMatcher.typeFor(null, "документ.pages", head))
        assertEquals("iwork", OfficeMatcher.typeFor(null, "слайды.key", head))
        assertEquals("ppt", OfficeMatcher.typeFor(null, "архив.ppt", head))
    }
}
