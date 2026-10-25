package tech.stpnk.unidocs.feature.books

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.booksDataStore: DataStore<Preferences> by preferencesDataStore(name = "books_store")

@Serializable
data class ReadingPosition(
    val fraction: Double,
    val index: Int,
    val updatedAtEpochMs: Long,
)

@Serializable
data class Bookmark(
    val label: String,
    val fraction: Double,
    val index: Int,
    val createdAtEpochMs: Long,
)

/**
 * Хранилище позиций чтения и закладок книг: uri → данные.
 * Всё локально, в Preferences DataStore (JSON).
 */
class BooksStore(context: Context) {

    private val dataStore = context.applicationContext.booksDataStore
    private val json = Json { ignoreUnknownKeys = true }
    private val positionsSerializer = MapSerializer(String.serializer(), ReadingPosition.serializer())
    private val bookmarksSerializer = MapSerializer(String.serializer(), ListSerializer(Bookmark.serializer()))

    fun position(uri: String): Flow<ReadingPosition?> = dataStore.data.map { prefs ->
        prefs[KEY_POSITIONS]?.let { raw ->
            runCatching { json.decodeFromString(positionsSerializer, raw)[uri] }.getOrNull()
        }
    }

    suspend fun currentPosition(uri: String): ReadingPosition? = position(uri).first()

    suspend fun savePosition(uri: String, fraction: Double, index: Int) {
        dataStore.edit { prefs ->
            val map = prefs[KEY_POSITIONS]
                ?.let { runCatching { json.decodeFromString(positionsSerializer, it) }.getOrDefault(emptyMap()) }
                .orEmpty()
                .toMutableMap()
            map[uri] = ReadingPosition(fraction, index, System.currentTimeMillis())
            prefs[KEY_POSITIONS] = json.encodeToString(positionsSerializer, map)
        }
    }

    fun bookmarks(uri: String): Flow<List<Bookmark>> = dataStore.data.map { prefs ->
        prefs[KEY_BOOKMARKS]?.let { raw ->
            runCatching { json.decodeFromString(bookmarksSerializer, raw)[uri] }.getOrNull()
        }.orEmpty()
    }

    suspend fun addBookmark(uri: String, bookmark: Bookmark) {
        dataStore.edit { prefs ->
            val map = prefs[KEY_BOOKMARKS]
                ?.let { runCatching { json.decodeFromString(bookmarksSerializer, it) }.getOrDefault(emptyMap()) }
                .orEmpty()
                .toMutableMap()
            val list = (map[uri].orEmpty() + bookmark)
                .sortedByDescending { it.createdAtEpochMs }
                .distinctBy { "${it.index}:${it.fraction}" }
            map[uri] = list
            prefs[KEY_BOOKMARKS] = json.encodeToString(bookmarksSerializer, map)
        }
    }

    suspend fun removeBookmark(uri: String, bookmark: Bookmark) {
        dataStore.edit { prefs ->
            val map = prefs[KEY_BOOKMARKS]
                ?.let { runCatching { json.decodeFromString(bookmarksSerializer, it) }.getOrDefault(emptyMap()) }
                .orEmpty()
                .toMutableMap()
            map[uri] = map[uri].orEmpty().filterNot {
                it.createdAtEpochMs == bookmark.createdAtEpochMs && it.fraction == bookmark.fraction
            }
            prefs[KEY_BOOKMARKS] = json.encodeToString(bookmarksSerializer, map)
        }
    }

    private companion object {
        val KEY_POSITIONS = stringPreferencesKey("positions")
        val KEY_BOOKMARKS = stringPreferencesKey("bookmarks")
    }
}
