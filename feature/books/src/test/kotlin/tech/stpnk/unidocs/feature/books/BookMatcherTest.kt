package tech.stpnk.unidocs.feature.books

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.stpnk.unidocs.core.model.ByteSequence

class BookMatcherTest {

    private fun mobiHead(): ByteSequence {
        val bytes = ByteArray(80)
        "BOOKMOBI".toByteArray().copyInto(bytes, 60)
        return ByteSequence(bytes)
    }

    @Test
    fun `epub mime matches`() {
        assertTrue(BookMatcher.supports("application/epub+zip", "any-name", ByteSequence(ByteArray(0))))
    }

    @Test
    fun `fb2 extension matches without mime`() {
        assertTrue(BookMatcher.supports(null, "Война и мир.fb2", ByteSequence(ByteArray(0))))
    }

    @Test
    fun `mobi magic matches even with useless name and mime`() {
        assertTrue(BookMatcher.supports(null, "download.bin", mobiHead()))
    }

    @Test
    fun `cbr extension matches`() {
        assertTrue(BookMatcher.supports("application/x-cbr", "comic", ByteSequence(ByteArray(0))))
    }

    @Test
    fun `plain zip without book extension is not a book`() {
        assertFalse(BookMatcher.supports("application/zip", "archive.zip", ByteSequence(ByteArray(0))))
    }

    @Test
    fun `plain text is not a book`() {
        assertFalse(BookMatcher.supports("text/plain", "notes.txt", ByteSequence("привет".toByteArray())))
    }
}
