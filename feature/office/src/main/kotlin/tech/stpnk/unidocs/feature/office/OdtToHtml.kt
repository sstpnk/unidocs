package tech.stpnk.unidocs.feature.office

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.zip.ZipInputStream

/**
 * Конвертер ODT → HTML: заголовки, абзацы, списки, таблицы (вкл. colspan),
 * картинки (base64 ≤ 2 МБ) и ФОРМАТИРОВАНИЕ текста через автоматические стили
 * (жирный/курсив/подчёркивание/цвет/размер/выравнивание, наследование parent-style).
 */
object OdtToHtml {

    private const val MAX_ENTRY_BYTES = 8L * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 2L * 1024 * 1024

    private val NS_OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
    private val NS_TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0"
    private val NS_TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
    private val NS_STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0"
    private val NS_XLINK = "http://www.w3.org/1999/xlink"

    suspend fun convert(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Нет доступа к файлу")
        input.use { convertStream(it) }
    }

    fun convertStream(input: InputStream): String {
        val entries = readZipEntries(input)
        val contentXml = entries["content.xml"] ?: throw IOException("content.xml не найден — это не ODT")

        // Проход 1: автоматические стили
        val styles = collectStyles(newParser(contentXml))

        // Проход 2: тело документа
        val bodyParser = newParser(contentXml)
        val out = StringBuilder()
        out.append(
            "<style>body{background:#fff;color:#161616;font-family:Georgia,'Times New Roman',serif;" +
                "line-height:1.55;margin:0;padding:16px}table{border-collapse:collapse;margin:8px 0}" +
                "td,th{border:1px solid #9a9a9a;padding:4px 8px}img{max-width:100%;height:auto}</style>",
        )

        while (bodyParser.next() != XmlPullParser.END_DOCUMENT) {
            if (bodyParser.eventType == XmlPullParser.START_TAG && bodyParser.name == "text" && bodyParser.namespace == NS_OFFICE) {
                renderChildren(bodyParser, out, entries, styles)
                break
            }
        }
        return out.toString()
    }

