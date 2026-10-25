package tech.stpnk.unidocs

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tech.stpnk.unidocs.core.model.ByteSequence
import tech.stpnk.unidocs.core.model.PluginRegistry
import tech.stpnk.unidocs.core.storage.RecentDocument
import tech.stpnk.unidocs.core.storage.RecentsStore
import tech.stpnk.unidocs.core.storage.SafFiles

/**
 * M0: файловый менеджер на SAF + «Недавние» + плагинный SPI.
 * Реестр плагинов пока пуст — первые плагины (Markdown, PDF) приходят в M1–M2.
 */
class DocumentsViewModel(application: Application) : AndroidViewModel(application) {

    private val recentsStore = RecentsStore(application)

    /** Целевой SPI-реестр; в M0 плагинов ещё нет. */
    private val registry = PluginRegistry(emptyList())

    val recents: StateFlow<List<RecentDocument>> = recentsStore.recents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    fun onDocumentPicked(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                // Право на повторное открытие после перезагрузки (для «Недавних»).
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
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

                _messages.emit(
                    when {
                        plugin != null -> "Открываем через плагин «${plugin.displayName}»…"
                        else -> "Плагин для «${meta.displayName}» появится на этапах M1–M5"
                    },
                )
            } catch (e: SecurityException) {
                recentsStore.remove(uri.toString())
                _messages.emit("Нет доступа к документу — запись удалена из «Недавних»")
            } catch (e: Exception) {
                _messages.emit("Не удалось открыть: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun removeRecent(uri: String) {
        viewModelScope.launch { recentsStore.remove(uri) }
    }

    fun clearRecents() {
        viewModelScope.launch { recentsStore.clear() }
    }
}
