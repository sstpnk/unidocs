package tech.stpnk.unidocs.core.model

import kotlinx.coroutines.flow.Flow

/**
 * Поверхность рендера документа. Конкретный вид (Compose-композиция, Canvas, WebView)
 * определяет реализация плагина; модель лишь помечает контракт.
 */
interface DocumentSurface

/** Открытый документ: метаданные, оглавление, поиск, сохранение. */
interface DocumentSession {
    val info: DocumentInfo
    val surface: DocumentSurface
    val outline: Flow<List<OutlineNode>>

    fun search(query: String): Flow<SearchHit>

    suspend fun save(target: SaveTarget)

    suspend fun close()
}
