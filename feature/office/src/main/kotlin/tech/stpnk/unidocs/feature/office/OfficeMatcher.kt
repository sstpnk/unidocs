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
        "application/vnd.oasis.opendocument.spreadsheet" to "xlsx",
    )

    private val extensionToType = mapOf(
        "docx" to "docx",
        "xlsx" to "xlsx",
        "xlsm" to "xlsx",
        "pptx" to "pptx",
        "odt" to "odt",
        "ods" to "xlsx",
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
