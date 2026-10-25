package tech.stpnk.unidocs.core.model

/**
 * Начало файла документа для «сниффинга» формата (по сигнатурам/содержимому),
 * когда MIME-тип от SAF отсутствует или врёт.
 */
class ByteSequence(val bytes: ByteArray) {

    val size: Int get() = bytes.size

    fun startsWith(vararg prefix: Int): Boolean {
        if (bytes.size < prefix.size) return false
        return prefix.indices.all { bytes[it].toInt() and 0xFF == prefix[it] }
    }

    fun indexOf(needle: ByteArray, start: Int = 0): Int {
        if (needle.isEmpty() || needle.size > bytes.size) return -1
        outer@ for (i in start..bytes.size - needle.size) {
            for (j in needle.indices) {
                if (bytes[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    fun containsAscii(text: String): Boolean = indexOf(text.toByteArray(Charsets.US_ASCII)) >= 0
}
