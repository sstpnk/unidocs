package tech.stpnk.unidocs.feature.office

import android.webkit.JavascriptInterface

/** JS-мост office.html → Kotlin. */
class OfficeBridge(private val callbacks: Callbacks) {

    interface Callbacks {
        fun onPageReady()
        fun onRendered()
        fun onError(message: String)
    }

    @JavascriptInterface
    fun onPageReady() = callbacks.onPageReady()

    @JavascriptInterface
    fun onRendered() = callbacks.onRendered()

    @JavascriptInterface
    fun onError(message: String) = callbacks.onError(message)
}
