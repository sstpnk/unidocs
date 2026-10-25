package tech.stpnk.unidocs.feature.pdf

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import tech.stpnk.unidocs.core.model.ByteSequence
import tech.stpnk.unidocs.core.model.DocumentSession
import tech.stpnk.unidocs.core.model.OpenContext
import androidx.pdf.SandboxedPdfLoader

/** Плагин PDF: основной движок — androidx.pdf (sandbox-сервис), fallback — PdfRenderer. */
class PdfPlugin(private val appContext: Context) : tech.stpnk.unidocs.core.model.DocumentPlugin {

    override val id: String = "pdf"
    override val displayName: String = "PDF"

    override fun supports(mimeType: String?, displayName: String, head: ByteSequence): Boolean =
        PdfMatcher.supports(mimeType, displayName, head)

    override suspend fun open(context: OpenContext): DocumentSession {
        val loader = SandboxedPdfLoader(appContext, Dispatchers.IO)
        // PdfPasswordException здесь означает «файл защищён паролем» —
        // M2 показывает сообщение; ввод пароля появится вместе с M3.
        val document = loader.openDocument(Uri.parse(context.uri), null)
        return PdfSession(document, context, appContext)
    }
}
