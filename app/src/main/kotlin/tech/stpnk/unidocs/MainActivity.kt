package tech.stpnk.unidocs

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.stpnk.unidocs.core.storage.RecentDocument
import tech.stpnk.unidocs.core.ui.theme.UnidocsTheme
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UnidocsTheme {
                DocumentsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(viewModel: DocumentsViewModel = viewModel()) {
    val recents by viewModel.recents.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onDocumentPicked(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
            ) {
                Text(stringResource(R.string.open_document))
            }

            Text(
                text = stringResource(R.string.recents_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (recents.isEmpty()) {
                EmptyRecents()
            } else {
                RecentList(
                    items = recents,
                    onOpen = viewModel::onDocumentPicked,
                    onRemove = viewModel::removeRecent,
                )
            }
        }
    }
}

@Composable
private fun EmptyRecents() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.empty_recents),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentList(
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
                    Row {
                        IconButton(onClick = { onRemove(doc.uri) }) {
                            Text("✕", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
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
