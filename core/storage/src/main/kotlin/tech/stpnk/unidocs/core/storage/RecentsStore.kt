package tech.stpnk.unidocs.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.unidocsDataStore: DataStore<Preferences> by preferencesDataStore(name = "unidocs")

/**
 * «Недавние» документы: Dedup по uri, сортировка по времени открытия, ограничение списка.
 * Хранение — Preferences DataStore, записи — JSON (kotlinx.serialization).
 */
class RecentsStore(
    context: Context,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {

    private val dataStore = context.applicationContext.unidocsDataStore
    private val json = Json { ignoreUnknownKeys = true }

    fun recents(): Flow<List<RecentDocument>> = dataStore.data.map { prefs ->
        prefs[KEY_RECENTS]
            ?.let { runCatching { json.decodeFromString(ListSerializer(RecentDocument.serializer()), it) }.getOrDefault(emptyList()) }
            .orEmpty()
            .sortedByDescending { it.openedAtEpochMs }
    }

    suspend fun upsert(document: RecentDocument) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_RECENTS]
                ?.let { runCatching { json.decodeFromString(ListSerializer(RecentDocument.serializer()), it) }.getOrDefault(emptyList()) }
                .orEmpty()
            val merged = (listOf(document) + current.filterNot { it.uri == document.uri })
                .sortedByDescending { it.openedAtEpochMs }
                .take(maxEntries)
            prefs[KEY_RECENTS] = json.encodeToString(ListSerializer(RecentDocument.serializer()), merged)
        }
    }

    suspend fun remove(uri: String) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_RECENTS]
                ?.let { runCatching { json.decodeFromString(ListSerializer(RecentDocument.serializer()), it) }.getOrDefault(emptyList()) }
                .orEmpty()
            prefs[KEY_RECENTS] = json.encodeToString(
                ListSerializer(RecentDocument.serializer()),
                current.filterNot { it.uri == uri },
            )
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY_RECENTS) }
    }

    suspend fun current(): List<RecentDocument> = recents().first()

    private companion object {
        val KEY_RECENTS = stringPreferencesKey("recents")
        const val DEFAULT_MAX_ENTRIES = 30
    }
}
