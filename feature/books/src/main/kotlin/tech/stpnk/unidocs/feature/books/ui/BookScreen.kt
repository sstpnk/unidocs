package tech.stpnk.unidocs.feature.books.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tech.stpnk.unidocs.core.model.OutlineNode
import tech.stpnk.unidocs.core.webview.ASSET_HOST
import tech.stpnk.unidocs.core.webview.IsolatedWebView
import tech.stpnk.unidocs.feature.books.BookAnnotation
import tech.stpnk.unidocs.feature.books.BookBridge
import tech.stpnk.unidocs.feature.books.BookSession
import tech.stpnk.unidocs.feature.books.R
import tech.stpnk.unidocs.feature.books.ReadingPosition
import tech.stpnk.unidocs.feature.books.SearchEvent
import tech.stpnk.unidocs.feature.books.SearchItem
import tech.stpnk.unidocs.feature.books.SelectionDetail
import tech.stpnk.unidocs.feature.books.TocEntry
import java.text.DateFormat
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference

private const val READER_URL = "https://$ASSET_HOST/assets/books/reader.html"

private fun themeColors(theme: String): Pair<String, String> = when (theme) {
    "sepia" -> "#f4ecd8" to "#5b4636"
    "dark" -> "#1b1b1b" to "#d6d6d6"
    else -> "#ffffff" to "#1b1b1b"
}

private fun escapeJs(text: String): String =
    text.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'").replace("\n", " ")

