package tech.stpnk.unidocs.feature.pdfedit.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.stpnk.unidocs.feature.pdfedit.PdfEditController
import tech.stpnk.unidocs.feature.pdfedit.R
import tech.stpnk.unidocs.feature.pdfedit.engine.PdfRendererEngine
import tech.stpnk.unidocs.feature.pdfedit.signature.SignatureStore
import java.io.ByteArrayOutputStream

private enum class FaxStage { SOURCE, PLACE }

/**
 * Вкладка «Факсимиле»: нарисовать подпись или взять сохранённую,
 * затем разместить её тапом на предпросмотре страницы.
 */
@Composable
fun FacsimileTab(
    uriString: String,
    controller: PdfEditController,
    onDirty: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { SignatureStore(context) }
    val saved by store.all().collectAsState(initial = emptyList())

    var stage by rememberSaveable { mutableStateOf(FaxStage.SOURCE) }
    var pendingPng by remember { mutableStateOf<ByteArray?>(null) }

    val png = pendingPng
    if (stage == FaxStage.PLACE && png != null) {
        PlacementView(
            uriString = uriString,
            controller = controller,
            pngBytes = png,
            onStamped = {
                onDirty()
                stage = FaxStage.SOURCE
            },
            onBack = { stage = FaxStage.SOURCE },
            onMessage = onMessage,
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.fax_draw_hint), style = MaterialTheme.typography.titleSmall)
        SignaturePad(modifier = Modifier.fillMaxWidth().height(200.dp)) { bitmap ->
            val bytes = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
            pendingPng = bytes
            scope.launch { store.add(bytes) }
            stage = FaxStage.PLACE
        }

        if (saved.isNotEmpty()) {
            Text(
                text = stringResource(R.string.fax_saved),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(saved, key = { it.createdAtEpochMs.toString() + it.pngBase64.hashCode() }) { item ->
                    val itemPng = remember(item) { store.decodePng(item) }
                    val bitmap = remember(itemPng) { BitmapFactory.decodeByteArray(itemPng, 0, itemPng.size) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .clickable {
                                    pendingPng = itemPng
                                    stage = FaxStage.PLACE
                                }
                                .padding(4.dp),
                        ) {
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = stringResource(R.string.fax_saved_cd),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        TextButton(onClick = { scope.launch { store.remove(item) } }) {
                            Text("✕", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlacementView(
    uriString: String,
    controller: PdfEditController,
    pngBytes: ByteArray,
    onStamped: () -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var page by rememberSaveable { mutableIntStateOf(0) }
    var centerX by rememberSaveable { mutableStateOf(0.5f) }
    var centerY by rememberSaveable { mutableStateOf(0.35f) }
    var widthPct by rememberSaveable { mutableStateOf(0.3f) }
    var stamping by remember { mutableStateOf(false) }

    val pageCount = remember { controller.pageCount }
    val engine = remember(uriString) {
        PdfRendererEngine(context, Uri.parse(uriString)).also { runCatching { it.open() } }
    }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(page) {
        pageBitmap = withContext(Dispatchers.IO) {
            runCatching { engine.renderPage(page, 900) }.getOrNull()
        }
    }
    DisposableEffect(Unit) {
        onDispose { engine.close() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.fax_page), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { if (page > 0) page-- }, enabled = page > 0) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text("${page + 1} / $pageCount", style = MaterialTheme.typography.bodyMedium)
            IconButton(
                onClick = { if (page < pageCount - 1) page++ },
                enabled = page < pageCount - 1,
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
                .pointerInput(page) {
                    detectTapGestures { offset ->
                        centerX = (offset.x / size.width).coerceIn(0f, 1f)
                        centerY = (offset.y / size.height).coerceIn(0f, 1f)
                    }
                },
        ) {
            val bitmap = pageBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Страница PDF",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            Box(
                modifier = Modifier
                    .offset(
                        x = maxWidth * centerX - 14.dp,
                        y = maxHeight * centerY - 14.dp,
                    )
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
            )
        }

        Text(
            text = stringResource(R.string.fax_width_hint, (widthPct * 100).toInt()),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Slider(
            value = widthPct,
            onValueChange = { widthPct = it },
            valueRange = 0.1f..0.6f,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.fax_back)) }
            Box(modifier = Modifier.weight(1f))
            Button(
                enabled = !stamping,
                onClick = {
                    stamping = true
                    scope.launch {
                        val ok = controller.stampSignature(page, centerX, centerY, widthPct, pngBytes)
                        stamping = false
                        if (ok) {
                            onMessage(context.getString(R.string.fax_stamped))
                            onStamped()
                        } else {
                            onMessage(context.getString(R.string.fax_stamp_failed))
                        }
                    }
                },
            ) { Text(stringResource(R.string.fax_stamp)) }
        }
    }
}
