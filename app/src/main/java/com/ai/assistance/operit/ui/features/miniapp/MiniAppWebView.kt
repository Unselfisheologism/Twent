package com.ai.assistance.operit.ui.features.miniapp

import android.content.Context
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ai.assistance.operit.data.miniapp.MiniAppManager
import com.ai.assistance.operit.data.miniapp.PermissionMapper
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * JavaScript interface exposed to mini-app WebViews.
 * Provides APIs that mini-apps can use to interact with the native Operit app.
 */
class MiniAppJsInterface(
    private val context: Context,
    private val onNotify: (String) -> Unit = {}
) {
    @JavascriptInterface
    fun getItem(key: String): String? {
        return null // Mini-app uses native localStorage; this is for cross-sync
    }

    @JavascriptInterface
    fun setItem(key: String, value: String) {
        // Trigger a localStorage backup to disk
        MiniAppBackupHelper.triggerBackup(context)
    }

    @JavascriptInterface
    fun notify(message: String) {
        onNotify(message)
    }

    @JavascriptInterface
    fun getAppInfo(): String {
        return """{"platform":"operit_android","type":"mini_app"}"""
    }
}

/**
 * Helper for localStorage backup/restore sync.
 */
object MiniAppBackupHelper {

    private const val TAG = "MiniAppBackupHelper"

    /**
     * Schedule a localStorage backup. Called from JS when localStorage is modified.
     */
    fun triggerBackup(context: Context) {
        // In a full implementation, this would collect localStorage from all
        // open mini-app WebViews and call MiniAppManager.backupLocalStorage().
        // For now, the backup is triggered at app close and manually.
        AppLogger.d(TAG, "localStorage backup triggered")
    }
}

private const val TAG = "MiniAppWebView"

/**
 * Creates and configures a WebView for loading mini-apps.
 *
 * This handles:
 * - JavaScript enabling
 * - localStorage / DOM storage
 * - Permission requests (camera, microphone, geolocation) via WebChromeClient
 * - JS bridge injection (OperitMiniApp)
 * - CORS/mixed content for localhost
 */
fun createMiniAppWebView(
    context: Context,
    onPermissionGranted: (resources: Array<String>) -> Unit = {},
    onPermissionDenied: (resources: Array<String>) -> Unit = {},
    onNotify: (String) -> Unit = {}
): WebView {
    val webView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        loadWithOverviewMode = true
        useWideViewPort = true
        setSupportZoom(true)
        builtInZoomControls = false
        displayZoomControls = false
        defaultTextEncodingName = "UTF-8"
        // Disable file scheme access for security
        allowUniversalAccessFromFileURLs = false
        allowFileAccessFromFileURLs = false
    }

    // Inject JS bridge
    webView.addJavascriptInterface(MiniAppJsInterface(context, onNotify), "OperitMiniAppNative")

    webView.webViewClient = WebViewClient()

    webView.webChromeClient = object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            val requestedResources = request.resources

            // Check if Operit has the corresponding Android permissions
            val grantedResources = mutableListOf<String>()
            val deniedResources = mutableListOf<String>()

            for (resource in requestedResources) {
                val androidPermission = when (resource) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> android.Manifest.permission.CAMERA
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> android.Manifest.permission.RECORD_AUDIO
                    PermissionRequest.RESOURCE_MIDI_SYSEX -> null // No direct mapping
                    else -> null
                }

                if (androidPermission != null) {
                    if (PermissionMapper.isPermissionGranted(context, androidPermission)) {
                        grantedResources.add(resource)
                    } else {
                        deniedResources.add(resource)
                    }
                } else {
                    // Unknown resource - deny by default
                    deniedResources.add(resource)
                }
            }

            if (grantedResources.isNotEmpty()) {
                request.grant(grantedResources.toTypedArray())
                onPermissionGranted(grantedResources.toTypedArray())
            }

            if (deniedResources.isNotEmpty()) {
                request.deny()
                onPermissionDenied(deniedResources.toTypedArray())
                AppLogger.w(TAG, "Mini-app permission denied: ${deniedResources.joinToString()}")
            }
        }
    }

    return webView
}
