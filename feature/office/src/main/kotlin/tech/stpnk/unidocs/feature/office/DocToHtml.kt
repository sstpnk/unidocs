package tech.stpnk.unidocs.feature.office

import java.io.IOException
import kotlin.math.min
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Читатель legacy .doc (Word 97-2003, бинарный OLE/CFB) без внешних библиотек:
 * - разбор составного файла (CFB): FAT/DIFAT, директория, miniFAT;
 * - FIB: ccpText, lid, fcClx/lcbClx, флаг fWhichTblStm;
 * - текст: piece table (Clx → PlcPcd: сжатые 8-бит и UTF-16 куски),
 *   при отсутствии — legacy-диапазон fcMin..fcMac;
 * - поля (0x13/0x14/0x15): отдаём результат, инструкции пропускаем.
 * Форматирование (жирный/шрифты) не извлекается — ограничение v1.3.
 */
object DocToHtml {

    private const val END_OF_CHAIN = 0xFFFFFFFE.toInt()
    private const val FAT_SECTOR = 0xFFFFFFFD.toInt()
    private const val DIFAT_SECTOR = 0xFFFFFFFC.toInt()
    private const val FREE_SECTOR = 0xFFFFFFFF.toInt()

    fun convert(bytes: ByteArray): String {
        val cfb = Cfb.parse(bytes)
        val word = cfb.stream("WordDocument")
            ?: throw IOException("Stream WordDocument не найден — файл не является документом Word (.doc)")

        val flags = word.u16(0x0A)
        val lid = word.u16(0x06)
        val ccpText = word.i32(0x4C)
        val fcClx = word.i32(0x1A2)
        val lcbClx = word.i32(0x1A6)

        val tableName = if ((flags and 0x0200) != 0) "1Table" else "0Table"
        val table = cfb.stream(tableName)
            ?: cfb.stream("1Table")
            ?: cfb.stream("0Table")

        val charset = charsetFor(lid)

        val text = if (lcbClx > 0 && table != null) {
            pieceTableText(word, table, fcClx, ccpText, charset)
        } else {
            legacyText(word, flags, ccpText, charset)
        }
        if (text.isBlank()) throw IOException("Не удалось извлечь текст из .doc")

        return renderHtml(text)
    }

    // ===== Кодировка 8-битных кусков =====

    private fun charsetFor(lid: Int): Charset {
        if (lid == 0x0419) return Charset.forName("windows-1251")
        if (lid == 0x0422 || lid == 0x0423 || lid == 0x042F) return Charset.forName("windows-1251")
        return Charset.forName("windows-1252")
    }

    /** Если cp1252 дал мусор (управляющие C1) — документ, скорее всего, в cp1251. */
    private fun decode8Bit(chunk: ByteArray, base: Charset): String {
        val primary = decodeStrict(chunk, base)
        if (base != Charset.forName("windows-1251")) {
            val controls = primary.count { it.code in 0x80..0x9F }
            if (controls > primary.length / 20) {
                return decodeStrict(chunk, Charset.forName("windows-1251"))
            }
        }
        return primary
    }

