package tech.stpnk.unidocs.feature.office

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import tech.stpnk.unidocs.core.model.DocumentInfo
import tech.stpnk.unidocs.core.model.DocumentSession
import tech.stpnk.unidocs.core.model.DocumentSurface
import tech.stpnk.unidocs.core.model.OpenContext
import tech.stpnk.unidocs.core.model.OutlineNode
import tech.stpnk.unidocs.core.model.SaveTarget
import tech.stpnk.unidocs.core.model.SearchHit

/**
 * Сессия офисного документа (просмотр). URI для WebView:
 * SAF-файл — для docx/xlsx/pptx; кэш-HTML — для odt.
 */
class OfficeSession(
    @Suppress("UNUSED_PARAMETER") private val appContext: Context,
    private val openContext: OpenContext,
    val officeType: String,
    val mediaUri: String,
    val mediaName: String,
) : DocumentSession {

    override val info: DocumentInfo = DocumentInfo(
        title = openContext.displayName,
        mimeType = openContext.mimeType,
        sizeBytes = openContext.sizeBytes,
    )

    override val surface: DocumentSurface = object : DocumentSurface {}

    override val outline: Flow<List<OutlineNode>> = flowOf(emptyList())

    override fun search(query: String): Flow<SearchHit> = emptyFlow()

    override suspend fun save(target: SaveTarget) {
        // M5 — только просмотр; правка офисных появится в динамическом модуле позже.
        throw UnsupportedOperationException("Редактирование офисных документов пока не поддерживается")
    }

    override suspend fun close() {
        // кэш-HTML подчищается системой; намеренно ничего не делаем
    }
}
