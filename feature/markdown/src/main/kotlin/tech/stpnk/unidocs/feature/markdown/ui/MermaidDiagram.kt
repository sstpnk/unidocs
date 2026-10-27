package tech.stpnk.unidocs.feature.markdown.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewAssetLoader
import tech.stpnk.unidocs.core.webview.ASSET_HOST

private const val MERMAID_URL = "https://$ASSET_HOST/assets/markdown/mermaid.html"

private class MermaidBridge(private val callbacks: Callbacks) {
    interface Callbacks {
        fun onPageReady()
        fun onRendered(heightPx: Double)
        fun onError(message: String)
    }

    @JavascriptInterface
    fun onPageReady() = callbacks.onPageReady()

    @JavascriptInterface
    fun onRendered(heightPx: Double) = callbacks.onRendered(heightPx)

    @JavascriptInterface
    fun onError(message: String) = callbacks.onError(message)
}

/**
 * Рендер диаграммы Mermaid (блоки ```mermaid в Markdown) в изолированном WebView.
 * Высота подстраивается под фактический SVG (сообщение из JS).
 * Движок mermaid 9.4.3 (UMD) вендорен в assets — офлайн, без сети.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidDiagram(code: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val main = remember { Handler(Looper.getMainLooper()) }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var heightDp by remember { mutableStateOf(240) }
    var renderedFor by remember { mutableStateOf<String?>(null) }

    val codeB64 = remember(code) {
        Base64.encodeToString(code.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    val bridge = remember(code) {
        MermaidBridge(object : MermaidBridge.Callbacks {
            override fun onPageReady() {
                main.post { ready = true }
            }

            override fun onRendered(heightPx: Double) {
                main.post {
                    val density = context.resources.displayMetrics.density
                    heightDp = (heightPx / density).toInt().coerceIn(120, 2400)
                }
            }

            override fun onError(message: String) {
                main.post { /* диаграмма не распарсилась — ошибка уже видна в WebView */ }
            }
        })
    }

    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    Box(modifier = modifier.fillMaxWidth().height(heightDp.dp)) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = request.url.host != ASSET_HOST
                    }
                    addJavascriptInterface(bridge, "MermaidBridge")
                    webView = this
                    loadUrl(MERMAID_URL)
                }
            },
        )
    }

    LaunchedEffect(ready, codeB64) {
        if (ready && renderedFor != codeB64) {
            renderedFor = codeB64
            webView?.evaluateJavascript("window.render('$codeB64', 'default')", null)
        }
    }
}
