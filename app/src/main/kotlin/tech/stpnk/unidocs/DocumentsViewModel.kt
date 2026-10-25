package tech.stpnk.unidocs

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.stpnk.unidocs.core.model.ByteSequence
import tech.stpnk.unidocs.core.model.DocumentSession
import tech.stpnk.unidocs.core.model.OpenContext
import tech.stpnk.unidocs.core.model.PluginRegistry
import tech.stpnk.unidocs.core.model.SaveTarget
import tech.stpnk.unidocs.core.storage.RecentDocument
import tech.stpnk.unidocs.core.storage.RecentsStore
import tech.stpnk.unidocs.core.storage.SafFiles
import tech.stpnk.unidocs.feature.markdown.MarkdownPlugin

/**
 * M1: файловый менеджер (SAF) + «Недавние» + открытие документов через плагины.
 * Реестр плагинов: Markdown; остальные форматы приходят в M2–M6.
 */
class DocumentsViewModel(application: Application) : AndroidViewModel(application) {

    private val recentsStore = RecentsStore(application)

    /** Порядок регистрации важен: более специфичные плагины — раньше. */
    private val registry = PluginRegistry(
        listOf(
            MarkdownPlugin(application),
        ),
    )

    val recents: StateFlow<List<RecentDocument>> = recentsStore.recents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _session = MutableStateFlow<DocumentSession?>(null)
    val session: StateFlow<DocumentSession?> = _session.asStateFlow()

    private var currentUri: String? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    fun onDocumentPicked(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                // Права на чтение и запись, переживающие перезапуск процесса.
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }

                val meta = SafFiles.queryMeta(context, uri)
                val head = SafFiles.readHead(context, uri)
                val plugin = registry.findFor(meta.mimeType, meta.displayName, ByteSequence(head))

                recentsStore.upsert(
                    RecentDocument(
                        uri = meta.uri,
                        name = meta.displayName,
                        mimeType = meta.mimeType,
                        sizeBytes = meta.sizeBytes,
                        openedAtEpochMs = System.currentTimeMillis(),
                    ),
                )

                if (plugin == null) {
                    _messages.emit("Плагин для «${meta.displayName}» появится на этапах M2–M6")
                    return@launch
                }

                val opened = plugin.open(
                    OpenContext(
                        uri = meta.uri,
                        displayName = meta.displayName,
                        mimeType = meta.mimeType,
                        sizeBytes = meta.sizeBytes,
                        sniff = { ByteSequence(SafFiles.readHead(context, uri)) },
                    ),
                )
                currentUri = meta.uri
                _session.value = opened
            } catch (e: SecurityException) {
                recentsStore.remove(uri.toString())
                _messages.emit("Нет доступа к документу — запись удалена из «Недавних»")
            } catch (e: Exception) {
                _messages.emit("Не удалось открыть: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun saveSession(onResult: (Boolean) -> Unit) {
        val target = _session.value
        val uri = currentUri
        if (target == null || uri == null) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val result = runCatching { target.save(SaveTarget(uri, inPlace = true)) }
            val ok = result.isSuccess
            _messages.emit(
                if (ok) {
                    "Сохранено"
                } else {
                    "Не удалось сохранить: ${result.exceptionOrNull()?.message ?: "ошибка"}"
                },
            )
            onResult(ok)
        }
    }

    fun closeSession() {
        val session = _session.value
        _session.value = null
        currentUri = null
        viewModelScope.launch { session?.close() }
    }

    fun removeRecent(uri: String) {
        viewModelScope.launch { recentsStore.remove(uri) }
    }

    fun clearRecents() {
        viewModelScope.launch { recentsStore.clear() }
    }
}
