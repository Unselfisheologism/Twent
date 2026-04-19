package com.ai.assistance.operit.ui.features.renders

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
import com.ai.assistance.operit.data.model.MiniApp
import com.ai.assistance.operit.data.model.MiniAppType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RendersViewModel(private val context: android.content.Context) : ViewModel() {
    private val manager = MiniAppManager.getInstance(context)

    private val _renders = MutableStateFlow<List<MiniApp>>(emptyList())
    val renders = _renders.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _selectedRender = MutableStateFlow<MiniApp?>(null)
    val selectedRender = _selectedRender.asStateFlow()

    private val _showCode = MutableStateFlow(false)
    val showCode = _showCode.asStateFlow()

    init {
        viewModelScope.launch {
            manager.ensureServerRunning(context)
        }
        loadRenders()
    }

    fun loadRenders() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = manager.listMiniApps()
            result.onSuccess { apps ->
                // Filter only OpenUI renders and sort by creation time (newest first)
                _renders.value = apps
                    .filter { it.metadata["created_by"]?.contains("openui") == true }
                    .sortedByDescending { it.createdAt }
            }
            _isLoading.value = false
        }
    }

    fun selectRender(render: MiniApp) {
        viewModelScope.launch {
            // Ensure the mini-app server is running before showing preview
            manager.ensureServerRunning(context)
            _selectedRender.value = render
            _showCode.value = false
        }
    }

    fun closePreview() {
        _selectedRender.value = null
        _showCode.value = false
    }

    fun toggleCodeView() {
        _showCode.value = !_showCode.value
    }

    fun deleteRender(render: MiniApp) {
        viewModelScope.launch {
            manager.deleteMiniApp(render.id, render.type)
            if (_selectedRender.value?.id == render.id) {
                _selectedRender.value = null
            }
            loadRenders()
        }
    }

    fun getUrl(render: MiniApp): String {
        return manager.getMiniAppUrl(render)
    }

    fun getChatSessionName(render: MiniApp): String? {
        return render.metadata["chat_session_name"]
    }

    fun getCreatedTime(render: MiniApp): String {
        val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        return sdf.format(Date(render.createdAt))
    }

    fun getCode(render: MiniApp): String? {
        return render.metadata["openui_code"]
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RendersScreen(onGoBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: RendersViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RendersViewModel(context) as T
            }
        }
    )

    val renders by viewModel.renders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedRender by viewModel.selectedRender.collectAsState()
    val showCode by viewModel.showCode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Renders") },
                navigationIcon = {
                    IconButton(onClick = onGoBack) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                renders.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No renders yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Use the render_openui tool in chat to create one",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(renders, key = { it.id }) { render ->
                            RenderCard(
                                render = render,
                                viewModel = viewModel,
                                onClick = { viewModel.selectRender(render) }
                            )
                        }
                    }
                }
            }

            // Full preview overlay
            AnimatedVisibility(
                visible = selectedRender != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                selectedRender?.let { render ->
                    RenderPreview(
                        render = render,
                        viewModel = viewModel,
                        showCode = showCode,
                        onToggleCode = { viewModel.toggleCodeView() },
                        onClose = { viewModel.closePreview() }
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderCard(
    render: MiniApp,
    viewModel: RendersViewModel,
    onClick: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val chatSessionName = viewModel.getChatSessionName(render)
    val createdTime = viewModel.getCreatedTime(render)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = render.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (chatSessionName != null) {
                        Text(
                            text = "from: $chatSessionName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = createdTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }

            // Actions
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete render?") },
            text = { Text("\"${render.name}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRender(render)
                    showDeleteConfirm = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RenderPreview(
    render: MiniApp,
    viewModel: RendersViewModel,
    showCode: Boolean,
    onToggleCode: () -> Unit,
    onClose: () -> Unit
) {
    val url = viewModel.getUrl(render)
    val code = viewModel.getCode(render)
    val chatSessionName = viewModel.getChatSessionName(render)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = render.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (chatSessionName != null) {
                        Text(
                            text = "Chat: $chatSessionName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (code != null) {
                    IconButton(onClick = onToggleCode) {
                        Icon(
                            Icons.Outlined.Code,
                            contentDescription = "Toggle code",
                            tint = if (showCode) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()

            // Content
            if (showCode && code != null) {
                // Code view
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    item {
                        Text(
                            text = code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        )
                    }
                }
            } else {
                // WebView preview
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
