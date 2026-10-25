package tech.stpnk.unidocs.feature.markdown.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.stpnk.unidocs.feature.markdown.MarkdownSession
import tech.stpnk.unidocs.feature.markdown.parser.MdBlock
import androidx.compose.runtime.collectAsState

private enum class ViewMode(val label: String) {
    EDIT("Текст"),
    SPLIT("Сплит"),
    PREVIEW("Превью"),
}

/**
 * Экран Markdown: режимы «Текст / Сплит / Превью», тулбар вставки разметки,
 * явное сохранение через SAF и предупреждение о несохранённых изменениях.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownScreen(
    session: MarkdownSession,
    onBack: () -> Unit,
    onSave: (onResult: (Boolean) -> Unit) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(ViewMode.SPLIT) }
    var savedText by remember(session) { mutableStateOf(session.text.value) }
    var editorValue by remember(session) { mutableStateOf(TextFieldValue(session.text.value)) }
    var showExitDialog by remember { mutableStateOf(false) }

    val blocks by session.blocks.collectAsState()
    val dirty = editorValue.text != savedText

    fun requestBack() {
        if (dirty) showExitDialog = true else onBack()
    }

    BackHandler { requestBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = session.info.title + if (dirty) " •" else "",
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
                            onSave { ok -> if (ok) savedText = editorValue.text }
                        },
                    ) {
                        Text("Сохранить")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ModeChips(current = mode, onSelect = { mode = it })

            when (mode) {
                ViewMode.EDIT -> EditorPane(
                    value = editorValue,
                    onChange = {
                        editorValue = it
                        session.updateText(it.text)
                    },
                )

                ViewMode.PREVIEW -> PreviewPane(blocks)

                ViewMode.SPLIT -> Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        EditorPane(
                            value = editorValue,
                            onChange = {
                                editorValue = it
                                session.updateText(it.text)
                            },
                        )
                    }
                    VerticalDivider()
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        PreviewPane(blocks)
                    }
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Изменения не сохранены") },
            text = { Text("Выйти без сохранения?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onBack()
                    },
                ) { Text("Не сохранять") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onSave { ok -> if (ok) onBack() }
                    },
                ) { Text("Сохранить и выйти") }
            },
        )
    }
}

@Composable
private fun ModeChips(current: ViewMode, onSelect: (ViewMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ViewMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == current,
                onClick = { onSelect(mode) },
                label = { Text(mode.label) },
            )
        }
    }
}

@Composable
private fun EditorPane(value: TextFieldValue, onChange: (TextFieldValue) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        EditorToolbar(value = value, onChange = onChange)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun EditorToolbar(value: TextFieldValue, onChange: (TextFieldValue) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
    ) {
        ToolbarButton("B", FontWeight.Bold) { onChange(value.wrap("**", "**")) }
        ToolbarButton("I") { onChange(value.wrap("*", "*")) }
        ToolbarButton("S") { onChange(value.wrap("~~", "~~")) }
        ToolbarButton("H") { onChange(value.toggleLinePrefix("# ")) }
        ToolbarButton("•") { onChange(value.toggleLinePrefix("- ")) }
        ToolbarButton("☑") { onChange(value.toggleLinePrefix("- [ ] ")) }
        ToolbarButton("❝") { onChange(value.toggleLinePrefix("> ")) }
        ToolbarButton("`") { onChange(value.wrap("`", "`")) }
        ToolbarButton("🔗") { onChange(value.wrap("[", "](https://)")) }
    }
}

@Composable
private fun ToolbarButton(label: String, fontWeight: FontWeight? = null, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, fontWeight = fontWeight)
    }
}

@Composable
private fun PreviewPane(blocks: List<MdBlock>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        MarkdownContent(blocks)
    }
}

private fun TextFieldValue.wrap(prefix: String, suffix: String): TextFieldValue {
    val selection = this.selection
    return if (selection.collapsed) {
        val newText = text.substring(0, selection.start) + prefix + suffix + text.substring(selection.end)
        TextFieldValue(newText, TextRange(selection.start + prefix.length))
    } else {
        val selected = text.substring(selection.start, selection.end)
        val newText = text.substring(0, selection.start) + prefix + selected + suffix + text.substring(selection.end)
        TextFieldValue(
            text = newText,
            selection = TextRange(selection.start + prefix.length, selection.end + prefix.length),
        )
    }
}

private fun TextFieldValue.toggleLinePrefix(prefix: String): TextFieldValue {
    val lineStart = text.lastIndexOf('\n', (selection.start - 1).coerceAtLeast(0)).let {
        if (it < 0 || selection.start == 0) 0 else it + 1
    }
    val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
    val line = text.substring(lineStart, lineEnd)

    return if (line.startsWith(prefix)) {
        val newText = text.removeRange(lineStart, lineStart + prefix.length)
        val caret = (selection.start - prefix.length).coerceAtLeast(lineStart)
        TextFieldValue(newText, TextRange(caret))
    } else {
        val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        val caret = (selection.start + prefix.length).coerceAtMost(newText.length)
        TextFieldValue(newText, TextRange(caret))
    }
}
