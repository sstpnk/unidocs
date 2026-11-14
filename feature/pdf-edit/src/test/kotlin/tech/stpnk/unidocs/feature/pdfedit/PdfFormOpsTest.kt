package tech.stpnk.unidocs.feature.pdfedit

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tech.stpnk.unidocs.feature.pdfedit.form.FieldValue
import tech.stpnk.unidocs.feature.pdfedit.form.FormFieldKind
import tech.stpnk.unidocs.feature.pdfedit.form.PdfFormOps
import java.io.ByteArrayOutputStream

/**
 * Полный цикл редактирования AcroForm на Robolectric: PDFBoxResourceLoader
 * инициализируется контекстом, AFM-метрики читаются из assets —
 * тесты реально заполняют поля и сохраняют файл (регрессия «не открывается/не редактируется»).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfFormOpsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun newDoc(): com.tom_roush.pdfbox.pdmodel.PDDocument {
        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument()
        doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
        val acroForm = com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm(doc)
        doc.documentCatalog.acroForm = acroForm

        val text = com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField(acroForm)
        text.partialName = "FullName"
        acroForm.fields.add(text)

        val checkbox = com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox(acroForm)
        checkbox.partialName = "Agree"
        acroForm.fields.add(checkbox)

        val combo = com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox(acroForm)
        combo.partialName = "City"
        combo.setOptions(arrayListOf("Москва", "Санкт-Петербург", "Казань"))
        acroForm.fields.add(combo)

        return doc
    }

    private fun saveAndReload(doc: com.tom_roush.pdfbox.pdmodel.PDDocument): com.tom_roush.pdfbox.pdmodel.PDDocument {
        val bytes = ByteArrayOutputStream().use { stream ->
            doc.save(stream)
            stream.toByteArray()
        }
        doc.close()
        return com.tom_roush.pdfbox.pdmodel.PDDocument.load(bytes)
    }

    @Before
    fun initPdfbox() {
        PDFBoxResourceLoader.init(context)
    }

    @Test
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
        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument()
        doc.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
        assertTrue(PdfFormOps.read(doc).isEmpty())
        doc.close()
    }

    @Test
    fun `text value persists after save and reload`() {
        val doc = newDoc()
        assertTrue(PdfFormOps.apply(doc, "FullName", FieldValue.Text("Иванов Иван")))
        val reloaded = saveAndReload(doc)
        val field = PdfFormOps.read(reloaded).first { it.id == "FullName" }
        assertEquals("Иванов Иван", field.text)
        reloaded.close()
    }

    @Test
    fun `checkbox toggles and persists`() {
        val doc = newDoc()
        assertTrue(PdfFormOps.apply(doc, "Agree", FieldValue.Check(true)))
        val reloaded = saveAndReload(doc)
        val field = PdfFormOps.read(reloaded).first { it.id == "Agree" }
        assertEquals(true, field.checked)
        reloaded.close()
    }

    @Test
    fun `choice accepts known option`() {
        val doc = newDoc()
        assertTrue(PdfFormOps.apply(doc, "City", FieldValue.Choice("Казань")))
        val reloaded = saveAndReload(doc)
        val field = PdfFormOps.read(reloaded).first { it.id == "City" }
        assertTrue(field.text.orEmpty().contains("Казань"))
        reloaded.close()
    }

    @Test
    fun `unknown field id is rejected`() {
        val doc = newDoc()
        assertFalse(PdfFormOps.apply(doc, "NoSuchField", FieldValue.Text("x")))
        assertFalse(PdfFormOps.apply(doc, "NoSuchField", FieldValue.Check(true)))
        doc.close()
    }
}
