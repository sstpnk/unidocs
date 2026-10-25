package tech.stpnk.unidocs.feature.pdfedit

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import tech.stpnk.unidocs.feature.pdfedit.form.FieldValue
import tech.stpnk.unidocs.feature.pdfedit.form.FormFieldKind
import tech.stpnk.unidocs.feature.pdfedit.form.PdfFormOps
import java.io.ByteArrayOutputStream

/**
 * JVM-тесты операций AcroForm.
 *
 * ОГРАНИЧЕНИЕ: pdfbox-android читает метрики стандартных шрифтов (AFM) из Android-assets
 * через PDFBoxResourceLoader — на JVM их нет, и любая генерация внешности поля
 * (setValue/check) падает. Поэтому операции с шрифтами помечены @Ignore: логика
 * AcroForm проверяется на устройстве (сборка M3). Чистые JVM-тесты остаются.
 */
class PdfFormOpsTest {

    private fun newDoc(): PDDocument {
        val doc = PDDocument()
        doc.addPage(PDPage())
        val acroForm = PDAcroForm(doc)
        doc.documentCatalog.acroForm = acroForm

        val text = PDTextField(acroForm)
        text.partialName = "FullName"
        acroForm.fields.add(text)

        val checkbox = PDCheckBox(acroForm)
        checkbox.partialName = "Agree"
        acroForm.fields.add(checkbox)

        val combo = PDComboBox(acroForm)
        combo.partialName = "City"
        combo.setOptions(arrayListOf("Москва", "Санкт-Петербург", "Казань"))
        acroForm.fields.add(combo)

        return doc
    }

    private fun saveAndReload(doc: PDDocument): PDDocument {
        val bytes = ByteArrayOutputStream().use { stream ->
            doc.save(stream)
            stream.toByteArray()
        }
        doc.close()
        return PDDocument.load(bytes)
    }

    @Test
    @Ignore("Требует Android-контекста: AFM-метрики грузятся из assets (см. класс-комментарий)")
    fun `read maps text checkbox and choice fields`() {
        val fields = PdfFormOps.read(newDoc())
        assertEquals(3, fields.size)
        assertEquals(FormFieldKind.TEXT, fields[0].kind)
        assertEquals("FullName", fields[0].id)
        assertEquals(FormFieldKind.CHECKBOX, fields[1].kind)
        assertEquals("Agree", fields[1].id)
        assertEquals(FormFieldKind.CHOICE, fields[2].kind)
        assertEquals(3, fields[2].options.size)
    }

    @Test
    fun `empty document without acroform reads as empty list`() {
        val doc = PDDocument()
        doc.addPage(PDPage())
        assertTrue(PdfFormOps.read(doc).isEmpty())
        doc.close()
    }

    @Test
    @Ignore("Требует Android-контекста (см. класс-комментарий)")
    fun `text value persists after save and reload`() {
        val doc = newDoc()
        assertTrue(PdfFormOps.apply(doc, "FullName", FieldValue.Text("Иванов Иван")))
        val reloaded = saveAndReload(doc)
        val field = PdfFormOps.read(reloaded).first { it.id == "FullName" }
        assertEquals("Иванов Иван", field.text)
        reloaded.close()
    }

    @Test
    @Ignore("Требует Android-контекста (см. класс-комментарий)")
    fun `checkbox toggles and persists`() {
        val doc = newDoc()
        assertTrue(PdfFormOps.apply(doc, "Agree", FieldValue.Check(true)))
        val reloaded = saveAndReload(doc)
        val field = PdfFormOps.read(reloaded).first { it.id == "Agree" }
        assertEquals(true, field.checked)
        reloaded.close()
    }

    @Test
    @Ignore("Требует Android-контекста (см. класс-комментарий)")
    fun `choice accepts known option`() {
        val doc = newDoc()
        assertTrue(PdfFormOps.apply(doc, "City", FieldValue.Choice("Казань")))
        val reloaded = saveAndReload(doc)
        val field = PdfFormOps.read(reloaded).first { it.id == "City" }
        assertTrue(field.text.orEmpty().contains("Казань"))
        reloaded.close()
    }

    @Test
    @Ignore("Требует Android-контекста: getAcroForm запускает fixup со шрифтами (см. класс-комментарий)")
    fun `unknown field id is rejected`() {
        val doc = newDoc()
        assertFalse(PdfFormOps.apply(doc, "NoSuchField", FieldValue.Text("x")))
        assertFalse(PdfFormOps.apply(doc, "NoSuchField", FieldValue.Check(true)))
        doc.close()
    }
}
