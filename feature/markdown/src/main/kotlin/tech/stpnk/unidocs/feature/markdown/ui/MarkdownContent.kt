package tech.stpnk.unidocs.feature.markdown.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import tech.stpnk.unidocs.feature.markdown.parser.MdBlock
import tech.stpnk.unidocs.feature.markdown.parser.MdSpan
import tech.stpnk.unidocs.feature.markdown.parser.MdStyle

/**
 * Собственный рендер Markdown в Compose: список MdBlock → экран.
 * Без WebView и без внешних HTML-движков.
 */
@Composable
fun MarkdownContent(blocks: List<MdBlock>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block -> BlockView(block) }
    }
}

@Composable
private fun BlockView(block: MdBlock) {
    when (block) {
        is MdBlock.Heading -> Text(
            text = spansToAnnotatedString(block.spans),
            style = headingStyle(block.level),
        )

        is MdBlock.Paragraph -> Text(
            text = spansToAnnotatedString(block.spans),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )

        is MdBlock.Code -> if (block.language == "mermaid") {
            MermaidDiagram(code = block.code)
        } else {
            CodeBlockView(block.code, block.language)
        }
        is MdBlock.Quote -> QuoteView(block.blocks)
        is MdBlock.ListBlock -> ListView(block)
        is MdBlock.Table -> TableView(block)
        MdBlock.Rule -> HorizontalDivider()
    }
}

@Composable
private fun headingStyle(level: Int) = when (level) {
    1 -> MaterialTheme.typography.headlineSmall
    2 -> MaterialTheme.typography.titleLarge
    3 -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.titleSmall
}

@Composable
private fun CodeBlockView(code: String, language: String?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            Text(
                text = if (language != null) "· $language\n$code".trimEnd() else code.trimEnd('\n'),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun QuoteView(blocks: List<MdBlock>) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(2.dp),
                ),
        )
        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            blocks.forEach { BlockView(it) }
        }
    }
}

@Composable
private fun ListView(block: MdBlock.ListBlock) {
    Column(modifier = Modifier.fillMaxWidth()) {
        block.items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    text = when (item.checked) {
                        null -> if (block.ordered) "${block.startNumber + index}." else "•"
                        true -> "☑"
                        false -> "☐"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.width(32.dp),
                )
                Column {
                    if (item.spans.isNotEmpty()) {
                        Text(
                            text = spansToAnnotatedString(item.spans),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    item.children.forEach { BlockView(it) }
                }
            }
        }
    }
}

@Composable
private fun TableView(block: MdBlock.Table) {
    Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Row {
            block.header.forEach { cell ->
                Text(
                    text = spansToAnnotatedString(cell),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.widthIn(min = 96.dp).padding(8.dp),
                )
            }
        }
        HorizontalDivider()
        block.rows.forEach { row ->
            Row {
                row.forEach { cell ->
                    Text(
                        text = spansToAnnotatedString(cell),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.widthIn(min = 96.dp).padding(8.dp),
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun spansToAnnotatedString(spans: List<MdSpan>): AnnotatedString = buildAnnotatedString {
    spans.forEach { span -> withStyle(spanStyle(span)) { append(span.text) } }
}

@Composable
private fun spanStyle(span: MdSpan): SpanStyle {
    var style = SpanStyle()
    if (MdStyle.BOLD in span.styles) {
        style = style.merge(SpanStyle(fontWeight = FontWeight.Bold))
    }
    if (MdStyle.ITALIC in span.styles) {
        style = style.merge(SpanStyle(fontStyle = FontStyle.Italic))
    }
    if (MdStyle.STRIKETHROUGH in span.styles) {
        style = style.merge(SpanStyle(textDecoration = TextDecoration.LineThrough))
    }
    if (MdStyle.INLINE_CODE in span.styles) {
        style = style.merge(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
    if (MdStyle.LINK in span.styles) {
        style = style.merge(
            SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            ),
        )
    }
    return style
}
