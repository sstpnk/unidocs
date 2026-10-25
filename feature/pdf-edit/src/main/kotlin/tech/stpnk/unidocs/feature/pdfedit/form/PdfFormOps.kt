package tech.stpnk.unidocs.feature.pdfedit.form

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField

enum class FormFieldKind { TEXT, CHECKBOX, RADIO, CHOICE }

/** UI-представление одного поля AcroForm. */
data class FormFieldUi(
    val id: String,
    val label: String,
    val kind: FormFieldKind,
    val text: String? = null,
    val checked: Boolean? = null,
    val options: List<String> = emptyList(),
    val readOnly: Boolean = false,
)

/** Значение, которое пользователь хочет применить к полю. */
sealed interface FieldValue {
    data class Text(val value: String) : FieldValue
    data class Check(val checked: Boolean) : FieldValue
    data class Choice(val option: String) : FieldValue
}

/**
 * Чистые операции над AcroForm поверх PDDocument (pdfbox-android).
 * Вынесены из контроллера, чтобы тестировались на JVM.
 * Нюанс порта: класс чекбокса называется PDCheckBox (в upstream — PDCheckbox).
 */
object PdfFormOps {

    fun read(doc: PDDocument): List<FormFieldUi> {
        val acroForm = doc.documentCatalog.acroForm ?: return emptyList()
        return acroForm.fields.mapNotNull { toUi(it) }
    }

    private fun toUi(field: PDField): FormFieldUi? = when (field) {
        is PDCheckBox -> FormFieldUi(
            id = field.fullyQualifiedName,
            label = field.partialName,
            kind = FormFieldKind.CHECKBOX,
            checked = runCatching { field.isChecked }.getOrNull(),
            readOnly = field.isReadOnly,
        )

        is PDRadioButton -> FormFieldUi(
            id = field.fullyQualifiedName,
            label = field.partialName,
            kind = FormFieldKind.RADIO,
            text = runCatching { field.value }.getOrNull(),
            options = runCatching { field.onValues.toList() }.getOrDefault(emptyList()),
            readOnly = field.isReadOnly,
        )

        is PDComboBox -> FormFieldUi(
            id = field.fullyQualifiedName,
            label = field.partialName,
            kind = FormFieldKind.CHOICE,
            text = runCatching { field.value?.joinToString("; ") }.getOrNull(),
            options = runCatching { field.options }.getOrDefault(emptyList()),
            readOnly = field.isReadOnly,
        )

        is PDListBox -> FormFieldUi(
            id = field.fullyQualifiedName,
            label = field.partialName,
            kind = FormFieldKind.CHOICE,
            text = runCatching { field.value?.joinToString("; ") }.getOrNull(),
            options = runCatching { field.options }.getOrDefault(emptyList()),
            readOnly = field.isReadOnly,
        )

        is PDTextField -> FormFieldUi(
            id = field.fullyQualifiedName,
            label = field.partialName,
            kind = FormFieldKind.TEXT,
            text = runCatching { field.value }.getOrNull(),
            readOnly = field.isReadOnly,
        )

        else -> null
    }

    /** Применить значение поля; true — успешно. */
    fun apply(doc: PDDocument, id: String, value: FieldValue): Boolean {
        val acroForm = doc.documentCatalog.acroForm ?: return false
        val field = acroForm.getField(id) ?: return false
        if (field.isReadOnly) return false
        return try {
            when (value) {
                is FieldValue.Text -> {
                    if (field !is PDTextField) return false
                    field.setValue(value.value)
                    true
                }

                is FieldValue.Check -> {
                    val checkbox = field as? PDCheckBox ?: return false
                    if (value.checked) checkbox.check() else checkbox.unCheck()
                    true
                }

                is FieldValue.Choice -> when (field) {
                    is PDRadioButton -> {
                        field.setValue(value.option)
                        true
                    }

                    is PDComboBox -> {
                        field.setValue(listOf(value.option))
                        true
                    }

                    is PDListBox -> {
                        field.setValue(listOf(value.option))
                        true
                    }

                    else -> false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Вписать значения в содержимое страниц и снять интерактивность полей. */
    fun flatten(doc: PDDocument): Boolean = runCatching {
        doc.documentCatalog.acroForm?.flatten()
        true
    }.getOrDefault(false)
}
