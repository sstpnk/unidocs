package tech.stpnk.unidocs.feature.office.ui

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewAssetLoader
import tech.stpnk.unidocs.core.webview.ASSET_HOST
import tech.stpnk.unidocs.core.webview.IsolatedWebView
import tech.stpnk.unidocs.feature.office.OfficeBridge
import tech.stpnk.unidocs.feature.office.OfficeSession
import tech.stpnk.unidocs.feature.office.R
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference

private const val OFFICE_URL = "https://$ASSET_HOST/assets/office/office.html"

/**
 * Экран офисного документа: изолированный WebView + office.html,
 * который диспетчеризует docx/xlsx/pptx/odt на нужный движок
 * по query-параметрам ?type=...&src=... (файл — через /media/).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeScreen(session: OfficeSession, onBack: () -> Unit) {
    val context = LocalContext.current
    val main = remember { Handler(Looper.getMainLooper()) }

    val webViewRef = remember { AtomicReference<WebView?>(null) }
    var rendered by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var csvMode by rememberSaveable { mutableStateOf("table") }

    val mediaHandler = remember(session) {
        WebViewAssetLoader.PathHandler { path ->
            runCatching {
                val stream: InputStream = if (session.officeType == "odt" || session.officeType == "rtf" || session.officeType == "doc") {
                    val filePath = Uri.parse(session.mediaUri).path
                        ?: return@PathHandler null
                    File(filePath).inputStream()
                } else {
                    context.contentResolver.openInputStream(Uri.parse(session.mediaUri))
                } ?: return@PathHandler null
                val mime = when (session.officeType) {
                    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    "odt", "rtf", "doc" -> "text/html"
                    "html", "mhtml" -> "text/html"
                    "eml" -> "message/rfc822"
                    "ics" -> "text/calendar"
                    "csv" -> "text/csv"
                    else -> "application/octet-stream"
                }
                WebResourceResponse(mime, if (session.officeType == "odt") "utf-8" else null, stream)
            }.getOrNull()
        }
    }

    val bridge = remember(session) {
        OfficeBridge(object : OfficeBridge.Callbacks {
            override fun onPageReady() {
                // html готов — диспетчеризация происходит в самом office.html
            }

            override fun onRendered() {
                main.post {
                    rendered = true
                    if (session.officeType == "csv") {
                        webViewRef.get()?.evaluateJavascript(
                            "window.csvView && window.csvView.setMode('$csvMode')",
                            null,
                        )
                    }
                }
            }

            override fun onError(message: String) {
                main.post { errorMessage = message }
            }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = session.info.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = session.officeType.uppercase(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (session.officeType == "csv") {
                        FilterChip(
                            selected = csvMode == "table",
                            onClick = {
                                csvMode = "table"
                                webViewRef.get()?.evaluateJavascript("window.csvView && window.csvView.setMode('table')", null)
                            },
                            label = { Text("Таблица") },
                        )
                        FilterChip(
                            selected = csvMode == "text",
                            onClick = {
                                csvMode = "text"
                                webViewRef.get()?.evaluateJavascript("window.csvView && window.csvView.setMode('text')", null)
                            },
                            label = { Text("Текст") },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            IsolatedWebView(
                context = context,
                startUrl = "$OFFICE_URL?type=${session.officeType}&src=https://$ASSET_HOST/media/${session.mediaName}" +
                    "&lang=" + java.util.Locale.getDefault().language,
                modifier = Modifier.fillMaxSize(),
                jsBridgeName = "AndroidBridge",
                jsBridge = bridge,
                mediaHandler = mediaHandler,
                onWebViewCreated = { webViewRef.set(it) },
                onExternalLink = { uri ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                },
            )
            if (!rendered && errorMessage == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.office_opening),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
        }
    }
}
