package tech.stpnk.unidocs.feature.pdf

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import tech.stpnk.unidocs.core.model.DocumentInfo
import tech.stpnk.unidocs.core.model.DocumentSession
import tech.stpnk.unidocs.core.model.DocumentSurface
import tech.stpnk.unidocs.core.model.OpenContext
import tech.stpnk.unidocs.core.model.OutlineNode
import tech.stpnk.unidocs.core.model.SaveTarget
import tech.stpnk.unidocs.core.model.SearchHit
import androidx.pdf.PdfDocument

/**
 * Сессия PDF на androidx.pdf: документ открыт в sandbox-сервисе,
 * PdfView получает его целиком; поиск идёт через PdfDocument.searchDocument.
 * Оглавление (bookmarks) в androidx.pdf beta не экспортируется — пусто, честно.
 */
class PdfSession(
    val document: PdfDocument,
    private val openContext: OpenContext,
    @Suppress("UNUSED_PARAMETER") appContext: Context,
) : DocumentSession {

    /** URI для fallback-движка (PdfRenderer читает файл напрямую). */
    val uriString: String = openContext.uri

    override val info: DocumentInfo = DocumentInfo(
        title = openContext.displayName,
        mimeType = openContext.mimeType,
        sizeBytes = openContext.sizeBytes,
        pageCount = document.pageCount,
    )

    override val surface: DocumentSurface = object : DocumentSurface {}

    override val outline: Flow<List<OutlineNode>> = flowOf(emptyList())

    override fun search(query: String): Flow<SearchHit> = flow {
        if (query.isBlank()) return@flow
        val matches = document.searchDocument(query, 0 until document.pageCount)
        for (page in 0 until matches.size()) {
            matches.get(page)?.forEach {
                emit(SearchHit(page = page, snippet = "Совпадение на странице ${page + 1}", charOffset = page))
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun save(target: SaveTarget) {
        throw UnsupportedOperationException("Редактирование PDF появится в M3 (формы и факсимиле)")
    }

    override suspend fun close() {
        runCatching { document.close() }
    }
}
