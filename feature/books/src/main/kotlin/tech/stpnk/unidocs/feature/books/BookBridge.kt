package tech.stpnk.unidocs.feature.books

import android.webkit.JavascriptInterface
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RelocateDetail(
    val fraction: Double = 0.0,
    val index: Int = 0,
    val chapter: String = "",
    val page: String = "",
)

@Serializable
data class TocEntry(
    val label: String = "",
    val href: String = "",
    val level: Int = 0,
)

@Serializable
data class SearchItem(
    val cfi: String = "",
    val pre: String = "",
    val match: String = "",
    val post: String = "",
)

@Serializable
data class SearchEvent(
    val done: Boolean = false,
    val progress: Double? = null,
    val error: String? = null,
    val label: String? = null,
    val subitems: List<SearchItem> = emptyList(),
    val cfi: String? = null,
    val pre: String? = null,
    val match: String? = null,
    val post: String? = null,
)

@Serializable
data class SelectionDetail(
    val cfi: String = "",
    val text: String = "",
)

/**
 * JS-мост foliate-js → Kotlin. Методы вызываются на потоке WebView,
 * поэтому экран обязан перепрыгивать на main для изменения состояния.
 */
class BookBridge(private val callbacks: Callbacks) {

    interface Callbacks {
        fun onPageReady()
        fun onLoaded()
        fun onRelocate(detail: RelocateDetail)
        fun onToc(entries: List<TocEntry>)
        fun onSearch(event: SearchEvent)
        fun onSelection(detail: SelectionDetail)
        fun onAnnotationTapped(cfi: String)
        fun onError(message: String)
    }

    private val json = Json { ignoreUnknownKeys = true }

    @JavascriptInterface
    fun onPageReady() = callbacks.onPageReady()

    @JavascriptInterface
    fun onLoaded() = callbacks.onLoaded()

    @JavascriptInterface
    fun onRelocate(payload: String) {
        val detail = runCatching { json.decodeFromString<RelocateDetail>(payload) }.getOrNull()
            ?: RelocateDetail()
        callbacks.onRelocate(detail)
    }

    @JavascriptInterface
    fun onToc(payload: String) {
        val entries = runCatching { json.decodeFromString<List<TocEntry>>(payload) }.getOrDefault(emptyList())
        callbacks.onToc(entries)
    }

    @JavascriptInterface
    fun onSearch(payload: String) {
        val event = runCatching { json.decodeFromString<SearchEvent>(payload) }.getOrNull() ?: return
        callbacks.onSearch(event)
    }

    @JavascriptInterface
    fun onSelection(payload: String) {
        val detail = runCatching { json.decodeFromString<SelectionDetail>(payload) }.getOrNull() ?: return
        callbacks.onSelection(detail)
    }

    @JavascriptInterface
    fun onAnnotationTapped(cfi: String) {
        callbacks.onAnnotationTapped(cfi)
    }

    @JavascriptInterface
    fun onError(message: String) = callbacks.onError(message)
}
