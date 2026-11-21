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
    fun `content without body fails clearly`() {
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

    @Test
    fun `table colspan and embedded image render`() {
        // 1×1 прозрачный PNG (канонический минимальный файл)
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06,
            0x00, 0x00, 0x00, 0x1F, 0x15.toByte(), 0xC4.toByte(), 0x89.toByte(), 0x00, 0x00, 0x00,
            0x0D, 0x49, 0x44, 0x41, 0x54, 0x78, 0x9C.toByte(), 0x63.toByte(), 0x00, 0x01, 0x00,
            0x00, 0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
        )
        val contentXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content
                xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
                xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
                xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
                xmlns:xlink="http://www.w3.org/1999/xlink">
              <office:body><office:text>
                <table:table>
                  <table:table-row>
                    <table:table-cell table:number-columns-spanned="2"><text:p>Объединена</text:p></table:table-cell>
                  </table:table-row>
                </table:table>
                <text:p><draw:image xlink:href="Pictures/img.png"/></text:p>
              </office:text></office:body>
            </office:document-content>
        """.trimIndent()

        val bytes = ByteArrayOutputStream().use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("content.xml"))
                zip.write(contentXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("Pictures/img.png"))
                zip.write(png)
                zip.closeEntry()
            }
            out.toByteArray()
        }

        val html = OdtToHtml.convertStream(ByteArrayInputStream(bytes))
        assertTrue("нет colspan", html.contains("colspan=\"2\""))
        assertTrue("нет встроенной картинки", html.contains("data:image/png;base64,"))
    }

    @Test
    fun `odt text styles apply bold and alignment`() {
        val contentXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content
                xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
                xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
              <office:automatic-styles>
                <style:style style:name="T1" style:family="text">
                  <style:text-properties fo:font-weight="bold" fo:color="#c00000"/>
                </style:style>
                <style:style style:name="P2" style:family="paragraph" style:parent-style-name="Standard">
                  <style:paragraph-properties fo:text-align="center"/>
                </style:style>
              </office:automatic-styles>
              <office:body><office:text>
                <text:p text:style-name="P2">Центр с <text:span text:style-name="T1">важным</text:span></text:p>
              </office:text></office:body>
            </office:document-content>
        """.trimIndent()
        val bytes = ByteArrayOutputStream().use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("content.xml"))
                zip.write(contentXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            out.toByteArray()
        }
        val html = OdtToHtml.convertStream(ByteArrayInputStream(bytes))
        assertTrue("нет bold", html.contains("font-weight:bold"))
        assertTrue("нет цвета", html.contains("#c00000"))
        assertTrue("нет выравнивания", html.contains("text-align:center"))
    }
}