/**
 * Экран чтения книг: foliate-js в изолированном WebView, темы/шрифт,
 * оглавление, закладки, автосохранение позиции чтения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(session: BookSession, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val main = remember { Handler(Looper.getMainLooper()) }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(false) }

    var fraction by remember { mutableStateOf(0.0) }
    var pageLabel by remember { mutableStateOf("") }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0.0) }

    var theme by rememberSaveable { mutableStateOf("light") }
    var fontScale by rememberSaveable { mutableStateOf(100) }
    var settingsOpen by remember { mutableStateOf(false) }
    var tocOpen by remember { mutableStateOf(false) }
    var bookmarksOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchRunning by remember { mutableStateOf(false) }
    var searchProgress by remember { mutableStateOf(0.0) }
    var searchResults by remember { mutableStateOf<List<SearchItem>>(emptyList()) }
    var searchSection by remember { mutableStateOf("") }
    var selectionDetail by remember { mutableStateOf<SelectionDetail?>(null) }
    var tappedAnnotationCfi by remember { mutableStateOf<String?>(null) }
    var sheetTab by rememberSaveable { mutableStateOf(0) }

    var tocEntries by remember { mutableStateOf<List<TocEntry>>(emptyList()) }
    val bookmarks by session.store.bookmarks(session.sourceUri).collectAsState(initial = emptyList())
    val toc by session.toc.collectAsState()
    val chapter by session.chapter.collectAsState()
    val annotations by session.store.annotations(session.sourceUri).collectAsState(initial = emptyList())

    var savedPosition by remember { mutableStateOf<ReadingPosition?>(null) }
    var positionRestored by remember { mutableStateOf(false) }
    var pendingPosition by remember { mutableStateOf<ReadingPosition?>(null) }
    val debouncer = remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(session) {
        savedPosition = session.store.currentPosition(session.sourceUri)
    }

    fun report(message: String) {
        main.post { scope.launch { snackbar.showSnackbar(message) } }
    }

    fun applyStyle() {
        val (bg, fg) = themeColors(theme)
        webView?.evaluateJavascript(
            "reader.setStyle({\"bg\":\"$bg\",\"fg\":\"$fg\",\"fontSize\":$fontScale})",
            null,
        )
    }

    fun jumpToFraction(value: Double) {
        webView?.evaluateJavascript("reader.goToFraction($value)", null)
    }

    fun maybeRestorePosition() {
        if (positionRestored) return
        positionRestored = true
        savedPosition?.let { saved -> jumpToFraction(saved.fraction) }
    }

    val bridge = remember(session) {
        BookBridge(object : BookBridge.Callbacks {
            override fun onPageReady() {
                main.post { pageReady = true }
            }

            override fun onLoaded() {
                main.post {
                    opened = true
                    maybeRestorePosition()
                    // Восстановление аннотаций: highlight рисуется через Overlayer,
                    // create-overlay пере-рисует их при загрузке каждой секции.
                    scope.launch {
                        runCatching { session.store.currentAnnotations(session.sourceUri) }
                            .onSuccess { annotations ->
                                annotations.forEach { annotation ->
                                    val cfiB64 = Base64.encodeToString(
                                        annotation.cfi.toByteArray(Charsets.UTF_8),
                                        Base64.NO_WRAP,
                                    )
                                    webView?.evaluateJavascript(
                                        "reader.addHighlight('$cfiB64', '${annotation.color}')",
                                        null,
                                    )
                                }
                            }
                    }
                }
            }

            override fun onRelocate(detail: tech.stpnk.unidocs.feature.books.RelocateDetail) {
                main.post {
                    selectionDetail = null
                    maybeRestorePosition()
                    fraction = detail.fraction
                    pageLabel = detail.page
                    session.onChapter(detail.chapter)
                    val position = ReadingPosition(detail.fraction, detail.index, System.currentTimeMillis())
                    pendingPosition = position
                    debouncer.value?.cancel()
                    debouncer.value = scope.launch {
                        delay(2000)
                        session.store.savePosition(session.sourceUri, detail.fraction, detail.index)
                    }
                }
            }

            override fun onToc(entries: List<TocEntry>) {
                main.post {
                    tocEntries = entries
                    session.onToc(
                        entries.mapIndexed { index, entry ->
                            OutlineNode(title = entry.label, level = entry.level, target = index)
                        },
                    )
                }
            }

            override fun onSearch(event: SearchEvent) {
                main.post {
                    when {
                        event.error != null -> {
                            searchRunning = false
                            report(context.getString(R.string.books_search_error, event.error))
                        }

                        event.done -> searchRunning = false

                        event.progress != null -> searchProgress = event.progress

                        event.subitems.isNotEmpty() -> {
                            searchSection = event.label.orEmpty()
                            searchResults = searchResults + event.subitems
                        }

                        event.cfi != null -> searchResults = searchResults + SearchItem(
                            cfi = event.cfi,
                            pre = event.pre.orEmpty(),
                            match = event.match.orEmpty(),
                            post = event.post.orEmpty(),
                        )
                    }
                }
            }

            override fun onSelection(detail: SelectionDetail) {
                main.post { selectionDetail = detail }
            }

            override fun onAnnotationTapped(cfi: String) {
                main.post { tappedAnnotationCfi = cfi }
            }

            override fun onError(message: String) {
                report("Ошибка книги: $message")
            }
        })
    }

    val mediaHandler = remember(session) {
        WebViewAssetLoader.PathHandler { path ->
            runCatching {
                val stream: InputStream = if (session.format == "cbr") {
                    val filePath = BookSession.fileUriToPath(session.readerUri)
                        ?: return@PathHandler null
                    File(filePath).inputStream()
                } else {
                    context.contentResolver.openInputStream(Uri.parse(session.readerUri))
                } ?: return@PathHandler null
                val mime = when (session.format) {
                    "epub" -> "application/epub+zip"
                    "fb2", "fbz" -> "application/xml"
                    else -> "application/octet-stream"
                }
                WebResourceResponse(mime, null, stream)
            }.getOrNull()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            debouncer.value?.cancel()
            pendingPosition?.let { position ->
                runBlocking {
                    runCatching {
                        session.store.savePosition(session.sourceUri, position.fraction, position.index)
                    }
                }
            }
        }
    }

    LaunchedEffect(theme, fontScale) {
        if (opened) applyStyle()
    }

    fun bookmarkAtCurrent(): tech.stpnk.unidocs.feature.books.Bookmark? =
        bookmarks.firstOrNull { kotlin.math.abs(it.fraction - fraction) < 0.005 }

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
                        if (chapter.isNotEmpty()) {
                            Text(
                                text = chapter,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = { searchOpen = true }) {
                        Text("🔍", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = { tocOpen = true }) {
                        Text("☰", style = MaterialTheme.typography.titleLarge)
                    }
                    Box {
                        IconButton(onClick = { settingsOpen = true }) {
                            Text("Aa", style = MaterialTheme.typography.titleMedium)
                        }
                        DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }) {
                            val themeLabels = listOf(
                                "light" to stringResource(R.string.books_theme_light),
                                "sepia" to stringResource(R.string.books_theme_sepia),
                                "dark" to stringResource(R.string.books_theme_dark),
                            )
                            themeLabels.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(if (theme == key) "● $label" else label) },
                                    onClick = {
                                        theme = key
                                        settingsOpen = false
                                    },
                                )
                            }
                            HorizontalMenuDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.books_font_minus, fontScale)) },
                                onClick = { fontScale = (fontScale - 10).coerceAtLeast(50) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.books_font_plus, fontScale)) },
                                onClick = { fontScale = (fontScale + 10).coerceAtMost(250) },
                            )
                        }
                    }
                    IconButton(onClick = {
                        val existing = bookmarkAtCurrent()
                        scope.launch {
                            if (existing != null) {
                                session.store.removeBookmark(session.sourceUri, existing)
                            } else {
                                session.store.addBookmark(
                                    session.sourceUri,
                                    tech.stpnk.unidocs.feature.books.Bookmark(
                                        label = chapter.ifEmpty { pageLabel.ifEmpty { "Позиция ${(fraction * 100).toInt()}%" } },
                                        fraction = fraction,
                                        index = 0,
                                        createdAtEpochMs = System.currentTimeMillis(),
                                    ),
                                )
                            }
                        }
                    }) {
                        Text(if (bookmarkAtCurrent() != null) "★" else "☆", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = { bookmarksOpen = true }) {
                        Text("🔖", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                Slider(
                    value = (if (dragging) dragValue else fraction).toFloat(),
                    onValueChange = {
                        dragging = true
                        dragValue = it.toDouble()
                    },
                    onValueChangeFinished = {
                        dragging = false
                        jumpToFraction(dragValue)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { webView?.evaluateJavascript("reader.prev()", null) }) {
                        Text("‹", style = MaterialTheme.typography.headlineSmall)
                    }
                    Text(
                        text = pageLabel.ifEmpty { "${(fraction * 100).toInt()}%" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    IconButton(onClick = { webView?.evaluateJavascript("reader.next()", null) }) {
                        Text("›", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            IsolatedWebView(
                context = context,
                startUrl = READER_URL,
                modifier = Modifier.fillMaxSize(),
                jsBridgeName = "AndroidBridge",
                jsBridge = bridge,
                mediaHandler = mediaHandler,
                onWebViewCreated = { webView = it },
                onPageFinished = { pageReady = true },
                onExternalLink = { uri ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                },
            )
            if (!opened) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.books_opening),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }

            selectionDetail?.let { selection ->
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selection.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        scope.launch {
                            runCatching {
                                session.store.addAnnotation(
                                    session.sourceUri,
                                    BookAnnotation(
                                        cfi = selection.cfi,
                                        text = selection.text,
                                        color = "yellow",
                                        createdAtEpochMs = System.currentTimeMillis(),
                                    ),
                                )
                                val cfiB64 = Base64.encodeToString(
                                    selection.cfi.toByteArray(Charsets.UTF_8),
                                    Base64.NO_WRAP,
                                )
                                webView?.evaluateJavascript("reader.addHighlight('$cfiB64', 'yellow')", null)
                                selectionDetail = null
                            }
                        }
                    }) { Text(stringResource(R.string.books_highlight)) }
                    IconButton(onClick = { selectionDetail = null }) {
                        Text("✕")
                    }
                }
            }
        }
    }

    if (pageReady && !opened) {
        LaunchedEffect(pageReady) {
            delay(100)
            webView?.evaluateJavascript(
                "reader.open('https://$ASSET_HOST/media/book.${session.format}')",
                null,
            )
        }
    }

    if (searchOpen) {
        ModalBottomSheet(onDismissRequest = { searchOpen = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(R.string.books_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                searchResults = emptyList()
                                searchSection = ""
                                searchRunning = true
                                searchProgress = 0.0
                                webView?.evaluateJavascript(
                                    "reader.search(\"${escapeJs(searchQuery)}\")",
                                    null,
                                )
                            }
                        },
                        enabled = !searchRunning && searchQuery.isNotBlank(),
                    ) { Text(stringResource(R.string.books_search)) }
                }

                if (searchRunning) {
                    LinearProgressIndicator(
                        progress = { searchProgress.toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.books_found, searchResults.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = {
                            webView?.evaluateJavascript("reader.clearSearch()", null)
                            searchResults = emptyList()
                        },
                        enabled = searchResults.isNotEmpty(),
                    ) { Text(stringResource(R.string.books_search_reset)) }
                }

                LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                    items(searchResults, key = { it.cfi + it.match.hashCode().toString() }) { item ->
                        val excerpt = buildAnnotatedString {
                            append(item.pre)
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                                append(item.match)
                            }
                            append(item.post)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchOpen = false
                                    webView?.evaluateJavascript(
                                        "reader.goToCfi(\"${escapeJs(item.cfi)}\")",
                                        null,
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = excerpt,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (searchSection.isNotEmpty()) {
                                Text(
                                    text = searchSection,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    if (tocOpen) {
        ModalBottomSheet(onDismissRequest = { tocOpen = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    text = stringResource(R.string.books_toc),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (tocEntries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.books_toc_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                    items(tocEntries) { entry ->
                        Text(
                            text = entry.label.ifEmpty { stringResource(R.string.books_toc_no_title) },
                            style = if (entry.level == 0) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tocOpen = false
                                    webView?.evaluateJavascript(
                                        "reader.goTo(\"${escapeJs(entry.href)}\")",
                                        null,
                                    )
                                }
                                .padding(start = (16 + entry.level * 16).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                        )
                    }
                }
            }
        }
    }

    if (bookmarksOpen) {
        ModalBottomSheet(onDismissRequest = { bookmarksOpen = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = sheetTab == 0,
                        onClick = { sheetTab = 0 },
                        label = { Text(stringResource(R.string.books_bookmarks)) },
                    )
                    FilterChip(
                        selected = sheetTab == 1,
                        onClick = { sheetTab = 1 },
                        label = { Text(stringResource(R.string.books_annotations)) },
                    )
                }

                if (sheetTab == 0) {
                    if (bookmarks.isEmpty()) {
                        Text(
                            text = stringResource(R.string.books_bookmarks_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        items(bookmarks, key = { it.createdAtEpochMs }) { bookmark ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        bookmarksOpen = false
                                        jumpToFraction(bookmark.fraction)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bookmark.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = "${(bookmark.fraction * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(onClick = {
                                    scope.launch { session.store.removeBookmark(session.sourceUri, bookmark) }
                                }) { Text("✕") }
                            }
                        }
                    }
                } else {
                    if (annotations.isEmpty()) {
                        Text(
                            text = stringResource(R.string.books_annotations_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        items(annotations, key = { it.cfi }) { annotation ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        bookmarksOpen = false
                                        webView?.evaluateJavascript(
                                            "reader.goToRaw(\"${escapeJs(annotation.cfi)}\")",
                                            null,
                                        )
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = annotation.text,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                            .format(java.util.Date(annotation.createdAtEpochMs)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = {
                                        scope.launch {
                                            session.store.removeAnnotation(session.sourceUri, annotation)
                                            val cfiB64 = Base64.encodeToString(
                                                annotation.cfi.toByteArray(Charsets.UTF_8),
                                                Base64.NO_WRAP,
                                            )
                                            webView?.evaluateJavascript("reader.removeHighlight('$cfiB64')", null)
                                        }
                                    }) { Text("✕") }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }

    tappedAnnotationCfi?.let { tappedCfi ->
        val annotation = annotations.firstOrNull { it.cfi == tappedCfi }
        AlertDialog(
            onDismissRequest = { tappedAnnotationCfi = null },
            title = { Text(stringResource(R.string.books_annotation_title)) },
            text = { Text(annotation?.text ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        annotation?.let {
                            session.store.removeAnnotation(session.sourceUri, it)
                            val cfiB64 = Base64.encodeToString(
                                it.cfi.toByteArray(Charsets.UTF_8),
                                Base64.NO_WRAP,
                            )
                            webView?.evaluateJavascript("reader.removeHighlight('$cfiB64')", null)
                        }
                    }
                    tappedAnnotationCfi = null
                }) { Text(stringResource(R.string.books_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { tappedAnnotationCfi = null }) { Text(stringResource(R.string.books_close)) }
            },
        )
    }
}

@Composable
private fun HorizontalMenuDivider() {
    Spacer(modifier = Modifier.height(4.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