    private fun newParser(contentXml: ByteArray): XmlPullParser {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(ByteArrayInputStream(contentXml), null)
        return parser
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

    // ===== Стили =====

    private data class OdtStyle(
        val bold: Boolean? = null,
        val italic: Boolean? = null,
        val underline: Boolean? = null,
        val color: String? = null,
        val sizePt: String? = null,
        val align: String? = null,
        val parent: String? = null,
    )

    private fun collectStyles(parser: XmlPullParser): Map<String, OdtStyle> {
        val styles = mutableMapOf<String, OdtStyle>()
        var inAutoStyles = false
        var currentName: String? = null
        val props = mutableMapOf<String, String?>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when {
                        parser.name == "automatic-styles" && parser.namespace == NS_OFFICE -> inAutoStyles = true

                        inAutoStyles && parser.name == "style" && parser.namespace == NS_STYLE -> {
                            currentName = parser.getAttributeValue(NS_STYLE, "name")
                            props.clear()
                            val parent = parser.getAttributeValue(NS_STYLE, "parent-style-name")
                            if (parent != null) props["parent"] = parent
                        }

                        inAutoStyles && currentName != null &&
                            (parser.name == "text-properties" || parser.name == "paragraph-properties") -> {
                            for (i in 0 until parser.attributeCount) {
                                val prefix = parser.getAttributePrefix(i)
                                val name = parser.getAttributeName(i)
                                val value = parser.getAttributeValue(i) ?: continue
                                when {
                                    prefix == "fo" && name == "font-weight" -> props["bold"] = value
                                    prefix == "fo" && name == "font-style" -> props["italic"] = value
                                    prefix == "fo" && name == "text-underline-style" ->
                                        props["underline"] = if (value == "none") "false" else "true"
                                    prefix == "fo" && name == "color" -> props["color"] = value
                                    prefix == "fo" && name == "font-size" -> props["size"] = value
                                    prefix == "fo" && name == "text-align" -> props["align"] = value
                                }
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (inAutoStyles && parser.name == "style" && parser.namespace == NS_STYLE) {
                        val name = currentName
                        if (name != null) {
                            styles[name] = OdtStyle(
                                bold = props["bold"]?.let { it == "bold" },
                                italic = props["italic"]?.let { it == "italic" },
                                underline = props["underline"]?.let { it == "true" },
                                color = props["color"],
                                sizePt = props["size"],
                                align = props["align"],
                                parent = props["parent"],
                            )
                        }
                        currentName = null
                        props.clear()
                    } else if (parser.name == "automatic-styles" && parser.namespace == NS_OFFICE) {
                        inAutoStyles = false
                    }
                }
            }
        }
        return styles
    }

    /** Сливает свойства стиля с учётом цепочки parent-style-name. */
    private fun resolveProps(styleName: String?, styles: Map<String, OdtStyle>, kind: PropKind): String? {
        var name = styleName
        val merged = mutableMapOf<String, String>()
        var depth = 0
        while (name != null && depth < 8) {
            val style = styles[name] ?: break
            fun take(key: String, value: String?) {
                if (merged.containsKey(key)) return
                value?.let { merged[key] = it }
            }
            when (kind) {
                PropKind.TEXT -> {
                    take("font-weight", style.bold?.let { if (it) "bold" else "normal" })
                    take("font-style", style.italic?.let { if (it) "italic" else "normal" })
                    take("text-decoration", style.underline?.let { if (it) "underline" else "none" })
                    take("color", style.color)
                    take("font-size", style.sizePt)
                }
                PropKind.PARAGRAPH -> {
                    take("text-align", style.align)
                    take("font-weight", style.bold?.let { if (it) "bold" else "normal" })
                    take("font-style", style.italic?.let { if (it) "italic" else "normal" })
                    take("color", style.color)
                    take("font-size", style.sizePt)
                }
            }
            name = style.parent
            depth++
        }
        if (merged.isEmpty()) return null
        return merged.entries.joinToString(";") { "${it.key}:${it.value}" }
    }

    private enum class PropKind { TEXT, PARAGRAPH }

    // ===== Рендер тела =====

    private fun renderChildren(
        parser: XmlPullParser,
        out: StringBuilder,
        entries: Map<String, ByteArray>,
        styles: Map<String, OdtStyle>,
    ) {
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> renderNode(parser, out, entries, styles)
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> out.append(escape(parser.text))
                XmlPullParser.END_TAG -> return
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun renderNode(
        parser: XmlPullParser,
        out: StringBuilder,
        entries: Map<String, ByteArray>,
        styles: Map<String, OdtStyle>,
    ) {
        val name = parser.name
        when {
            name == "h" && parser.namespace == NS_TEXT -> {
                val level = (parser.getAttributeValue(NS_TEXT, "outline-level")?.toIntOrNull() ?: 1)
                    .coerceIn(1, 6)
                val styleAttr = paragraphStyleAttr(parser, styles)
                out.append("<h$level").append(styleAttr).append(">")
                renderChildren(parser, out, entries, styles)
                out.append("</h$level>")
            }

            name == "p" && parser.namespace == NS_TEXT -> {
                val styleAttr = paragraphStyleAttr(parser, styles)
                out.append("<p").append(styleAttr).append(">")
                renderChildren(parser, out, entries, styles)
                out.append("</p>")
            }

            name == "list" && parser.namespace == NS_TEXT -> {
                out.append("<ul>")
                renderChildren(parser, out, entries, styles)
                out.append("</ul>")
            }

            name == "list-item" && parser.namespace == NS_TEXT -> {
                out.append("<li>")
                renderChildren(parser, out, entries, styles)
                out.append("</li>")
            }

            name == "table" && parser.namespace == NS_TABLE -> {
                out.append("<table>")
                renderChildren(parser, out, entries, styles)
                out.append("</table>")
            }

            name == "table-row" && parser.namespace == NS_TABLE -> {
                out.append("<tr>")
                renderChildren(parser, out, entries, styles)
                out.append("</tr>")
            }

            name == "table-cell" && parser.namespace == NS_TABLE -> {
                val span = parser.getAttributeValue(NS_TABLE, "number-columns-spanned")?.toIntOrNull()
                out.append(if (span != null && span > 1) "<td colspan=\"$span\">" else "<td>")
                renderChildren(parser, out, entries, styles)
                out.append("</td>")
            }

            name == "image" && parser.namespace.endsWith("drawing:1.0") -> {
                val href = parser.getAttributeValue(NS_XLINK, "href").orEmpty()
                val bytes = entries[href]
                if (bytes != null && bytes.size <= MAX_IMAGE_BYTES) {
                    val mime = when {
                        href.endsWith(".png", true) -> "image/png"
                        href.endsWith(".jpg", true) || href.endsWith(".jpeg", true) -> "image/jpeg"
                        href.endsWith(".gif", true) -> "image/gif"
                        else -> "image/png"
                    }
                    val base64 = Base64.getEncoder().encodeToString(bytes)
                    out.append("<img alt=\"\" src=\"data:$mime;base64,$base64\">")
                } else {
                    out.append("<p><i>[изображение]</i></p>")
                }
                skipSubtree(parser)
            }

            name == "span" && parser.namespace == NS_TEXT -> {
                val styleName = parser.getAttributeValue(NS_TEXT, "style-name")
                val styleAttr = resolveProps(styleName, styles, PropKind.TEXT)
                    ?.let { " style=\"$it\"" } ?: ""
                out.append("<span").append(styleAttr).append(">")
                renderChildren(parser, out, entries, styles)
                out.append("</span>")
            }

            name == "a" && parser.namespace == NS_TEXT -> {
                val href = parser.getAttributeValue(NS_XLINK, "href").orEmpty()
                out.append("<a href=\"").append(escape(href)).append("\">")
                renderChildren(parser, out, entries, styles)
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

            else -> renderChildren(parser, out, entries, styles)
        }
    }

    private fun paragraphStyleAttr(parser: XmlPullParser, styles: Map<String, OdtStyle>): String =
        resolveProps(parser.getAttributeValue(NS_TEXT, "style-name"), styles, PropKind.PARAGRAPH)
            ?.let { " style=\"$it\"" } ?: ""

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
}
