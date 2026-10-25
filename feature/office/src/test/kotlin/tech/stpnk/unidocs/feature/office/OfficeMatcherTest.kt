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
        assertEquals("xlsx", OfficeMatcher.typeFor(null, "таблица.ods", ByteSequence(ByteArray(0))))
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
    fun `legacy doc is not supported`() {
        assertNull(OfficeMatcher.typeFor("application/msword", "старый.doc", ByteSequence(ByteArray(0))))
        assertFalse(OfficeMatcher.supports("application/msword", "старый.doc", ByteSequence(ByteArray(0))))
    }

    @Test
    fun `random file is not office`() {
        assertFalse(OfficeMatcher.supports("text/plain", "notes.txt", ByteSequence("привет".toByteArray())))
        assertTrue(OfficeMatcher.supports(null, "report.xlsx", ByteSequence(ByteArray(0))))
    }
}
