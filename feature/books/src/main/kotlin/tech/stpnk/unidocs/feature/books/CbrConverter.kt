package tech.stpnk.unidocs.feature.books

import android.content.Context
import android.net.Uri
import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * CBR (rar-архив картинок) → CBZ (zip): foliate-js читает только zip.
 * Распаковываем junrar во временный каталог и собираем zip в кэше.
 */
object CbrConverter {

    suspend fun toCbz(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val cacheDir = File(context.cacheDir, "cbr").apply { mkdirs() }
            val workDir = File(cacheDir, "work-${System.currentTimeMillis()}")
            workDir.mkdirs()

            context.contentResolver.openInputStream(uri)?.use { input ->
                Archive(input).use { archive ->
                    archive.fileHeaders.forEach { header ->
                        if (header.isDirectory) return@forEach
                        val target = File(workDir, header.fileName.replace('\\', '/'))
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out -> archive.extractFile(header, out) }
                    }
                }
            } ?: return@runCatching null

            val images = workDir.walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.absolutePath.lowercase() }
                .toList()
            if (images.isEmpty()) return@runCatching null

            val outFile = File(cacheDir, "book-${System.currentTimeMillis()}.cbz")
            ZipOutputStream(FileOutputStream(outFile)).use { zip ->
                images.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(workDir).path.replace('\\', '/')))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            workDir.deleteRecursively()
            outFile
        }.getOrNull()
    }

    fun cleanupCache(context: Context) {
        File(context.cacheDir, "cbr").deleteRecursively()
    }
}
