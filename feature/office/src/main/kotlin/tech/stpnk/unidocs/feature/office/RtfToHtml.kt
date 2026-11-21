package tech.stpnk.unidocs.feature.office

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Минимальный RTF → HTML: текст, жирный/курсив/подчёркивание, размер шрифта,
 * абзацы, переводы строк, unicode \uN и байты \'hh через \ansicpg.
 * Таблицы и картинки RTF не рендерятся (v1.1 — честное ограничение).
 */
object RtfToHtml {

    /** Известные destination-группы RTF — служебные структуры, не текст документа. */
    private val DESTINATIONS = setOf(
        "fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "mmathPr",
        "header", "footer", "headerl", "headerr", "headerf", "footerl", "footerr", "footerf",
        "footnote", "annotation", "xfilt", "xmlnstbl", "themedata", "colorschememapping",
        "datastore", "latentstyles", "listtable", "listoverridetable", "rsidtbl", "generator",
        "filetbl", "revtbl", "pgptbl", "pgdsctbl", "wgrffmtfilter", "fldinst", "formfield",
        "do", "bkmkstart", "bkmkend", "shpinst", "nonshppict", "defchp", "defpap", "pn",
        "pnseclvl", "passwordhash", "upr", "ud", "private", "objdata", "objclass", "objname",
        "rslt", "genobj", "sn", "sv", "falt", "result", "wfonts", "pntext", "do", "object",
    )

    private const val MAX_INPUT = 32L * 1024 * 1024

    fun convert(bytes: ByteArray): String = convertStream(ByteArrayInputStream(bytes))

