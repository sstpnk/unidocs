package tech.stpnk.unidocs.feature.markdown

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tech.stpnk.unidocs.core.model.DocumentInfo
import tech.stpnk.unidocs.core.model.DocumentSession
import tech.stpnk.unidocs.core.model.DocumentSurface
import tech.stpnk.unidocs.core.model.OpenContext
import tech.stpnk.unidocs.core.model.SaveTarget
import tech.stpnk.unidocs.core.model.SearchHit
import tech.stpnk.unidocs.feature.markdown.parser.MdBlock
import tech.stpnk.unidocs.feature.markdown.parser.MarkdownParser
import java.io.IOException

/**
 * Сессия Markdown-документа: единый источник правды текста (_text),
 * из которого пересчитываются блоки предпросмотра и оглавление.
 */
class MarkdownSession(
    private val openContext: OpenContext,
    initialText: String,
    appContext: Context,
) : DocumentSession {

    private val resolver = appContext.contentResolver
    private val uri = Uri.parse(openContext.uri)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _text = MutableStateFlow(initialText)

    val text: StateFlow<String> = _text.asStateFlow()

    val blocks: StateFlow<List<MdBlock>> = _text
        .map { MarkdownParser.parse(it) }
        .stateIn(scope, SharingStarted.Eagerly, MarkdownParser.parse(initialText))

    override val info: DocumentInfo = DocumentInfo(
        title = openContext.displayName,
        mimeType = openContext.mimeType,
        sizeBytes = openContext.sizeBytes,
    )

    override val surface: DocumentSurface = object : DocumentSurface {}

    override val outline = blocks.map { MarkdownParser.outline(it) }

    fun updateText(value: String) {
        _text.value = value
    }

    override fun search(query: String): Flow<SearchHit> = flow {
        if (query.isBlank()) return@flow
        val haystack = _text.value
        val lower = haystack.lowercase()
        val needle = query.lowercase()
        var index = 0
        while (true) {
            val found = lower.indexOf(needle, index)
            if (found < 0) break
            val from = maxOf(0, found - 30)
            val to = minOf(haystack.length, found + needle.length + 30)
            emit(
                SearchHit(
                    page = null,
                    snippet = haystack.substring(from, to).replace('\n', ' '),
                    charOffset = found,
                ),
            )
            index = found + needle.length
        }
    }

    override suspend fun save(target: SaveTarget) {
        val stream = resolver.openOutputStream(uri, "wt")
            ?: throw IOException("Провайдер не даёт записать в этот файл")
        stream.use { it.write(_text.value.toByteArray(Charsets.UTF_8)) }
    }

    override suspend fun close() {
        scope.cancel()
    }
}
