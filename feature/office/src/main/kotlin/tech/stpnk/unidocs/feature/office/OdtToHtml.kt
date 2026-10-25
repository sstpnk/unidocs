package tech.stpnk.unidocs.feature.office

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Лёгкий конвертер ODT → HTML (свой, без тяжёлых движков):
 * читает content.xml из zip, отдаёт HTML с абзацами, заголовками,
 * списками, таблицами и встроенными (base64) картинками.
 * Поддержка подмножества: h/p/list/table/image/span/a/s/tab/line-break.
 */
object OdtToHtml {

    private const val MAX_ENTRY_BYTES = 8L * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 2L * 1024 * 1024

    private val NS_OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    private val NS_TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private val NS_TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    private val NS_XLINK = "http://www.w3.org/1999/xlink"

    suspend fun convert(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Нет доступа к файлу")
        input.use { convertStream(it) }
    }

    fun convertStream(input: InputStream): String {
        val entries = readZipEntries(input)
        val contentXml = entries["content.xml"] ?: throw IOException("content.xml не найден — это не ODT")

        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(ByteArrayInputStream(contentXml), null)

        val out = StringBuilder()
        out.append(
            "<style>body{background:#fff;color:#161616;font-family:Georgia,'Times New Roman',serif;" +
                "line-height:1.55;margin:0;padding:16px}table{border-collapse:collapse;margin:8px 0}" +
                "td,th{border:1px solid #9a9a9a;padding:4px 8px}img{max-width:100%;height:auto}</style>",
        )

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "text" && parser.namespace == NS_OFFICE) {
                renderChildren(parser, out, entries)
                break
            }
        }
        return out.toString()
    }

    private fun readZipEntries(input: InputStream): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val name = entry.name
                    val isNeeded = name == "content.xml" || name.startsWith("Pictures/")
                    if (isNeeded && entry.size <= MAX_ENTRY_BYTES) {
                        result[name] = zip.readBytes()
                    }
                }
                zip.closeEntry()
            }
        }
        return result
    }

    private fun renderChildren(parser: XmlPullParser, out: StringBuilder, entries: Map<String, ByteArray>) {
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> renderNode(parser, out, entries)
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> out.append(escape(parser.text))
                XmlPullParser.END_TAG -> return
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun renderNode(parser: XmlPullParser, out: StringBuilder, entries: Map<String, ByteArray>) {
        val name = parser.name
        when {
            name == "h" && parser.namespace == NS_TEXT -> {
                val level = (parser.getAttributeValue(NS_TEXT, "outline-level")?.toIntOrNull() ?: 1)
                    .coerceIn(1, 6)
                out.append("<h$level>")
                renderChildren(parser, out, entries)
                out.append("</h$level>")
            }

            name == "p" && parser.namespace == NS_TEXT -> {
                out.append("<p>")
                renderChildren(parser, out, entries)
                out.append("</p>")
            }

            name == "list" && parser.namespace == NS_TEXT -> {
                out.append("<ul>")
                renderChildren(parser, out, entries)
                out.append("</ul>")
            }

            name == "list-item" && parser.namespace == NS_TEXT -> {
                out.append("<li>")
                renderChildren(parser, out, entries)
                out.append("</li>")
            }

            name == "table" && parser.namespace == NS_TABLE -> {
                out.append("<table>")
                renderChildren(parser, out, entries)
                out.append("</table>")
            }

            name == "table-row" && parser.namespace == NS_TABLE -> {
                out.append("<tr>")
                renderChildren(parser, out, entries)
                out.append("</tr>")
            }

            name == "table-cell" && parser.namespace == NS_TABLE -> {
                val span = parser.getAttributeValue(NS_TABLE, "number-columns-spanned")?.toIntOrNull()
                out.append(if (span != null && span > 1) "<td colspan=\"$span\">" else "<td>")
                renderChildren(parser, out, entries)
                out.append("</td>")
            }

            name == "image" && parser.namespace.endsWith("draw:1.0") -> {
                val href = parser.getAttributeValue(NS_XLINK, "href").orEmpty()
                val bytes = entries[href]
                if (bytes != null && bytes.size <= MAX_IMAGE_BYTES) {
                    val mime = when {
                        href.endsWith(".png", true) -> "image/png"
                        href.endsWith(".jpg", true) || href.endsWith(".jpeg", true) -> "image/jpeg"
                        href.endsWith(".gif", true) -> "image/gif"
                        else -> "image/png"
                    }
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    out.append("<img alt=\"\" src=\"data:$mime;base64,$base64\">")
                } else {
                    out.append("<p><i>[изображение]</i></p>")
                }
                skipSubtree(parser)
            }

            name == "span" && parser.namespace == NS_TEXT -> {
                out.append("<span>")
                renderChildren(parser, out, entries)
                out.append("</span>")
            }

            name == "a" && parser.namespace == NS_TEXT -> {
                val href = parser.getAttributeValue(NS_XLINK, "href").orEmpty()
                out.append("<a href=\"").append(escape(href)).append("\">")
                renderChildren(parser, out, entries)
                out.append("</a>")
            }

            name == "s" && parser.namespace == NS_TEXT -> {
                val count = parser.getAttributeValue(NS_TEXT, "c")?.toIntOrNull() ?: 1
                out.append("&nbsp;".repeat(count.coerceIn(1, 64)))
                skipSubtree(parser)
            }

            name == "tab" && parser.namespace == NS_TEXT -> {
                out.append("&nbsp;&nbsp;&nbsp;&nbsp;")
                skipSubtree(parser)
            }

            name == "line-break" && parser.namespace == NS_TEXT -> {
                out.append("<br>")
                skipSubtree(parser)
            }

            name == "annotation" && parser.namespace == NS_OFFICE -> skipSubtree(parser)

            else -> renderChildren(parser, out, entries)
        }
    }

    /** Просмотреть subtree, ничего не выводя (при текущем START_TAG). */
    private fun skipSubtree(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
