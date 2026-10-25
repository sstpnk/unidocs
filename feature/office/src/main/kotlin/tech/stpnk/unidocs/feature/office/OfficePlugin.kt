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
 * odt/ods — через собственный конвертер / SheetJS.
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

        // ODT конвертируется в HTML заранее (файл в кэше отдаётся через /media/odt.html)
        var mediaUri = context.uri
        var mediaName = "office.${type}"
        if (type == "odt") {
            val html = OdtToHtml.convert(appContext, Uri.parse(context.uri))
            val dir = File(appContext.cacheDir, "office").apply { mkdirs() }
            val outFile = File(dir, "odt-${System.currentTimeMillis()}.html")
            outFile.writeText(html, Charsets.UTF_8)
            mediaUri = Uri.fromFile(outFile).toString()
            mediaName = "odt.html"
        }

        return OfficeSession(
            appContext = appContext,
            openContext = context,
            officeType = type,
            mediaUri = mediaUri,
            mediaName = mediaName,
        )
    }
}
