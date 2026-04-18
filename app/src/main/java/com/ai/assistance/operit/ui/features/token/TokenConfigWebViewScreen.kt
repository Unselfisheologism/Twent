package com.ai.assistance.operit.ui.features.token

import com.ai.assistance.operit.util.AppLogger
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.main.LocalTopBarActions
import com.ai.assistance.operit.ui.main.components.LocalAppBarContentColor
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.features.token.components.UrlConfigDialog
import com.ai.assistance.operit.ui.features.token.model.NavDestination
import com.ai.assistance.operit.ui.features.token.model.ProviderApiKeyConfig
import com.ai.assistance.operit.ui.features.token.model.getIconForIndex
import com.ai.assistance.operit.ui.features.token.preferences.UrlConfigManager
import com.ai.assistance.operit.ui.features.token.webview.WebViewConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Token配置屏幕 */
@Composable
fun TokenConfigWebViewScreen(
    onNavigateBack: () -> Unit,
    onNavigateToModelConfig: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val urlConfigManager = remember { UrlConfigManager(context) }
    val modelConfigManager = remember { ModelConfigManager(context) }
    
    // 获取URL配置
    val urlConfig by urlConfigManager.urlConfigFlow.collectAsState(initial = com.ai.assistance.operit.ui.features.token.model.UrlConfig())

    // State
    var isLoading by remember { mutableStateOf(true) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showConfigDialog by remember { mutableStateOf(false) }
    
    // Provider dropdown state
    val providersWithApiKeyPages = remember { ProviderApiKeyConfig.getProvidersWithApiKeyPages() }
    var selectedProviderIndex by remember { mutableStateOf(0) }
    var isProviderDropdownExpanded by remember { mutableStateOf(false) }
    
    // Get the selected provider info
    val selectedProviderInfo = providersWithApiKeyPages.getOrNull(selectedProviderIndex)
    
    // URL bar state
    var currentUrl by remember { mutableStateOf("") }
    var urlBarText by remember { mutableStateOf("") }
    
    // Clipboard monitoring state
    var showApiKeyToast by remember { mutableStateOf(false) }
    var clipboardContent by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var isApiKeySaved by remember { mutableStateOf(false) }
    
    // Clipboard manager
    val clipboardManager = remember { 
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager 
    }
    
    // 创建WebView实例
    val webView = remember { WebViewConfig.createWebView(context) }

    // 根据配置创建导航目标
    val navDestinations = remember(urlConfig) {
        urlConfig.tabs.take(4).mapIndexed { index, tabConfig ->
            NavDestination(
                title = tabConfig.title,
                url = tabConfig.url,
                icon = getIconForIndex(index)
            )
        }
    }

    // 导航到指定URL
    fun navigateTo(url: String, index: Int) {
        isLoading = true
        webView.loadUrl(url)
        selectedTabIndex = index
    }
    
    // Load URL from URL bar
    fun loadUrlFromBar() {
        val url = urlBarText.trim()
        if (url.isNotEmpty()) {
            val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            isLoading = true
            webView.loadUrl(fullUrl)
        }
    }
    
    // Check clipboard for API key pattern
    fun checkClipboard() {
        try {
            if (clipboardManager.hasPrimaryClip()) {
                val clipData = clipboardManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val clipText = clipData.getItemAt(0).text?.toString() ?: ""
                    // Check if it looks like an API key (long alphanumeric string)
                    if (clipText.length >= 20 && clipText.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) {
                        clipboardContent = clipText
                        apiKeyInput = clipText
                        showApiKeyToast = true
                        isApiKeySaved = false
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("TokenConfigWebView", "Error checking clipboard: ${e.message}")
        }
    }
    
    // Save API key for the selected provider
    suspend fun saveApiKey() {
        val providerInfo = selectedProviderInfo ?: return
        val apiKey = apiKeyInput.trim()
        if (apiKey.isEmpty()) return
        
        try {
            // Find or create a config for this provider
            val configList = modelConfigManager.configListFlow.first()
            
            // Try to find an existing config for this provider
            var targetConfigId: String? = null
            for (configId in configList) {
                val config = modelConfigManager.getModelConfigFlow(configId).first()
                if (config.apiProviderType == providerInfo.providerType) {
                    targetConfigId = configId
                    break
                }
            }
            
            // If no config exists for this provider, create one
            if (targetConfigId == null) {
                val configName = "${providerInfo.displayName} Config"
                targetConfigId = modelConfigManager.createConfig(configName)
                
                // Update the new config with the provider type and API key
                modelConfigManager.updateModelConfig(
                    configId = targetConfigId,
                    apiKey = apiKey,
                    apiEndpoint = "",
                    modelName = "",
                    apiProviderType = providerInfo.providerType
                )
            } else {
                // Update existing config with the new API key
                modelConfigManager.updateModelConfig(
                    configId = targetConfigId,
                    apiKey = apiKey,
                    apiEndpoint = "",
                    modelName = "",
                    apiProviderType = providerInfo.providerType
                )
            }
            
            isApiKeySaved = true
            AppLogger.d("TokenConfigWebView", "API key saved for ${providerInfo.displayName}")
        } catch (e: Exception) {
            AppLogger.e("TokenConfigWebView", "Error saving API key: ${e.message}")
        }
    }

    // 简化的WebViewClient
    val webViewClient = remember {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
            ): Boolean {
                request?.url?.let { uri ->
                    val url = uri.toString()
                    
                    // 只拦截明确需要外部应用处理的协议
                    if (url.startsWith("alipays:") || 
                        url.startsWith("alipay:") || 
                        url.startsWith("weixin:") ||
                        url.startsWith("weixins:")) {
                        
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            return true
                        } catch (e: Exception) {
                            AppLogger.e("TokenConfigWebView", "无法打开外部应用: ${e.message}")
                            // 如果打开失败，返回false让WebView尝试处理
                            return false
                        }
                    }
                    
                    // 对于http/https链接，让WebView正常加载
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        return false
                    }
                    
                    // 对于其他协议（如javascript:, about:等），也让WebView处理
                    // 不要尝试用外部应用打开
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isLoading = false
                
                // Update current URL and URL bar
                url?.let { finishedUrl ->
                    currentUrl = finishedUrl
                    urlBarText = finishedUrl
                    
                    // Update selected tab
                    navDestinations.forEachIndexed { index, destination ->
                        if (finishedUrl.contains(destination.url) || 
                            destination.url.contains(finishedUrl)) {
                                selectedTabIndex = index
                        }
                    }
                }
                
                // Check clipboard when page finishes loading
                checkClipboard()
            }
        }
    }

    // 设置WebView
    DisposableEffect(webView, selectedProviderInfo) {
        webView.webViewClient = webViewClient
        
        // 加载初始URL - use the selected provider's API key URL if available
        val initialUrl = selectedProviderInfo?.apiKeyUrl?.takeIf { it.isNotEmpty() } 
            ?: urlConfig.signInUrl
        
        if (initialUrl.isNotEmpty()) {
            webView.loadUrl(initialUrl)
            currentUrl = initialUrl
            urlBarText = initialUrl
        }

        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    
    // Monitor clipboard periodically
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Check clipboard when app resumes
                checkClipboard()
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Periodic clipboard check
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000) // Check every 2 seconds
            checkClipboard()
        }
    }

    // 从CompositionLocal获取设置TopBar Actions的函数
    val setTopBarActions = LocalTopBarActions.current
    val appBarContentColor = LocalAppBarContentColor.current
    val isCurrentScreen = LocalIsCurrentScreen.current

    // 使用DisposableEffect来设置和清理TopBar按钮，避免竞争条件
    LaunchedEffect(isCurrentScreen, appBarContentColor) {
        if (isCurrentScreen) {
            setTopBarActions {
                // 设置按钮
                IconButton(
                    onClick = { showConfigDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings_config),
                        tint = appBarContentColor
                    )
                }
            }
        }
    }

    // 配置对话框
    if (showConfigDialog) {
        UrlConfigDialog(
            currentConfig = urlConfig,
            onSave = { newConfig ->
                scope.launch {
                    urlConfigManager.saveUrlConfig(newConfig)
                    showConfigDialog = false
                }
            },
            onDismiss = { showConfigDialog = false }
        )
    }

    // UI布局
    CustomScaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // URL Bar with Provider Dropdown
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    tonalElevation = 0.5.dp,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Provider dropdown selector
                        Text(
                            text = stringResource(R.string.select_api_provider),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isProviderDropdownExpanded = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedProviderInfo?.displayName 
                                        ?: stringResource(R.string.select_api_provider),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                Icon(
                                    imageVector = if (isProviderDropdownExpanded) 
                                        Icons.Default.KeyboardArrowUp 
                                    else 
                                        Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isProviderDropdownExpanded) 
                                        stringResource(R.string.collapse) 
                                    else 
                                        stringResource(R.string.expand),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            DropdownMenu(
                                expanded = isProviderDropdownExpanded,
                                onDismissRequest = { isProviderDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                providersWithApiKeyPages.forEachIndexed { index, providerInfo ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = providerInfo.displayName,
                                                fontWeight = if (index == selectedProviderIndex) 
                                                    FontWeight.SemiBold 
                                                else 
                                                    FontWeight.Normal,
                                                color = if (index == selectedProviderIndex)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            selectedProviderIndex = index
                                            isProviderDropdownExpanded = false
                                            // Load the new provider's API key URL
                                            val newUrl = providerInfo.apiKeyUrl
                                            if (newUrl.isNotEmpty()) {
                                                isLoading = true
                                                webView.loadUrl(newUrl)
                                                currentUrl = newUrl
                                                urlBarText = newUrl
                                            }
                                        },
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    
                                    if (index < providersWithApiKeyPages.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            thickness = 0.5.dp
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // URL Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = urlBarText,
                                onValueChange = { urlBarText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(stringResource(R.string.enter_url)) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { loadUrlFromBar() })
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            IconButton(
                                onClick = { loadUrlFromBar() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = "Go to URL",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                // WebView
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { webView },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Loading indicator
                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
            
            // API Key Toast Card
            AnimatedVisibility(
                visible = showApiKeyToast,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        if (!isApiKeySaved) {
                            // API Key Input State
                            Text(
                                text = stringResource(R.string.api_key_detected),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = stringResource(
                                    R.string.api_key_detected_message,
                                    selectedProviderInfo?.displayName ?: "this provider"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.api_key)) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { 
                                        showApiKeyToast = false 
                                        apiKeyInput = ""
                                    }
                                ) {
                                    Text(stringResource(R.string.dialog_button_dismiss))
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Button(
                                    onClick = {
                                        scope.launch {
                                            saveApiKey()
                                        }
                                    },
                                    enabled = apiKeyInput.trim().isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.save))
                                }
                            }
                        } else {
                            // Saved State
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.api_key_saved),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    Text(
                                        text = stringResource(
                                            R.string.api_key_saved_message,
                                            selectedProviderInfo?.displayName ?: "the provider"
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        text = stringResource(R.string.go_to_config_message),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    TextButton(
                                        onClick = {
                                            showApiKeyToast = false
                                            onNavigateToModelConfig()
                                        }
                                    ) {
                                        Text(
                                            text = stringResource(R.string.open_configuration),
                                            textDecoration = TextDecoration.Underline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
