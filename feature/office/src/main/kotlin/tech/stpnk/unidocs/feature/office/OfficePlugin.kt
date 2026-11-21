package tech.stpnk.unidocs.feature.office

import android.content.Context
import android.net.Uri
import tech.stpnk.unidocs.core.model.ByteSequence
import tech.stpnk.unidocs.core.model.DocumentSession
import tech.stpnk.unidocs.core.model.OpenContext
import java.io.File
import java.io.IOException

/**
 * Плагин офисных форматов: docx/xlsx/pptx через JS-движки в изолированном WebView,
 * odt/ods/rtf — через собственные конвертеры (RtfToHtml / OdtToHtml).
 */
class OfficePlugin(private val appContext: Context) : tech.stpnk.unidocs.core.model.DocumentPlugin {

    override val id: String = "office"
    override val displayName: String = "Документы"

    override fun supports(mimeType: String?, displayName: String, head: ByteSequence): Boolean =
        OfficeMatcher.supports(mimeType, displayName, head)

    override suspend fun open(context: OpenContext): DocumentSession {
        val head = context.sniff()
        val type = OfficeMatcher.typeFor(context.mimeType, context.displayName, head)
            ?: throw IOException("Не удалось определить формат офисного документа")

        // Конвертируемые форматы (odt/rtf) заранее превращаем в HTML в кэше
        var mediaUri = context.uri
        var mediaName = "office.$type"
        if (type == "odt") {
            val html = OdtToHtml.convert(appContext, Uri.parse(context.uri))
            val outFile = cacheHtml("odt", html)
            mediaUri = Uri.fromFile(outFile).toString()
            mediaName = "odt.html"
        } else if (type == "rtf") {
            val bytes = tech.stpnk.unidocs.core.storage.SafFiles.readAll(appContext, Uri.parse(context.uri))
            val html = RtfToHtml.convert(bytes)
            val outFile = cacheHtml("rtf", html)
            mediaUri = Uri.fromFile(outFile).toString()
            mediaName = "rtf.html"
        }

        return OfficeSession(
            appContext = appContext,
            openContext = context,
            officeType = type,
            mediaUri = mediaUri,
            mediaName = mediaName,
        )
    }

    private fun cacheHtml(prefix: String, html: String): File {
        val dir = File(appContext.cacheDir, "office").apply { mkdirs() }
        return File(dir, "$prefix-${System.currentTimeMillis()}.html").apply {
            writeText(html, Charsets.UTF_8)
        }
    }
}
