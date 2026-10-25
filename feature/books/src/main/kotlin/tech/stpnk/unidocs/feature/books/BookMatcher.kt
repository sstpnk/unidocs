package tech.stpnk.unidocs.feature.books

import tech.stpnk.unidocs.core.model.ByteSequence

/** Определение «свой ли файл» для книг — чистая логика, тестируется на JVM. */
object BookMatcher {

    private val mimes = setOf(
        "application/epub+zip",
        "application/x-mobipocket-ebook",
        "application/vnd.amazon.ebook",
        "application/x-fictionbook",
        "application/x-fictionbook+xml",
        "application/vnd.comicbook+zip",
        "application/x-cbr",
        "application/vnd.comicbook-rar",
    )

    private val extensions = setOf(
        "epub", "mobi", "prc", "azw", "azw3", "kfx", "fb2", "fbz", "cbz", "cbr",
    )

    fun supports(mimeType: String?, displayName: String, head: ByteSequence): Boolean {
        val mime = mimeType?.lowercase()
        if (mime != null && mime in mimes) return true
        val ext = displayName.substringAfterLast('.', "").lowercase()
        if (ext in extensions) return true

        // Магия Mobipocket: "BOOKMOBI" со смещением 60
        if (head.size >= 68 && String(head.bytes, 60, 8) == "BOOKMOBI") return true
        // ZIP-контейнеры (epub/cbz): PK\x03\x04 — только вместе с расширением/типом,
        // иначе обычный zip-архив ложно станет книгой
        return false
    }
}
