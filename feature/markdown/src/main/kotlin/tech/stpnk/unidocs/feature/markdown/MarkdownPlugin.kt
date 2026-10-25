package tech.stpnk.unidocs.feature.markdown

import android.content.Context
import android.net.Uri
import tech.stpnk.unidocs.core.model.ByteSequence
import tech.stpnk.unidocs.core.model.DocumentSession
import tech.stpnk.unidocs.core.model.OpenContext
import tech.stpnk.unidocs.core.storage.SafFiles

/** Плагин Markdown: открывает .md/.markdown и text/markdown. */
class MarkdownPlugin(private val appContext: Context) : tech.stpnk.unidocs.core.model.DocumentPlugin {

    override val id: String = "markdown"
    override val displayName: String = "Markdown"

    private val extensions = setOf("md", "markdown", "mkd", "mdown", "mkdn", "mdtxt", "mdtext")
    private val mimes = setOf("text/markdown", "text/x-markdown")

    override fun supports(mimeType: String?, displayName: String, head: ByteSequence): Boolean {
        val mime = mimeType?.lowercase()
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return (mime != null && mime in mimes) || ext in extensions
    }

    override suspend fun open(context: OpenContext): DocumentSession {
        val text = SafFiles.readAll(appContext, Uri.parse(context.uri)).toString(Charsets.UTF_8)
        return MarkdownSession(context, initialText = text, appContext = appContext)
    }
}
