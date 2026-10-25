package tech.stpnk.unidocs.feature.pdfedit.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.IOException

/**
 * Fallback-движок на системном PdfRenderer: без зависимостей, рендер по запросу.
 * Используется fallback-просмотрщиком PDF и предпросмотром страницы
 * при позиционировании факсимиле.
 */
class PdfRendererEngine(context: Context, uri: Uri) : AutoCloseable {

    private val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
    private var renderer: PdfRenderer? = null

    fun open() {
        check(renderer == null) { "Движок уже открыт" }
        val descriptor = pfd ?: throw IOException("Не удалось открыть файл через SAF")
        renderer = PdfRenderer(descriptor)
    }

    val pageCount: Int
        get() = renderer?.pageCount ?: 0

    @Synchronized
    fun renderPage(index: Int, targetWidthPx: Int): Bitmap {
        val r = checkNotNull(renderer) { "Движок не открыт" }
        require(index in 0 until r.pageCount) { "Страница $index вне диапазона" }
        r.openPage(index).use { page ->
            val scale = targetWidthPx.toFloat() / page.width
            val height = (page.height * scale).toInt().coerceIn(1, 8192)
            val bitmap = Bitmap.createBitmap(targetWidthPx, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            val matrix = Matrix().apply { setScale(scale, scale) }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }

    override fun close() {
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
        renderer = null
    }
}
