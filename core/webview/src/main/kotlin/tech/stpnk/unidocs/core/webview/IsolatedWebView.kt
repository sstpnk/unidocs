package tech.stpnk.unidocs.core.webview

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

/** Хост приложения в домене WebViewAssetLoader (безопасный контекст, без сети). */
const val ASSET_HOST = "appassets.androidplatform.net"

/**
 * Фабрика изолированного WebView для локального контента:
 * - без доступа к файлам и content:// напрямую;
 * - весь контент идёт через WebViewAssetLoader (assets модулей + кастомные обработчики);
 * - внешние ссылки не открываются внутри — отдаются в колбэк.
 */
class IsolatedWebViewFactory(
    private val context: Context,
    /** Обработчик путей поверх /media/ — например, раздача файла книги из SAF. */
    private val mediaHandler: WebViewAssetLoader.PathHandler? = null,
) {
    val assetLoader: WebViewAssetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .apply { mediaHandler?.let { addPathHandler("/media/", it) } }
        .build()

    fun create(clientCallbacks: ClientCallbacks? = null): WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        configureIsolated(this)
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean {
                val url = request.url
                if (url.host == ASSET_HOST) return false
                clientCallbacks?.onExternalLink(url)
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                clientCallbacks?.onPageFinished(view)
            }
        }
    }

    interface ClientCallbacks {
        fun onPageFinished(webView: WebView)
        fun onExternalLink(uri: Uri)
    }
}

@SuppressLint("SetJavaScriptEnabled")
fun configureIsolated(webView: WebView) {
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        mediaPlaybackRequiresUserGesture = true
        cacheMode = WebSettings.LOAD_DEFAULT
        textZoom = 100
    }
    webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
}

/**
 * Compose-обёртка: создаёт изолированный WebView, регистрирует JS-мост и отдаёт
 * готовый WebView в [onWebViewCreated] (для evaluateJavascript).
 */
@Composable
fun IsolatedWebView(
    context: Context,
    startUrl: String,
    modifier: Modifier = Modifier,
    jsBridgeName: String? = null,
    jsBridge: Any? = null,
    mediaHandler: WebViewAssetLoader.PathHandler? = null,
    onWebViewCreated: (WebView) -> Unit = {},
    onPageFinished: (WebView) -> Unit = {},
    onExternalLink: (Uri) -> Unit = {},
) {
    val factory = remember(context, mediaHandler) {
        IsolatedWebViewFactory(context, mediaHandler)
    }
    val callbacks = remember(factory, onPageFinished, onExternalLink) {
        object : IsolatedWebViewFactory.ClientCallbacks {
            override fun onPageFinished(webView: WebView) = onPageFinished(webView)
            override fun onExternalLink(uri: Uri) = onExternalLink(uri)
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            factory.create(callbacks).apply {
                jsBridgeName?.let { name -> jsBridge?.let { bridge -> addJavascriptInterface(bridge, name) } }
                onWebViewCreated(this)
                loadUrl(startUrl)
            }
        },
    )
}
