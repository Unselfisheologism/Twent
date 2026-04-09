package com.ai.assistance.operit.ui.features.miniapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.data.miniapp.MiniAppManager
import com.ai.assistance.operit.data.miniapp.PermissionMapper
import com.ai.assistance.operit.data.miniapp.MiniAppTemplates
import com.ai.assistance.operit.data.model.MiniApp
import com.ai.assistance.operit.data.model.MiniAppType
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for the MiniApp screen.
 */
class MiniAppViewModel : ViewModel() {

    private val _miniApps = mutableStateListOf<MiniApp>()
    val miniApps: List<MiniApp> = _miniApps

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _selectedApp = mutableStateOf<MiniApp?>(null)
    val selectedApp: State<MiniApp?> = _selectedApp

    private val _isViewingApp = mutableStateOf(false)
    val isViewingApp: State<Boolean> = _isViewingApp

    private val _filterType = mutableStateOf<MiniAppType?>(null)
    val filterType: State<MiniAppType?> = _filterType

    private var manager: MiniAppManager? = null

    fun initialize(ctx: Context) {
        manager = MiniAppManager.getInstance(ctx)
    }

    fun loadMiniApps() {
        val mgr = manager ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val currentFilter = _filterType.value
            mgr.listMiniApps(currentFilter).fold(
                onSuccess = { apps ->
                    _miniApps.clear()
                    _miniApps.addAll(apps)
                    _isLoading.value = false
                },
                onFailure = { e ->
                    _error.value = "Failed to load mini-apps: ${e.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    fun setFilter(type: MiniAppType?) {
        _filterType.value = type
        loadMiniApps()
    }

    fun openApp(app: MiniApp, ctx: Context) {
        // Start server FIRST (this is now a suspend function that waits for server to be ready)
        viewModelScope.launch {
            MiniAppManager.getInstance(ctx).ensureServerRunning(ctx)
            _selectedApp.value = app
            _isViewingApp.value = true
        }
    }

    fun closeApp() {
        _selectedApp.value = null
        _isViewingApp.value = false
    }

    fun deleteApp(app: MiniApp, onComplete: () -> Unit) {
        val mgr = manager ?: return
        viewModelScope.launch {
            mgr.deleteMiniApp(app.id, app.type).fold(
                onSuccess = {
                    _miniApps.remove(app)
                    onComplete()
                },
                onFailure = { e ->
                    _error.value = "Failed to delete: ${e.message}"
                }
            )
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun createFromTemplate(template: com.ai.assistance.operit.data.miniapp.MiniAppTemplate, ctx: Context, onComplete: () -> Unit) {
        val mgr = MiniAppManager.getInstance(ctx)
        viewModelScope.launch {
            val scaffold = com.ai.assistance.operit.data.miniapp.MiniAppScaffold.FromTemplate(
                template = template,
                name = template.name,
                type = MiniAppType.PERSISTENT
            )
            mgr.createMiniApp(scaffold).fold(
                onSuccess = {
                    loadMiniApps()
                    onComplete()
                },
                onFailure = { e ->
                    _error.value = "Failed to create: ${e.message}"
                }
            )
        }
    }
}

/**
 * Main Mini-App screen with list/grid view and app launcher.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniAppScreen(
    onGoBack: () -> Unit = {},
    viewModel: MiniAppViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return MiniAppViewModel() as T
        }
    })
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        viewModel.loadMiniApps()
        // Reset viewer state when screen is first shown (e.g. from sidebar navigation)
        viewModel.closeApp()
    }

    LaunchedEffect(viewModel.error.value) {
        viewModel.error.value?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (viewModel.isViewingApp.value && viewModel.selectedApp.value != null) {
                // Show mini-app name in TopAppBar when viewing
                TopAppBar(
                    title = { Text(viewModel.selectedApp.value!!.name) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.closeApp() }) {
                            Icon(Icons.Default.ArrowBack, "Back to Mini-Apps")
                        }
                    }
                )
            } else {
                // Show Mini-Apps title and filter when listing
                TopAppBar(
                    title = { Text("Mini-Apps") },
                    navigationIcon = {
                        IconButton(onClick = onGoBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        // Filter chip
                        FilterChip(
                            selected = viewModel.filterType.value != null,
                            onClick = {
                                viewModel.setFilter(if (viewModel.filterType.value == null) MiniAppType.PERSISTENT else null)
                            },
                            label = {
                                Text(
                                    if (viewModel.filterType.value == null) "All"
                                    else if (viewModel.filterType.value == MiniAppType.PERSISTENT) "Persistent"
                                    else "Ephemeral"
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (viewModel.filterType.value == null) Icons.Default.List else Icons.Default.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                )
            }
        }
    ) { padding ->
        if (viewModel.isViewingApp.value && viewModel.selectedApp.value != null) {
            // When viewing a mini-app, use full screen without parent padding
            Box(modifier = Modifier.fillMaxSize()) {
                MiniAppViewer(
                    miniApp = viewModel.selectedApp.value!!,
                    onGoBack = { viewModel.closeApp() }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Templates quick-create row
                TemplateQuickCreateRow(
                    onSelectTemplate = { template ->
                        viewModel.createFromTemplate(template, context) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Created: ${template.name}")
                            }
                        }
                    }
                )

                // Mini-app list
                if (viewModel.isLoading.value) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (viewModel.miniApps.isEmpty()) {
                    EmptyMiniAppsView()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(viewModel.miniApps, key = { it.id }) { app ->
                            MiniAppCard(
                                miniApp = app,
                                onClick = { viewModel.openApp(app, context) },
                                onDelete = { viewModel.deleteApp(app) {} }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateQuickCreateRow(
    onSelectTemplate: (com.ai.assistance.operit.data.miniapp.MiniAppTemplate) -> Unit
) {
    val templates = MiniAppTemplates.ALL

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            "Quick Create",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            templates.forEach { template ->
                SuggestionChip(
                    onClick = { onSelectTemplate(template) },
                    label = { Text(template.name, maxLines = 1) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
    }
}

@Composable
private fun MiniAppCard(
    miniApp: MiniApp,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = miniApp.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                miniApp.description?.let { desc ->
                    Text(
                        text = desc,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    // Type badge
                    Surface(
                        color = if (miniApp.type == MiniAppType.PERSISTENT)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = miniApp.type.name.lowercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (miniApp.type == MiniAppType.PERSISTENT)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    // Date
                    Text(
                        text = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(miniApp.createdAt)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Mini-App") },
            text = { Text("Are you sure you want to delete \"${miniApp.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyMiniAppsView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.Apps,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No mini-apps yet",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Ask the AI agent to create one, or use a template above.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Screen that displays a WebView loading the mini-app.
 * Note: Does NOT use Scaffold - the parent MiniAppScreen handles the TopAppBar.
 * This composable just fills the available space with the WebView.
 */
@Composable
fun MiniAppViewer(
    miniApp: MiniApp,
    onGoBack: () -> Unit
) {
    val context = LocalContext.current
    val url = MiniAppManager.getInstance(context).getMiniAppUrl(miniApp)
    val aiBridge = remember { MiniAppAiBridge(context) }

    // Log the URL for debugging
    LaunchedEffect(miniApp.id) {
        AppLogger.d("MiniAppViewer", "Opening mini-app: ${miniApp.name}, URL: $url")
        AppLogger.d("MiniAppViewer", "Mini-app ID: ${miniApp.id}, Type: ${miniApp.type}")
    }

    // WebView loading state
    var webViewLoaded by remember { mutableStateOf(false) }
    var webViewError by remember { mutableStateOf<String?>(null) }

    // Use a Box that fills the entire available space
    Box(modifier = Modifier.fillMaxSize()) {
        // WebView
        AndroidView(
            factory = { ctx ->
                val webView = createMiniAppWebView(
                    context = ctx,
                    aiBridge = aiBridge,
                    onPermissionGranted = { resources ->
                        AppLogger.d("MiniAppViewer", "Permissions granted: ${resources.joinToString()}")
                    },
                    onPermissionDenied = { resources ->
                        AppLogger.w("MiniAppViewer", "Permissions denied: ${resources.joinToString()}")
                    },
                    onNotify = { message ->
                        AppLogger.d("MiniAppViewer", "Mini-app notify: $message")
                    }
                )
                // Set up error detection
                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        webViewLoaded = true
                        webViewError = null
                    }

                    override fun onReceivedError(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            webViewError = error?.description?.toString() ?: "Failed to load"
                            AppLogger.e("MiniAppViewer", "WebView error: $webViewError for URL: $url")
                        }
                    }
                }
                // Wait for server to be ready (500ms already waited in ensureServerRunning)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    AppLogger.d("MiniAppViewer", "Loading URL: $url")
                    webView.loadUrl(url)
                }, 100)
                webView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading indicator overlay
        if (!webViewLoaded && webViewError == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading mini-app...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("URL: ${url.take(50)}...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }

        // Error state display
        if (webViewError != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Failed to load mini-app", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = webViewError!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        webViewError = null
                        webViewLoaded = false
                    }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionWarningBanner(
    missingPermissions: List<String>,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "This app needs: ${missingPermissions.joinToString(", ")}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    "Open Settings",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

@Composable
private fun VisionRequiredDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Vision Capability Required") },
        text = {
            Column {
                Text("This mini-app needs to send images to the AI model, but your currently configured model doesn't support vision.")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Please go to Settings → Models & Parameters Configuration and select a vision-enabled model (e.g. GPT-4o, Claude 3, Gemini Pro Vision).",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
