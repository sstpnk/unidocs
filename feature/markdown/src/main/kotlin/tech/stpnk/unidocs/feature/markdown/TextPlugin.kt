package tech.stpnk.unidocs.feature.markdown

import android.content.Context
import android.net.Uri
import tech.stpnk.unidocs.core.model.ByteSequence
import tech.stpnk.unidocs.core.model.DocumentSession
import tech.stpnk.unidocs.core.model.OpenContext
import tech.stpnk.unidocs.core.storage.SafFiles
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

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
        val text = decodeText(SafFiles.readAll(appContext, Uri.parse(context.uri)))
        return MarkdownSession(context, initialText = text, appContext = appContext, plainText = true)
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.startsWith(byteArrayOf(0xFE.toByte(), 0xFF.toByte()))) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        val declaration = bytes.copyOfRange(0, bytes.size.coerceAtMost(512)).toString(Charsets.ISO_8859_1)
            .let { Regex("encoding\\s*=\\s*['\\\"]([^'\\\"]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        val declared = declaration?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() }
        val candidates = listOfNotNull(declared, Charsets.UTF_8, java.nio.charset.Charset.forName("windows-1251"))
        return candidates.firstNotNullOfOrNull { charset ->
            runCatching {
                charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            }.getOrNull()
        } ?: String(bytes, Charsets.UTF_8)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
