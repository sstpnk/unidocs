package tech.stpnk.unidocs.feature.office

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** JVM-тест ODT→HTML: собираем минимальный odt-контейнер и конвертируем. */
class OdtToHtmlTest {

    private fun sampleOdt(): ByteArray {
        val contentXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content
                xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
                xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
                xmlns:xlink="http://www.w3.org/1999/xlink">
              <office:body>
                <office:text>
                  <text:h text:outline-level="1">Заголовок</text:h>
                  <text:p>Абзац с &lt;экранированием&gt; и <text:span>спаном</text:span>.</text:p>
                  <text:list>
                    <text:list-item><text:p>Пункт один</text:p></text:list-item>
                    <text:list-item><text:p>Пункт два</text:p></text:list-item>
                  </text:list>
                  <table:table>
                    <table:table-row>
                      <table:table-cell><text:p>Ячейка 1</text:p></table:table-cell>
                      <table:table-cell><text:p>Ячейка 2</text:p></table:table-cell>
                    </table:table-row>
                  </table:table>
                  <text:p>Ссылка: <text:a xlink:href="https://example.com">пример</text:a></text:p>
                </office:text>
              </office:body>
            </office:document-content>
        """.trimIndent()

        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/vnd.oasis.opendocument.text".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("content.xml"))
            zip.write(contentXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    @Test
    fun `converts headings paragraphs lists tables and links`() {
        val html = OdtToHtml.convertStream(ByteArrayInputStream(sampleOdt()))
        assertTrue("нет заголовка", html.contains("<h1>Заголовок</h1>"))
        assertTrue("нет абзаца", html.contains("<p>Абзац с &lt;экранированием&gt; и <span>спаном</span>.</p>"))
        assertTrue("нет списка", html.contains("<ul>") && html.contains("<li>"))
        assertTrue("нет пункта списка", html.contains("Пункт один"))
        assertTrue("нет таблицы", html.contains("<table>") && html.contains("Ячейка 1"))
        assertTrue("нет ссылки", html.contains("href=\"https://example.com\""))
        assertTrue("нет стилей", html.contains("<style>"))
    }

    @Test
    fun `content without acroform-like body fails clearly`() {
        val bytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zip ->
                zip.putNextEntry(ZipEntry("mimetype"))
                zip.write("application/zip".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        val result = runCatching { OdtToHtml.convertStream(ByteArrayInputStream(bytes)) }
        assertTrue(result.isFailure)
    }
}
