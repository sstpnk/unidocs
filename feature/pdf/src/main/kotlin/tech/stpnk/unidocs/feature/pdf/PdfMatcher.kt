package tech.stpnk.unidocs.feature.pdf

import tech.stpnk.unidocs.core.model.ByteSequence

/** Логика «свой ли это файл» для PDF — вынесена отдельно, чтобы тестировалась на JVM. */
object PdfMatcher {

    private val MAGIC = byteArrayOf(0x25, 0x50, 0x44, 0x46) // "%PDF"
    private val mimes = setOf("application/pdf", "application/x-pdf")
    private val extensions = setOf("pdf")

    fun supports(mimeType: String?, displayName: String, head: ByteSequence): Boolean {
        val mime = mimeType?.lowercase()
        if (mime != null && mime in mimes) return true
        val ext = displayName.substringAfterLast('.', "").lowercase()
        if (ext in extensions) return true
        return head.startsWith(0x25, 0x50, 0x44, 0x46) || head.indexOf(MAGIC) == 0
    }
}
