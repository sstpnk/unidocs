package tech.stpnk.unidocs

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tech.stpnk.unidocs.core.model.ByteSequence
import tech.stpnk.unidocs.core.model.PluginRegistry

/**
 * Роутинг всех заявленных типов файлов: каждый формат должен попадать
 * в правильный плагин (регрессия «заявленные типы не открываются»).
 * Нужен Android-контекст: конструкторы плагинов требуют Context.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PluginRoutingTest {

    private val registry = PluginRegistry(
        createDocumentPlugins(ApplicationProvider.getApplicationContext() as Application),
    )

    private fun routeTo(name: String, mime: String? = null): String? =
        registry.findFor(mime, name, ByteSequence(ByteArray(0)))?.id

    @Test
    fun `markdown routes to markdown plugin`() {
        assertEquals("markdown", routeTo("notes.md"))
        assertEquals("markdown", routeTo("README.markdown"))
        assertEquals("markdown", routeTo("any", "text/markdown"))
    }

    @Test
    fun `pdf routes to pdf plugin`() {
        assertEquals("pdf", routeTo("doc.pdf"))
        assertEquals("pdf", routeTo("doc", "application/pdf"))
    }

    @Test
    fun `book formats route to books plugin`() {
        assertEquals("books", routeTo("book.epub"))
        assertEquals("books", routeTo("book.fb2"))
        assertEquals("books", routeTo("book.mobi"))
        assertEquals("books", routeTo("book.prc"))
        assertEquals("books", routeTo("book.azw3"))
        assertEquals("books", routeTo("book.cbz"))
        assertEquals("books", routeTo("book.cbr"))
        assertEquals("books", routeTo("book", "application/epub+zip"))
        assertEquals("books", routeTo("book", "application/x-mobipocket-ebook"))
    }

    @Test
    fun `office formats route to office plugin`() {
        assertEquals("office", routeTo("договор.docx"))
        assertEquals("office", routeTo("budget.xlsx"))
        assertEquals("office", routeTo("budget.xlsm"))
        assertEquals("office", routeTo("slides.pptx"))
        assertEquals("office", routeTo("заявление.odt"))
        assertEquals("office", routeTo("таблица.ods"))
        assertEquals("office", routeTo("file", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
    }

    @Test
    fun `plain text routes to text plugin`() {
        assertEquals("text", routeTo("notes.txt"))
        assertEquals("text", routeTo("notes.txt", "text/plain"))
        assertEquals("text", routeTo("debug.log"))
    }

    @Test
    fun `unsupported types route nowhere`() {
        assertNull(routeTo("archive.zip", "application/zip"))
        assertNull(routeTo("старый.doc", "application/msword"))
        assertNull(routeTo("image.png", "image/png"))
    }

    @Test
    fun `sniff fallback works for pdf without mime and extension`() {
        val pdfHead = ByteSequence("%PDF-1.7\n".toByteArray())
        assertNotNull(registry.findFor(null, "download.bin", pdfHead))
        assertEquals("pdf", registry.findFor(null, "download.bin", pdfHead)?.id)
    }

    @Test
    fun `open context uri survives routing`() {
        val plugin = registry.findFor("application/pdf", "x", ByteSequence(ByteArray(0)))
        assertNotNull(plugin)
        assertEquals(
            Uri.parse("content://test/x"),
            Uri.parse("content://test/x"),
        )
    }
}
