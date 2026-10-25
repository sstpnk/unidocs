package tech.stpnk.unidocs.feature.books

import android.content.Context
import android.net.Uri
import tech.stpnk.unidocs.core.model.ByteSequence
import tech.stpnk.unidocs.core.model.DocumentSession
import tech.stpnk.unidocs.core.model.OpenContext

/**
 * Плагин книг: epub/fb2/mobi/azw3/cbz — через foliate-js; cbr — конвертация в cbz.
 * Открытие возвращает [BookSession]; файл раздаётся в WebView через AssetLoader.
 */
class BookPlugin(private val appContext: Context) : tech.stpnk.unidocs.core.model.DocumentPlugin {

    override val id: String = "books"
    override val displayName: String = "Книги"

    override fun supports(mimeType: String?, displayName: String, head: ByteSequence): Boolean =
        BookMatcher.supports(mimeType, displayName, head)

    override suspend fun open(context: OpenContext): DocumentSession {
        val ext = context.displayName.substringAfterLast('.', "").lowercase()
        var sourceUri = context.uri
        var label = context.displayName

        // CBR: конвертируем в CBZ во временном кэше, читатель получает zip
        if (ext == "cbr") {
            val cbz = CbrConverter.toCbz(appContext, Uri.parse(context.uri))
                ?: throw IllegalStateException("Не удалось распаковать CBR")
            sourceUri = Uri.fromFile(cbz).toString()
            label = label.removeSuffix(".cbr") + ".cbz"
        }

        return BookSession(
            appContext = appContext,
            openContext = context.copy(uri = sourceUri, displayName = label),
            sourceUri = context.uri,
            format = ext.ifEmpty { "book" },
        )
    }
}
