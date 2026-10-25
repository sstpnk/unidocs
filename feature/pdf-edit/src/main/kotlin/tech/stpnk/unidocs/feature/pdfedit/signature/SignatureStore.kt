package tech.stpnk.unidocs.feature.pdfedit.signature

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.signaturesDataStore: DataStore<Preferences> by preferencesDataStore(name = "pdf_signatures")

@Serializable
data class SavedSignature(
    val pngBase64: String,
    val createdAtEpochMs: Long,
)

/**
 * Локальное хранилище факсимиле (PNG в base64, DataStore).
 * Приватность: подписи не покидают устройство, максимум [maxEntries].
 */
class SignatureStore(context: Context, private val maxEntries: Int = 5) {

    private val dataStore = context.applicationContext.signaturesDataStore
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedSignature.serializer())

    fun all(): Flow<List<SavedSignature>> = dataStore.data.map { prefs ->
        prefs[KEY_SIGNATURES]
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptyList()) }
            .orEmpty()
            .sortedByDescending { it.createdAtEpochMs }
    }

    suspend fun add(pngBytes: ByteArray) {
        val item = SavedSignature(
            pngBase64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP),
            createdAtEpochMs = System.currentTimeMillis(),
        )
        dataStore.edit { prefs ->
            val current = prefs[KEY_SIGNATURES]
                ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptyList()) }
                .orEmpty()
            val merged = (listOf(item) + current).take(maxEntries)
            prefs[KEY_SIGNATURES] = json.encodeToString(serializer, merged)
        }
    }

    suspend fun remove(item: SavedSignature) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_SIGNATURES]
                ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrDefault(emptyList()) }
                .orEmpty()
                .filterNot { it.createdAtEpochMs == item.createdAtEpochMs && it.pngBase64 == item.pngBase64 }
            prefs[KEY_SIGNATURES] = json.encodeToString(serializer, current)
        }
    }

    fun decodePng(item: SavedSignature): ByteArray = Base64.decode(item.pngBase64, Base64.DEFAULT)

    private companion object {
        val KEY_SIGNATURES = stringPreferencesKey("signatures")
    }
}