    private fun decodeStrict(chunk: ByteArray, charset: Charset): String =
        try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .decode(java.nio.ByteBuffer.wrap(chunk))
                .toString()
        } catch (e: CharacterCodingException) {
            String(chunk, Charsets.ISO_8859_1)
        }

    // ===== Текст =====

    private fun pieceTableText(
        word: ByteArray,
        table: ByteArray,
        fcClx: Int,
        ccpText: Int,
        charset: Charset,
    ): String {
        var p = fcClx
        // Prc'ы пропускаем, ищем Pcdt (0x02)
        while (p < table.size && table[p].toInt() == 0x01) {
            if (p + 3 > table.size) break
            val cb = table.u16(p + 1)
            p += 3 + cb
        }
        if (p >= table.size || table[p].toInt() != 0x02) {
            throw IOException("Piece table (Pcdt) в .doc не найдена")
        }
        val lcbPlc = table.i32(p + 1)
        val plcStart = p + 5
        if (lcbPlc < 16 || plcStart + lcbPlc > table.size) {
            throw IOException("Повреждённая piece table в .doc")
        }
        val n = (lcbPlc - 4) / 12
        val sb = StringBuilder()
        val cpsBase = plcStart
        val pcdBase = plcStart + 4 * (n + 1)
        var total = 0
        for (i in 0 until n) {
            if (total >= ccpText && ccpText > 0) break
            val cpStart = table.i32(cpsBase + 4 * i)
            val cpEnd = table.i32(cpsBase + 4 * (i + 1))
            val chars = cpEnd - cpStart
            if (chars <= 0) continue
            val fcRaw = table.i32(pcdBase + 8 * i + 2)
            val compressed = (fcRaw and 0x40000000) != 0
            val chunk: String = if (compressed) {
                val off = (fcRaw and 0x3FFFFFFF) ushr 1
                val end = minOf(off + chars, word.size)
                if (off < 0 || off >= end) continue
                decode8Bit(word.copyOfRange(off, end), charset)
            } else {
                val off = fcRaw and 0x3FFFFFFF
                val end = minOf(off + chars * 2, word.size)
                if (off < 0 || off >= end) continue
                decodeStrict(word.copyOfRange(off, end), Charsets.UTF_16LE)
            }
            sb.append(chunk)
            total += chars
        }
        val text = if (ccpText > 0 && sb.length > ccpText) sb.substring(0, ccpText) else sb.toString()
        if (text.isBlank()) throw IOException("Piece table не дала текста")
        return text
    }

    private fun legacyText(word: ByteArray, flags: Int, ccpText: Int, charset: Charset): String {
        val fcMin = word.i32(0x18)
        val fcMac = word.i32(0x1C)
        if (fcMin < 0 || fcMac <= fcMin || fcMac > word.size) {
            throw IOException("Не найден текст документа (fcMin/fcMac некорректны)")
        }
        val fExtChar = (flags and 0x1000) != 0
        return if (fExtChar) {
            decodeStrict(word.copyOfRange(fcMin, fcMac), Charsets.UTF_16LE)
        } else {
            decode8Bit(word.copyOfRange(fcMin, fcMac), charset)
        }
    }

    // ===== HTML =====

    private fun renderHtml(raw: String): String {
        val out = StringBuilder()
        out.append(
            "<style>body{background:#fff;color:#161616;font-family:Georgia,'Times New Roman',serif;" +
                "line-height:1.55;margin:0;padding:16px}p{margin:0 0 10px}</style>",
        )
        var inP = false
        fun openP() {
            if (!inP) {
                out.append("<p>")
                inP = true
            }
        }
        fun closeP() {
            if (inP) {
                out.append("</p>")
                inP = false
            }
        }

        var fieldDepth = 0
        var inFieldResult = false
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when (c.code) {
                0x0D, 0x0C -> { // конец абзаца / разрыв страницы-секции
                    closeP()
                }
                0x07 -> { // конец ячейки/строки таблицы
                    openP()
                    out.append(" | ")
                }
                0x0B -> { // разрыв строки
                    openP()
                    out.append("<br>")
                }
                0x09 -> { // табуляция
                    openP()
                    out.append("&nbsp;&nbsp;&nbsp;&nbsp;")
                }
                0x13 -> { // начало поля: инструкции пропускаем
                    fieldDepth++
                    inFieldResult = false
                }
                0x14 -> { // разделитель поля: дальше результат
                    if (fieldDepth > 0 && fieldDepth == 1) inFieldResult = true
                }
                0x15 -> { // конец поля
                    if (fieldDepth > 0) fieldDepth--
                    inFieldResult = false
                }
                0x1E -> { openP(); out.append('-') }
                0x1A, 0x02, 0x03, 0x05, 0x08, 0x01 -> { /* служебные ссылки/объекты */ }
                0x1F -> { /* условный дефис */ }
                else -> {
                    val printable = c.code >= 0x20 || c == '\t'
                    if (fieldDepth == 0 || inFieldResult) {
                        if (printable) {
                            openP()
                            out.append(escape(c))
                        }
                    }
                }
            }
            i++
        }
        closeP()
        if (out.length <= 120) throw IOException("Текст .doc пуст")
        return out.toString()
    }

    private fun escape(c: Char): String = when (c) {
        '&' -> "&amp;"
        '<' -> "&lt;"
        '>' -> "&gt;"
        '"' -> "&quot;"
        else -> c.toString()
    }

    // ===== CFB (составной файл OLE) =====

    private class Cfb(
        private val sectorSize: Int,
        private val miniSize: Int,
        private val miniCutoff: Int,
        private val fat: IntArray,
        private val miniFat: IntArray,
        private val sectors: ByteArray,
        private val entries: List<Entry>,
    ) {
        class Entry(val name: String, val type: Int, val start: Int, val size: Long)

        private val rootEntry by lazy { entries.first { it.type == 5 } }

        fun stream(name: String): ByteArray? {
            val entry = entries.firstOrNull { it.type == 2 && it.name.equals(name, ignoreCase = true) }
                ?: return null
            return if (entry.size < miniCutoff) readMini(entry) else readBig(entry)
        }

        private fun sector(n: Int): ByteArray {
            val off = n * sectorSize
            val end = off + sectorSize
            if (off < 0 || end > sectors.size) throw IOException("CFB: сектор $n вне файла")
            return sectors.copyOfRange(off, end)
        }

        private fun chain(start: Int, fat: IntArray): List<Int> {
            val list = mutableListOf<Int>()
            var cur = start
            var guard = 0
            while (cur in 0..0xFFFFFFFA && cur != END_OF_CHAIN) {
                list.add(cur)
                if (cur >= fat.size) throw IOException("CFB: цепочка вышла за FAT")
                cur = fat[cur]
                if (++guard > fat.size + 16) throw IOException("CFB: зацикленная цепочка")
            }
            return list
        }

        private fun readBig(entry: Entry): ByteArray {
            val total = entry.size.toInt()
            if (total < 0 || total > 256 * 1024 * 1024) throw IOException("CFB: некорректный размер потока")
            val out = ByteArray(total)
            var pos = 0
            for (sid in chain(entry.start, fat)) {
                if (pos >= out.size) break
                val chunk = sector(sid)
                val n = minOf(chunk.size, out.size - pos)
                System.arraycopy(chunk, 0, out, pos, n)
                pos += n
            }
            return out
        }

        private fun readMini(entry: Entry): ByteArray {
            val miniBlob = readBig(rootEntry)
            val out = ByteArray(entry.size.toInt())
            var pos = 0
            for (mid in chain(entry.start, miniFat)) {
                if (pos >= out.size) break
                val off = mid * miniSize
                val end = minOf(off + miniSize, miniBlob.size)
                if (off < 0 || off >= miniBlob.size) throw IOException("CFB: мини-сектор вне потока")
                val n = minOf(end - off, out.size - pos)
                System.arraycopy(miniBlob, off, out, pos, n)
                pos += n
            }
            return out
        }

        companion object {
            fun parse(bytes: ByteArray): Cfb {
                if (bytes.size < 512) throw IOException("Файл слишком мал для .doc")
                val magic = intArrayOf(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)
                for (i in magic.indices) {
                    if (bytes[i].toInt() and 0xFF != magic[i]) {
                        throw IOException("Это не документ Word (.doc): нет подписи OLE")
                    }
                }
                val sectorSize = 1 shl bytes.u16(0x1E)
                val miniSize = 1 shl bytes.u16(0x20)
                val miniCutoff = bytes.i32(0x38)
                val difatCount = bytes.i32(0x44)
                val firstDir = bytes.i32(0x30)
                val firstMiniFat = bytes.i32(0x3C)
                val numMiniFat = bytes.i32(0x40)

                val payload = bytes.copyOfRange(min(512, sectorSize), bytes.size)

                fun sectorOf(n: Int): ByteArray {
                    val off = n * sectorSize
                    if (off < 0 || off + sectorSize > payload.size) throw IOException("CFB: сектор вне файла")
                    return payload.copyOfRange(off, off + sectorSize)
                }

                // DIFAT: 109 записей в заголовке + цепочка (0x44 = кол-во, 0x48 — первый сектор)
                val fatIds = mutableListOf<Int>()
                for (i in 0 until 109) fatIds.add(bytes.i32(0x4C + 4 * i))
                var difatCur = bytes.i32(0x48)
                var difatGuard = 0
                while (difatCur in 0..0xFFFFFFFA && difatCur != END_OF_CHAIN && difatGuard <= difatCount + 8) {
                    val sec = sectorOf(difatCur)
                    val per = sectorSize / 4 - 1
                    for (i in 0 until per) fatIds.add(sec.i32(4 * i))
                    difatCur = sec.i32(sectorSize - 4)
                    difatGuard++
                }

                val fat = IntArray(fatIds.size * sectorSize / 4)
                var fatPos = 0
                for (sid in fatIds) {
                    if (sid < 0 || sid == FREE_SECTOR || sid == END_OF_CHAIN) continue
                    val sec = sectorOf(sid)
                    for (i in 0 until sectorSize / 4) {
                        if (fatPos + i < fat.size) fat[fatPos + i] = sec.i32(4 * i)
                    }
                    fatPos += sectorSize / 4
                }
                val fatTrim = fat.copyOf(fatPos.coerceAtLeast(1))

                // Директория
                val dirBytes = readChainBytes(::sectorOf, fatTrim, firstDir)
                val entries = mutableListOf<Entry>()
                var e = 0
                while (e + 128 <= dirBytes.size) {
                    val nameLen = dirBytes.u16(e + 0x40)
                    val name = if (nameLen in 2..64) {
                        String(dirBytes, e, nameLen - 2, Charsets.UTF_16LE)
                    } else ""
                    val type = dirBytes[e + 0x42].toInt() and 0xFF
                    val start = dirBytes.i32(e + 0x74)
                    val size = dirBytes.i64(e + 0x78)
                    entries.add(Entry(name, type, start, size))
                    e += 128
                }

                // MiniFAT
                val miniFatList = mutableListOf<Int>()
                if (numMiniFat > 0 && firstMiniFat in 0..0xFFFFFFFA && firstMiniFat != END_OF_CHAIN) {
                    for (sid in chain(firstMiniFat, fatTrim)) {
                        val sec = sectorOf(sid)
                        for (i in 0 until sectorSize / 4) miniFatList.add(sec.i32(4 * i))
                    }
                }

                return Cfb(
                    sectorSize, miniSize, miniCutoff,
                    fatTrim, miniFatList.toIntArray(),
                    payload, entries,
                )
            }

            private fun readChainBytes(
                sectorOf: (Int) -> ByteArray,
                fat: IntArray,
                start: Int,
            ): ByteArray {
                val out = java.io.ByteArrayOutputStream()
                var cur = start
                var guard = 0
                while (cur in 0..0xFFFFFFFA && cur != END_OF_CHAIN) {
                    out.write(sectorOf(cur))
                    if (cur >= fat.size) break
                    cur = fat[cur]
                    if (++guard > fat.size + 16) throw IOException("CFB: зацикленная цепочка директории")
                }
                return out.toByteArray()
            }

            private fun chain(start: Int, fat: IntArray): List<Int> {
                val list = mutableListOf<Int>()
                var cur = start
                var guard = 0
                while (cur in 0..0xFFFFFFFA && cur != END_OF_CHAIN) {
                    list.add(cur)
                    if (cur >= fat.size) break
                    cur = fat[cur]
                    if (++guard > fat.size + 16) break
                }
                return list
            }
        }
    }

    private fun ByteArray.u16(off: Int): Int =
        (this[off].toInt() and 0xFF) or ((this[off + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.i32(off: Int): Int =
        this.u16(off) or (this.u16(off + 2) shl 16)

    private fun ByteArray.i64(off: Int): Long =
        (this.i32(off).toLong() and 0xFFFFFFFFL) or ((this.i32(off + 4).toLong() and 0xFFFFFFFFL) shl 32)
}
