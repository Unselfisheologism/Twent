package com.ai.assistance.operit.ui.features.chat.components.part

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.delay

/**
 * Renders AI-generated OpenUI Lang code in a WebView below the AI chat message.
 *
 * The AI outputs OpenUI code wrapped in `<openui>...</openui>` tags.
 * After the stream completes, the code is extracted and passed here for rendering.
 *
 * The WebView loads a bundled HTML/CSS/JS renderer that parses OpenUI Lang DSL
 * (declarative statements like `title = Text("Hello", "large")`) into HTML UI.
 * Supports: Card, Stack, Text, Button, Input, Select, Form, FormControl, Table,
 * StatCard, StatGrid, Tabs, Accordion, Badge, Callout, List, Alert, ProgressBar, etc.
 *
 * @param openUiCode The OpenUI Lang DSL code to render (extracted from AI response)
 * @param onEvent Optional callback for UI interaction events (formSubmit, input, etc.)
 * @param isDarkTheme Whether dark theme is active (controls WebView background)
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GenerativeUiWebView(
    openUiCode: String,
    onEvent: ((String, Map<String, Any>) -> Unit)? = null,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(true) }
    var isWebViewReady by remember { mutableStateOf(false) }
    var renderAttempt by remember { mutableIntStateOf(0) }

    val bgColor = if (isDarkTheme) "#1c1c1e" else "#f5f5f7"
    val surfaceColor = if (isDarkTheme) "#2c2c2e" else "#ffffff"
    val textColor = if (isDarkTheme) "#f5f5f7" else "#1d1d1f"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
    ) {
        // ── Header toggle bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(
                    if (isDarkTheme) Color(0xFF2c2c2e) else Color(0xFFe8e8ec)
                )
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Generated UI",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkTheme) Color(0xFFf5f5f7) else Color(0xFF1d1d1f)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Refresh button
                if (isWebViewReady) {
                    IconButton(
                        onClick = { renderAttempt++ },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = if (isDarkTheme) Color(0xFF98989d) else Color(0xFF86868b),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── WebView container ──
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 400.dp)
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(surfaceColor.let { if (isDarkTheme) Color(0xFF2c2c2e) else Color(0xFFffffff) })
            ) {
                if (openUiCode.isBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No UI code provided",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    var webViewInstance by remember(openUiCode, renderAttempt) {
                        mutableStateOf<WebView?>(null)
                    }

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    setSupportZoom(true)
                                    // Performance
                                    cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                }

                                setBackgroundColor(if (isDarkTheme) Color.parseColor("#2c2c2e") else Color.WHITE)

                                webChromeClient = WebChromeClient()

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isWebViewReady = true
                                        // Inject the OpenUI code and trigger render
                                        injectAndRender(openUiCode, isDarkTheme)
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        errorCode: Int,
                                        description: String?,
                                        failingUrl: String?
                                    ) {
                                        super.onReceivedError(view, errorCode, description, failingUrl)
                                        AppLogger.e("GenerativeUiWebView", "WebView error: $description ($errorCode)")
                                    }
                                }

                                // Android JS interface for events from the rendered UI
                                addJavascriptInterface(
                                    GenerativeUiBridge(onEvent),
                                    "TwAndroid"
                                )

                                // Load the bundled HTML renderer
                                try {
                                    loadUrl("file:///android_asset/generative_ui_renderer.html")
                                } catch (e: Exception) {
                                    AppLogger.e("GenerativeUiWebView", "Failed to load renderer: ${e.message}")
                                }

                                webViewInstance = this
                            }
                        },
                        update = { webView ->
                            webViewInstance = webView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Injects the OpenUI Lang code into the WebView and triggers rendering.
 * Uses JavaScript evaluation to call window.renderGenerativeUI(code).
 */
private fun WebView.injectAndRender(openUiCode: String, isDarkTheme: Boolean) {
    try {
        // Escape the code for safe JavaScript string embedding
        val escapedCode = openUiCode
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")

        val darkModeScript = if (isDarkTheme) """
            document.body.style.background = '#1c1c1e';
            document.body.style.color = '#f5f5f7';
            document.documentElement.style.setProperty('--bg', '#1c1c1e');
            document.documentElement.style.setProperty('--surface', '#2c2c2e');
            document.documentElement.style.setProperty('--border', '#3a3a3c');
            document.documentElement.style.setProperty('--text', '#f5f5f7');
            document.documentElement.style.setProperty('--text-secondary', '#98989d');
        """ else ""

        evaluate(
            """
            (function() {
                window._genUiCode = "$escapedCode";
                $darkModeScript
                if (typeof window.renderGenerativeUI === 'function') {
                    window.renderGenerativeUI("$escapedCode");
                } else {
                    // Wait for script to load
                    var waitCount = 0;
                    var wait = setInterval(function() {
                        waitCount++;
                        if (typeof window.renderGenerativeUI === 'function') {
                            clearInterval(wait);
                            window.renderGenerativeUI("$escapedCode");
                        } else if (waitCount > 20) {
                            clearInterval(wait);
                            console.error('renderGenerativeUI not found after 2s');
                        }
                    }, 100);
                }
            })();
            """.trimIndent(),
            object : android.webkit.ValueCallback<String> {
                override fun onReceiveValue(result: String?) {
                    // Result from JS evaluation
                }
            }
        )
    } catch (e: Exception) {
        AppLogger.e("GenerativeUiWebView", "injectAndRender failed: ${e.message}")
    }
}

/**
 * JavaScript interface exposed to the WebView.
 * Allows the rendered UI to emit events back to the Android host.
 */
class GenerativeUiBridge(
    private val onEvent: ((String, Map<String, Any>) -> Unit)?
) {
    @JavascriptInterface
    fun log(level: String, msg: String) {
        when (level) {
            "error" -> AppLogger.e("GenUI", msg)
            "warn" -> AppLogger.w("GenUI", msg)
            else -> AppLogger.d("GenUI", msg)
        }
    }

    @JavascriptInterface
    fun onEvent(event: String, dataJson: String) {
        try {
            val data = parseJsonToMap(dataJson)
            onEvent?.invoke(event, data)
            AppLogger.d("GenUI", "Event: $event — $dataJson")
        } catch (e: Exception) {
            AppLogger.e("GenUI", "Failed to parse event data: ${e.message}")
        }
    }

    private fun parseJsonToMap(json: String): Map<String, Any> {
        return try {
            val result = mutableMapOf<String, Any>()
            // Simple JSON parser for flat objects
            val clean = json.trim().removePrefix("{").removeSuffix("}")
            clean.split(",").forEach { pair ->
                val parts = pair.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim().removeSurrounding("\"")
                    val value = parts[1].trim().removeSurrounding("\"")
                    result[key] = value
                }
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
