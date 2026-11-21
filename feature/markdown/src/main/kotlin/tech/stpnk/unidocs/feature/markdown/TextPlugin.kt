package tech.stpnk.unidocs.feature.markdown

import android.content.Context
import android.net.Uri
import tech.stpnk.unidocs.core.model.ByteSequence
import tech.stpnk.unidocs.core.model.DocumentSession
import tech.stpnk.unidocs.core.model.OpenContext
import tech.stpnk.unidocs.core.storage.SafFiles

/**
 * Плагин простого текста (txt/log/csv): тот же движок, что у Markdown,
 * но без парсинга и предпросмотра — чистый редактор с сохранением.
 * Не перехватывает .md (они у MarkdownPlugin).
 */
class TextPlugin(private val appContext: Context) : tech.stpnk.unidocs.core.model.DocumentPlugin {

    override val id: String = "text"
    override val displayName: String = "Текст"

    private val markdownExtensions = setOf("md", "markdown", "mkd", "mdown", "mkdn", "mdtxt", "mdtext")
    private val extensions = setOf("txt", "log", "csv")
    private val mimes = setOf("text/plain")

    override fun supports(mimeType: String?, displayName: String, head: ByteSequence): Boolean {
        val mime = mimeType?.lowercase()
        val ext = displayName.substringAfterLast('.', "").lowercase()
        if (ext in markdownExtensions) return false // .md всегда у MarkdownPlugin
        return (mime != null && mime in mimes && ext !in markdownExtensions) || ext in extensions
    }

    override suspend fun open(context: OpenContext): DocumentSession {
        val text = SafFiles.readAll(appContext, Uri.parse(context.uri)).toString(Charsets.UTF_8)
        return MarkdownSession(context, initialText = text, appContext = appContext, plainText = true)
    }
}
