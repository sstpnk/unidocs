package tech.stpnk.unidocs.core.storage

import kotlinx.serialization.Serializable

/** Запись в списке «Недавние». */
@Serializable
data class RecentDocument(
    val uri: String,
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val openedAtEpochMs: Long,
)
