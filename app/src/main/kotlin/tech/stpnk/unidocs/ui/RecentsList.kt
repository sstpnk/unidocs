package tech.stpnk.unidocs.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.stpnk.unidocs.core.storage.RecentDocument
import java.text.DateFormat
import java.util.Date

/**
 * Список «Недавних». Строка целиком кликабельна и открывает документ
 * ([onOpen] получает SAF-uri записи); кнопка ✕ удаляет запись.
 */
@Composable
internal fun RecentList(
    items: List<RecentDocument>,
    onOpen: (Uri) -> Unit,
    onRemove: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.uri }) { doc ->
            ListItem(
                headlineContent = {
                    Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(
                        text = listOfNotNull(
                            doc.mimeType ?: "неизвестный тип",
                            doc.sizeBytes?.let { formatBytes(it) },
                            DateFormat.getDateInstance(DateFormat.SHORT).format(Date(doc.openedAtEpochMs)),
                        ).joinToString("  •  "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    IconButton(onClick = { onRemove(doc.uri) }) {
                        Text("✕", style = MaterialTheme.typography.titleMedium)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(Uri.parse(doc.uri)) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1 shl 30 -> "%.1f ГБ".format(bytes.toDouble() / (1 shl 30))
    bytes >= 1 shl 20 -> "%.1f МБ".format(bytes.toDouble() / (1 shl 20))
    bytes >= 1 shl 10 -> "%.1f КБ".format(bytes.toDouble() / (1 shl 10))
    else -> "$bytes Б"
}
