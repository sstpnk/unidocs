package tech.stpnk.unidocs.feature.pdfedit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tech.stpnk.unidocs.feature.pdfedit.PdfEditController
import tech.stpnk.unidocs.feature.pdfedit.form.FieldValue
import tech.stpnk.unidocs.feature.pdfedit.form.FormFieldKind
import tech.stpnk.unidocs.feature.pdfedit.form.FormFieldUi

private enum class EditTab(val label: String) {
    FORM("Форма"),
    FACSIMILE("Факсимиле"),
}

/**
 * Экран редактирования PDF (M3): поля AcroForm, факсимиле, сохранение через SAF.
 * Работает поверх копии документа в памяти PDFBox; исходный файл перезаписывается
 * по кнопке «Сохранить».
 */
@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun PdfEditScreen(
    uriString: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { PdfEditController(context) }
    val snackbar = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableStateOf(EditTab.FORM) }
    var loading by remember { mutableStateOf(true) }
    var fields by remember { mutableStateOf<List<FormFieldUi>>(emptyList()) }
    var flattenOnSave by rememberSaveable { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    fun report(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    LaunchedEffect(uriString) {
        runCatching { fields = controller.open(uriString) }
            .onFailure { report("Не удалось открыть PDF: ${it.message ?: "ошибка"}") }
        loading = false
    }

    DisposableEffect(Unit) {
        onDispose { GlobalScope.launch { controller.close() } }
    }

    fun requestBack() {
        if (dirty) showExitDialog = true else onClose()
    }

    androidx.activity.compose.BackHandler { requestBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Редактирование" + if (dirty) " •" else "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { requestBack() }) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching { controller.saveTo(flatten = flattenOnSave) }
                                    .onSuccess {
                                        dirty = false
                                        fields = controller.fieldsSnapshot()
                                        report("Сохранено")
                                    }
                                    .onFailure { report("Не удалось сохранить: ${it.message ?: "ошибка"}") }
                            }
                        },
                    ) { Text("Сохранить") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditTab.entries.forEach { item ->
                    FilterChip(
                        selected = tab == item,
                        onClick = { tab = item },
                        label = { Text(item.label) },
                    )
                }
            }

            when {
                loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text("Открываем документ…", modifier = Modifier.padding(top = 12.dp))
                }

                tab == EditTab.FORM -> FormTab(
                    fields = fields,
                    flattenOnSave = flattenOnSave,
                    onFlattenChange = { flattenOnSave = it },
                    onApply = { id, value ->
                        scope.launch {
                            val ok = controller.applyValue(id, value)
                            if (ok) {
                                dirty = true
                                fields = controller.fieldsSnapshot()
                            } else {
                                report("Не удалось применить значение")
                            }
                        }
                    },
                )

                else -> FacsimileTab(
                    uriString = uriString,
                    controller = controller,
                    onDirty = { dirty = true },
                    onMessage = ::report,
                )
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Изменения не сохранены") },
            text = { Text("Выйти без сохранения?") },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; onClose() }) { Text("Не сохранять") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        scope.launch {
                            runCatching { controller.saveTo(flatten = flattenOnSave) }
                                .onSuccess { onClose() }
                                .onFailure { report("Не удалось сохранить: ${it.message ?: "ошибка"}") }
                        }
                    },
                ) { Text("Сохранить и выйти") }
            },
        )
    }
}

@Composable
private fun FormTab(
    fields: List<FormFieldUi>,
    flattenOnSave: Boolean,
    onFlattenChange: (Boolean) -> Unit,
    onApply: (String, FieldValue) -> Unit,
) {
    if (fields.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "В этом PDF нет полей формы (AcroForm).\nИспользуйте вкладку «Факсимиле», чтобы поставить подпись.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(fields, key = { it.id }) { field ->
            FieldRow(field = field, onApply = onApply)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = flattenOnSave, onCheckedChange = onFlattenChange)
                Text(
                    text = "Флаттер при сохранении (сделать поля неинтерактивными)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun FieldRow(field: FormFieldUi, onApply: (String, FieldValue) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = field.label + if (field.readOnly) "  (только чтение)" else "",
            style = MaterialTheme.typography.labelLarge,
        )
        when (field.kind) {
            FormFieldKind.TEXT -> {
                var value by remember(field.id, field.text) { mutableStateOf(field.text.orEmpty()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        enabled = !field.readOnly,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Button(
                        onClick = { onApply(field.id, FieldValue.Text(value)) },
                        enabled = !field.readOnly,
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("OK") }
                }
            }

            FormFieldKind.CHECKBOX -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = field.checked == true,
                        onCheckedChange = { onApply(field.id, FieldValue.Check(it)) },
                        enabled = !field.readOnly,
                    )
                    Text(if (field.checked == true) "Отмечено" else "Не отмечено")
                }
            }

            FormFieldKind.RADIO, FormFieldKind.CHOICE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    field.options.take(8).forEach { option ->
                        FilterChip(
                            selected = field.text?.contains(option) == true,
                            onClick = { onApply(field.id, FieldValue.Choice(option)) },
                            label = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            enabled = !field.readOnly,
                        )
                    }
                }
                if (field.options.isEmpty()) {
                    Text("Нет вариантов", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
