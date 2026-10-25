package tech.stpnk.unidocs.core.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/** Чтение метаданных и «головы» документа через SAF. */
object SafFiles {

    data class DocumentMeta(
        val uri: String,
        val displayName: String,
        val mimeType: String?,
        val sizeBytes: Long?,
    )

    fun queryMeta(context: Context, uri: Uri): DocumentMeta {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment ?: "document"
        var size: Long? = null

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    name = cursor.getString(nameIndex)
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        return DocumentMeta(
            uri = uri.toString(),
            displayName = name,
            mimeType = resolver.getType(uri),
            sizeBytes = size,
        )
    }

    /** Первые [limit] байт для сниффинга формата. */
    suspend fun readHead(context: Context, uri: Uri, limit: Int = 4096): ByteArray {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(limit)
            var read = 0
            while (read < limit) {
                val chunk = input.read(buffer, read, limit - read)
                if (chunk <= 0) break
                read += chunk
            }
            buffer.copyOf(read)
        }
        return bytes ?: ByteArray(0)
    }
}
