package tech.stpnk.unidocs.feature.office

import tech.stpnk.unidocs.core.model.ByteSequence

/** Определение офисных форматов, которые умеем показывать. */
object OfficeMatcher {

    /** формат → какой рендерер в office.html */
    private val mimeToType = mapOf(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" to "pptx",
        "application/vnd.oasis.opendocument.text" to "odt",
        "application/vnd.oasis.opendocument.spreadsheet" to "odt",
        "application/vnd.oasis.opendocument.presentation" to "odt",
        "application/vnd.oasis.opendocument.graphics" to "odt",
        "application/vnd.oasis.opendocument.text-flat-xml" to "odt",
        "application/vnd.oasis.opendocument.spreadsheet-flat-xml" to "odt",
        "application/vnd.oasis.opendocument.presentation-flat-xml" to "odt",
        "application/vnd.oasis.opendocument.graphics-flat-xml" to "odt",
        "application/vnd.ms-excel" to "xlsx",
        "application/rtf" to "rtf",
        "text/rtf" to "rtf",
        "application/msword" to "doc",
        "application/vnd.ms-powerpoint" to "ppt",
        "text/html" to "html",
        "application/xhtml+xml" to "html",
        "multipart/related" to "mhtml",
        "message/rfc822" to "eml",
        "text/calendar" to "ics",
        "text/csv" to "csv",
    )

    private val extensionToType = mapOf(
        "docx" to "docx",
        "xlsx" to "xlsx",
        "xlsm" to "xlsx",
        "xls" to "xlsx",
        "pptx" to "pptx",
        "odt" to "odt",
        "ods" to "odt",
        "odp" to "odt",
        "odg" to "odt",
        "fodt" to "odt",
        "fods" to "odt",
        "fodp" to "odt",
        "fodg" to "odt",
        // MyOffice использует совместимый ODF-контейнер с другим расширением.
        "xodt" to "odt",
        "xods" to "odt",
        "xodp" to "odt",
        "rtf" to "rtf",
        "doc" to "doc",
        "ppt" to "ppt",
        "html" to "html",
        "htm" to "html",
        "mhtml" to "mhtml",
        "mht" to "mhtml",
        "csv" to "csv",
        "eml" to "eml",
        "ics" to "ics",
        "xmind" to "xmind",
        "xboard" to "xmind",
        // iWork хранит документ в IWA, но кладёт внутрь preview.jpg.
        "pages" to "iwork",
        "numbers" to "iwork",
        "numbes" to "iwork",
        "key" to "iwork",
        "keynote" to "iwork",
    )

    fun typeFor(mimeType: String?, displayName: String, head: ByteSequence): String? {
        val mime = mimeType?.lowercase()
        if (mime != null) mimeToType[mime]?.let { return it }
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return extensionToType[ext]
    }

    fun supports(mimeType: String?, displayName: String, head: ByteSequence): Boolean =
        typeFor(mimeType, displayName, head) != null
}
