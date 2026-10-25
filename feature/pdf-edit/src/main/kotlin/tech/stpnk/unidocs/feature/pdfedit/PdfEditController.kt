package tech.stpnk.unidocs.feature.pdfedit

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.stpnk.unidocs.core.storage.SafFiles
import tech.stpnk.unidocs.feature.pdfedit.form.FieldValue
import tech.stpnk.unidocs.feature.pdfedit.form.FormFieldUi
import tech.stpnk.unidocs.feature.pdfedit.form.PdfFormOps
import java.io.IOException

/**
 * Контроллер редактирования PDF (M3): AcroForm-поля и штамп-факсимиле поверх копии
 * документа в памяти; сохранение — перезапись исходного файла через SAF.
 * Живёт в экране редактора, закрывается вместе с ним.
 */
class PdfEditController(private val appContext: Context) {

    private var document: PDDocument? = null
    private var sourceUri: String? = null

    var pageCount: Int = 0
        private set

    var dirty: Boolean = false
        private set

    suspend fun open(uriString: String): List<FormFieldUi> = withContext(Dispatchers.IO) {
        PdfBoxRuntime.ensureInitialized(appContext)
        closeInternal()
        val bytes = SafFiles.readAll(appContext, Uri.parse(uriString))
        val doc = PDDocument.load(bytes)
        document = doc
        sourceUri = uriString
        pageCount = doc.numberOfPages
        PdfFormOps.read(doc)
    }

    suspend fun applyValue(id: String, value: FieldValue): Boolean = withContext(Dispatchers.IO) {
        val doc = document ?: return@withContext false
        val ok = PdfFormOps.apply(doc, id, value)
        if (ok) dirty = true
        ok
    }

    /** Актуальный список полей (после правок). */
    suspend fun fieldsSnapshot(): List<FormFieldUi> = withContext(Dispatchers.IO) {
        document?.let { PdfFormOps.read(it) } ?: emptyList()
    }

    /**
     * Наложить PNG-факсимиле на страницу. Позиция задаётся в долях страницы (0..1),
     * отсчёт — от левого верхнего угла, как видит пользователь.
     */
    suspend fun stampSignature(
        pageIndex: Int,
        centerXPct: Float,
        centerYPct: Float,
        widthPct: Float,
        pngBytes: ByteArray,
    ): Boolean = withContext(Dispatchers.IO) {
        val doc = document ?: return@withContext false
        if (pageIndex !in 0 until doc.numberOfPages) return@withContext false
        runCatching {
            val page = doc.getPage(pageIndex)
            val image = PDImageXObject.createFromByteArray(doc, pngBytes, "signature.png")
            val box = page.mediaBox
            val width = box.width * widthPct.coerceIn(0.05f, 0.9f)
            val height = width * image.height.toFloat() / image.width.toFloat()
            val x = (box.width * centerXPct - width / 2f).coerceIn(0f, (box.width - width).coerceAtLeast(0f))
            val y = (box.height * (1f - centerYPct) - height / 2f)
                .coerceIn(0f, (box.height - height).coerceAtLeast(0f))
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                stream.drawImage(image, x, y, width, height)
            }
            dirty = true
            true
        }.getOrDefault(false)
    }

    suspend fun saveTo(uriString: String = sourceUri ?: "", flatten: Boolean): Unit = withContext(Dispatchers.IO) {
        val doc = document ?: throw IOException("Документ не открыт")
        if (uriString.isBlank()) throw IOException("Нет файла для сохранения")
        if (flatten) PdfFormOps.flatten(doc)
        val output = appContext.contentResolver.openOutputStream(Uri.parse(uriString), "wt")
            ?: throw IOException("Провайдер не даёт записать в этот файл")
        output.use { stream -> doc.save(stream) }
        dirty = false
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        closeInternal()
    }

    private fun closeInternal() {
        runCatching { document?.close() }
        document = null
        sourceUri = null
        pageCount = 0
        dirty = false
    }
}

/** Однократная инициализация PDFBox (загрузка шрифтовых ресурсов Android). */
private object PdfBoxRuntime {
    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                initialized = true
            }
        }
    }
}
