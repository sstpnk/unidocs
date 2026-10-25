package tech.stpnk.unidocs.feature.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.stpnk.unidocs.core.model.ByteSequence

class PdfMatcherTest {

    private val pdfHead = ByteSequence("%PDF-1.7\n1 0 obj".toByteArray())

    @Test
    fun `mime application pdf matches`() {
        assertTrue(PdfMatcher.supports("application/pdf", "anything", ByteSequence(ByteArray(0))))
    }

    @Test
    fun `extension pdf matches without mime`() {
        assertTrue(PdfMatcher.supports(null, "Документ.PDF", ByteSequence(ByteArray(0))))
    }

    @Test
    fun `magic header matches when mime and name are useless`() {
        assertTrue(PdfMatcher.supports(null, "document.bin", pdfHead))
    }

    @Test
    fun `plain text file does not match`() {
        val head = ByteSequence("Привет, это текстовый файл".toByteArray())
        assertFalse(PdfMatcher.supports("text/plain", "notes.txt", head))
    }

    @Test
    fun `empty head with unknown name does not match`() {
        assertFalse(PdfMatcher.supports(null, "file.zip", ByteSequence(ByteArray(0))))
    }
}
