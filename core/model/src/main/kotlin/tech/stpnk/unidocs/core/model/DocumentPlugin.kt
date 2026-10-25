package tech.stpnk.unidocs.core.model

/** Всё, что плагину нужно знать о выбранном пользователем документе до открытия. */
data class OpenContext(
    /** URI SAF в строковом виде (модель намеренно не зависит от android.net.Uri). */
    val uri: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    /** Ленивое чтение первых байт — вызывается, только если MIME-типа недостаточно. */
    val sniff: suspend () -> ByteSequence,
)

/** Краткие сведения о документе для шапки экрана и «Недавних». */
data class DocumentInfo(
    val title: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val pageCount: Int? = null,
)

data class OutlineNode(
    val title: String,
    val level: Int,
    val target: Int,
)

data class SearchHit(
    val page: Int?,
    val snippet: String,
    val charOffset: Int,
)

/** Куда сохранять результат редактирования. */
data class SaveTarget(
    val uri: String,
    /** true — писать в исходный документ, false — «Сохранить как». */
    val inPlace: Boolean,
)

/**
 * Плагин формата: умеет определить «свой» файл и открыть сессию документа.
 * Реализации живут в :feature:* модулях.
 */
interface DocumentPlugin {
    val id: String
    val displayName: String

    fun supports(mimeType: String?, displayName: String, head: ByteSequence): Boolean

    suspend fun open(context: OpenContext): DocumentSession
}