    fun convertStream(input: InputStream): String {
        val raw = input.use { stream ->
            val bytes = stream.readBytes()
            check(bytes.size <= MAX_INPUT) { "Файл больше ${MAX_INPUT / (1024 * 1024)} МБ" }
            String(bytes, Charsets.ISO_8859_1)
        }
        if (!raw.startsWith("{\\rtf")) throw IOException("Это не RTF")

        val charset = detectCharset(raw)
        val out = StringBuilder()
        out.append(
            "<style>body{background:#fff;color:#111;font-family:'Times New Roman',serif;line-height:1.5;margin:0;padding:16px}p{margin:0 0 10px}</style>",
        )

        val stack = ArrayDeque<Format>()
        var current = Format.DEFAULT
        var skipDepth = 0           // >0 — внутри {\*\...} группы-приёмника
        var pending = StringBuilder()
        var pendingBytes = StringBuilder()
        var openP = false
        var ucSkip = 1
        var index = 0

        fun flushPendingBytes() {
            if (pendingBytes.isEmpty()) return
            val decoded = String(pendingBytes.toString().toByteArray(Charsets.ISO_8859_1), charset)
            pendingBytes = StringBuilder()
            pending.append(decoded)
        }

        fun spanStyle(f: Format): String? {
            val parts = mutableListOf<String>()
            if (f.bold) parts += "font-weight:bold"
            if (f.italic) parts += "font-style:italic"
            if (f.underline) parts += "text-decoration:underline"
            f.halfPoints?.let { parts += "font-size:${it / 2.0}pt" }
            return if (parts.isEmpty()) null else parts.joinToString(";")
        }

        fun flush() {
            flushPendingBytes()
            val text = pending.toString()
            pending = StringBuilder()
            if (text.isEmpty()) return
            val style = spanStyle(current)
            if (style == null) {
                out.append(escape(text))
            } else {
                out.append("<span style=\"").append(style).append("\">").append(escape(text)).append("</span>")
            }
        }

        fun appendChar(ch: Char) {
            flushPendingBytes()
            pending.append(ch)
        }

        fun setFormat(
            bold: Boolean = current.bold,
            italic: Boolean = current.italic,
            underline: Boolean = current.underline,
            halfPoints: Int? = current.halfPoints,
        ) {
            val next = Format(bold, italic, underline, halfPoints)
            if (next != current) {
                flush()
                current = next
            }
        }

        fun openParagraph() {
            if (!openP) {
                out.append("<p>")
                openP = true
            }
        }

        fun closeParagraph() {
            flush()
            if (openP) out.append("</p>")
            openP = false
        }

        fun parseControl() {
            if (index >= raw.length) return
            val ch = raw[index]
            if (ch == '\'') {
                val hex = raw.substring(index + 1, (index + 3).coerceAtMost(raw.length))
                index += 3
                pendingBytes.append(((hex.toIntOrNull(16) ?: 0x3F) and 0xFF).toChar())
                return
            }
            if (!ch.isLetter()) {
                appendChar(ch)
                index++
                return
            }
            val start = index
            while (index < raw.length && raw[index].isLetter()) index++
            val word = raw.substring(start, index)
            var param: Int? = null
            if (index < raw.length && (raw[index] == '-' || raw[index].isDigit())) {
                val pStart = index
                if (raw[index] == '-') index++
                while (index < raw.length && raw[index].isDigit()) index++
                param = raw.substring(pStart, index).toIntOrNull()
            }
            if (index < raw.length && raw[index] == ' ') index++

            when (word) {
                "b" -> setFormat(bold = !(param != null && param == 0))
                "i" -> setFormat(italic = !(param != null && param == 0))
                "ul" -> setFormat(underline = !(param != null && param == 0))
                "ulnone" -> setFormat(underline = false)
                "fs" -> param?.let { setFormat(halfPoints = it) }
                "par" -> {
                    flush()
                    if (openP) out.append("</p>")
                    out.append("<p>")
                    openP = true
                }
                "line" -> {
                    flush()
                    out.append("<br>")
                }
                "tab" -> {
                    flush()
                    out.append("&nbsp;&nbsp;&nbsp;&nbsp;")
                }
                "u" -> param?.let {
                    flushPendingBytes()
                    val code = if (it < 0) it + 65536 else it
                    pending.append(code.toChar())
                    index += ucSkip.coerceAtLeast(0)
                }
                "uc" -> param?.let { ucSkip = it }
                "emdash" -> { flushPendingBytes(); pending.append('\u2014') }
                "endash" -> { flushPendingBytes(); pending.append('\u2013') }
                "lquote" -> { flushPendingBytes(); pending.append('\u2018') }
                "rquote" -> { flushPendingBytes(); pending.append('\u2019') }
                "ldblquote" -> { flushPendingBytes(); pending.append('\u201C') }
                "rdblquote" -> { flushPendingBytes(); pending.append('\u201D') }
                "bullet" -> { flushPendingBytes(); pending.append('\u2022') }
                "~" -> { flushPendingBytes(); pending.append('\u00A0') }
                "_" -> { flushPendingBytes(); pending.append('-') }
                else -> {
                    // Destination-группа без \*: fonttbl/colortbl/info и т.п. —
                    // содержимое пропускаем, чтобы не выдавать артефакты.
                    if (word in DESTINATIONS) {
                        skipDepth = 1
                        if (openP) {
                            closeParagraph()
                            openParagraph()
                        }
                    }
                }
            }
        }

        openParagraph()
        while (index < raw.length) {
            val ch = raw[index]
            if (skipDepth > 0) {
                when (ch) {
                    '{' -> skipDepth++
                    '}' -> skipDepth--
                }
                index++
                continue
            }
            when {
                ch == '{' -> {
                    flush()
                    stack.addLast(current)
                    if (raw.startsWith("\\*", index + 1)) skipDepth = 1
                    index++
                }

                ch == '}' -> {
                    flush()
                    current = stack.removeLastOrNull() ?: Format.DEFAULT
                    index++
                }

                ch == '\\' -> {
                    index++
                    parseControl()
                }

                ch == '\n' || ch == '\r' -> index++

                // Лояльность к «битым» RTF: сырые байты >127 декодируем через кодовую страницу
                ch.code > 127 -> {
                    pendingBytes.append(ch)
                    index++
                }

                else -> {
                    appendChar(ch)
                    index++
                }
            }
        }
        closeParagraph()
        return out.toString()
    }

    private data class Format(val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false, val halfPoints: Int? = null) {
        companion object {
            val DEFAULT = Format()
        }
    }

    private fun detectCharset(raw: String): Charset {
        val codepage = Regex("\\\\ansicpg(\\d+)").find(raw)?.groupValues?.get(1) ?: "1252"
        return runCatching { Charset.forName("windows-$codepage") }.getOrDefault(Charset.forName("windows-1252"))
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
