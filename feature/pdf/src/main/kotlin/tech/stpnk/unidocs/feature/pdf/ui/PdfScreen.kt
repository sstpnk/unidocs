package tech.stpnk.unidocs.feature.pdf.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.pdf.view.PdfView
import tech.stpnk.unidocs.feature.pdf.PdfSession
import tech.stpnk.unidocs.feature.pdf.engine.PdfRendererEngine

/**
 * Экран PDF: основной движок — androidx.pdf PdfView (поиск, выделение, ссылки),
 * переключатель на fallback-рендер системного PdfRenderer — для сравнения
 * и как запасной путь.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScreen(session: PdfSession, onBack: () -> Unit) {
    var fallback by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = buildString {
                            append(session.info.title)
                            session.info.pageCount?.let { append("  ·  $it стр.") }
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    FilterChip(
                        selected = fallback,
                        onClick = { fallback = !fallback },
                        label = { Text(if (fallback) "PdfRenderer" else "androidx.pdf") },
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (fallback) {
                FallbackPdfView(session)
            } else {
                AndroidXPdfView(session)
            }
        }
    }
}

@Composable
private fun AndroidXPdfView(session: PdfSession) {
    val context = LocalContext.current
    val pdfView = remember {
        PdfView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    AndroidView(factory = { pdfView }, modifier = Modifier.fillMaxSize())

    LaunchedEffect(session, pdfView) {
        runCatching { pdfView.pdfDocument = session.document }
            .onFailure { error -> error.printStackTrace() }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { pdfView.pdfDocument = null }
        }
    }
}

@Composable
private fun FallbackPdfView(session: PdfSession) {
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    val engine = remember(session) {
        PdfRendererEngine(context, Uri.parse(session.uriString)).also { runCatching { it.open() } }
    }
    val pageCount = remember(session) { session.info.pageCount ?: engine.pageCount }
    val cache = remember { android.util.LruCache<Int, android.graphics.Bitmap>(8) }
    var cacheTick by remember { mutableIntStateOf(0) }

    DisposableEffect(session) {
        onDispose { engine.close() }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(count = pageCount, key = { it }) { index ->
            val bitmap = cacheTick.let { cache.get(index) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Страница ${index + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                )
            } else {
                LaunchedEffect(index) {
                    val widthPx = (screenWidthDp * 1.5f).toInt() // небольшой оверсемпл для чёткости
                    val rendered = runCatching { engine.renderPage(index, widthPx) }.getOrNull()
                    if (rendered != null) {
                        cache.put(index, rendered)
                        cacheTick++
                    }
                }
                PagePlaceholder(index + 1)
            }
        }
    }
}

@Composable
private fun PagePlaceholder(pageNumber: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .aspectRatio(0.707f), // A4
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Страница $pageNumber…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
