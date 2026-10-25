package tech.stpnk.unidocs.feature.books

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import tech.stpnk.unidocs.core.model.DocumentInfo
import tech.stpnk.unidocs.core.model.DocumentSession
import tech.stpnk.unidocs.core.model.DocumentSurface
import tech.stpnk.unidocs.core.model.OpenContext
import tech.stpnk.unidocs.core.model.OutlineNode
import tech.stpnk.unidocs.core.model.SaveTarget
import tech.stpnk.unidocs.core.model.SearchHit

/**
 * Сессия книги. Пагинация и оглавление приходят из foliate-js через JS-мост,
 * поэтому [outline] — StateFlow, обновляемый экраном; поиск появится при
 * интеграции search.js (в M4 не заявлен).
 */
class BookSession(
    private val appContext: Context,
    private val openContext: OpenContext,
    /** Исходный SAF-uri (для CBR — оригинал; файл для читателя может быть кэш-cbz). */
    val sourceUri: String,
    val format: String,
) : DocumentSession {

    /** URI файла для читателя: SAF для обычных форматов, file://cbz для CBR. */
    val readerUri: String = openContext.uri

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _outline = MutableStateFlow<List<OutlineNode>>(emptyList())
    val toc: StateFlow<List<OutlineNode>> = _outline.asStateFlow()

    private val _chapter = MutableStateFlow("")
    val chapter: StateFlow<String> = _chapter.asStateFlow()

    val store = BooksStore(appContext)

    override val info: DocumentInfo = DocumentInfo(
        title = openContext.displayName,
        mimeType = openContext.mimeType,
        sizeBytes = openContext.sizeBytes,
    )

    override val surface: DocumentSurface = object : DocumentSurface {}

    override val outline: Flow<List<OutlineNode>> = toc

    override fun search(query: String): Flow<SearchHit> = emptyFlow()

    override suspend fun save(target: SaveTarget) {
        // Книги не редактируются — сохранение не имеет смысла.
    }

    fun onToc(entries: List<OutlineNode>) {
        _outline.value = entries
    }

    fun onChapter(label: String) {
        _chapter.value = label
    }

    fun savePosition(fraction: Double, index: Int) {
        scope.launch {
            store.savePosition(sourceUri, fraction, index)
        }
    }

    override suspend fun close() {
        scope.cancel()
        // Кэш CBR подчищаем при закрытии сессии
        if (format == "cbr") CbrConverter.cleanupCache(appContext)
    }

    companion object {
        fun fileUriToPath(uriString: String): String? =
            runCatching { Uri.parse(uriString).path }.getOrNull()
    }
}
