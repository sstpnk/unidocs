package tech.stpnk.unidocs

import android.app.Application
import tech.stpnk.unidocs.core.model.DocumentPlugin
import tech.stpnk.unidocs.feature.books.BookPlugin
import tech.stpnk.unidocs.feature.markdown.MarkdownPlugin
import tech.stpnk.unidocs.feature.markdown.TextPlugin
import tech.stpnk.unidocs.feature.office.OfficePlugin
import tech.stpnk.unidocs.feature.pdf.PdfPlugin

/**
 * Единая точка сборки реестра плагинов: порядок важен —
 * более специфичные плагины регистрируются раньше общих.
 * Вынесено для тестов роутинга форматов.
 */
internal fun createDocumentPlugins(application: Application): List<DocumentPlugin> = listOf(
    PdfPlugin(application),
    BookPlugin(application),
    OfficePlugin(application),
    MarkdownPlugin(application),
    // TextPlugin последним: text/plain не должен перехватывать .md
    TextPlugin(application),
)
