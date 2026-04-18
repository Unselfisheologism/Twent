package com.ai.assistance.operit.ui.features.packages.screens

import com.ai.assistance.operit.util.AppLogger
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.EnvVar
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.preferences.EnvPreferences
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.ui.features.packages.screens.mcp.components.MCPEnvironmentVariablesDialog
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.features.packages.components.EmptyState
import com.ai.assistance.operit.ui.features.packages.components.PackageTab
import com.ai.assistance.operit.ui.features.packages.components.dialogs.ImportExportDialog
import com.ai.assistance.operit.ui.features.packages.components.dialogs.ExportAllDataDialog
import com.ai.assistance.operit.ui.features.packages.dialogs.PackageDetailsDialog
import com.ai.assistance.operit.ui.features.packages.dialogs.ScriptExecutionDialog
import com.ai.assistance.operit.ui.features.packages.lists.PackagesList
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PackageManagerScreen(
    onNavigateToMCPMarket: () -> Unit = {},
    onNavigateToSkillMarket: () -> Unit = {},
    onNavigateToMCPDetail: ((com.ai.assistance.operit.data.api.GitHubIssue) -> Unit)? = null
) {
    val context = LocalContext.current
    val packageManager = remember {
        PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    }
    val scope = rememberCoroutineScope()
    val mcpRepository = remember { MCPRepository(context) }
    val skillRepository = remember { SkillRepository.getInstance(context.applicationContext) }

    val envPreferences = remember { EnvPreferences.getInstance(context) }

    // State for available and imported packages
    val availablePackages = remember { mutableStateOf<Map<String, ToolPackage>>(emptyMap()) }
    val importedPackages = remember { mutableStateOf<List<String>>(emptyList()) }
    // UI展示用的导入状态列表，与后端状态分离
    val visibleImportedPackages = remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // State for selected package and showing details
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    // State for script execution
    var showScriptExecution by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf<PackageTool?>(null) }
    var scriptExecutionResult by remember { mutableStateOf<ToolResult?>(null) }

    // State for snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Tab selection state - default to SKILLS since PACKAGES is removed
    var selectedTab by rememberSaveable { mutableStateOf(PackageTab.SKILLS) }

    // Environment variables dialog state
    var showEnvDialog by remember { mutableStateOf(false) }
    var envVariables by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val packageLoadErrors = remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showPackageLoadErrorsDialog by remember { mutableStateOf(false) }
    
    // Import/Export dialog state
    var showImportExportDialog by remember { mutableStateOf(false) }
    var showExportAllDataDialog by remember { mutableStateOf(false) }

    val requiredEnvByPackage by remember {
        derivedStateOf {
            val packagesMap = availablePackages.value
            val imported = importedPackages.value.toSet()

            imported
                .mapNotNull { packageName -> packagesMap[packageName] }
                .sortedBy { it.name }
                .associate { toolPackage ->
                    toolPackage.name to toolPackage.env
                }
                .filterValues { envVars -> envVars.isNotEmpty() }
        }
    }

    val requiredEnvKeys by remember {
        derivedStateOf {
            requiredEnvByPackage.values
                .flatten()
                .map { it.name }
                .toSet()
                .toList()
                .sorted()
        }
    }

    // File picker launcher for importing external packages
    val packageFilePicker =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                scope.launch {
                    try {
                        val fileName: String? =
                            withContext(Dispatchers.IO) {
                                var name: String? = null
                                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                    val nameIndex = cursor.getColumnIndex("_display_name")
                                    if (cursor.moveToFirst() && nameIndex >= 0) {
                                        name = cursor.getString(nameIndex)
                                    }
                                }
                                name
                            }

                        if (fileName == null) {
                            snackbarHostState.showSnackbar(context.getString(R.string.no_filename))
                            return@launch
                        }

                        // 根据当前选中的标签页处理不同类型的文件
                        when (selectedTab) {
                            else -> {
                                snackbarHostState.showSnackbar(context.getString(R.string.current_tab_not_support_import))
                            }
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        AppLogger.e("PackageManagerScreen", "Failed to import file", e)
                        snackbarHostState.showSnackbar(
                            message = context.getString(
                                R.string.import_failed,
                                e.message
                            )
                        )
                    }
                }
            }

        }

    // Load packages
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val loadResult =
                withContext(Dispatchers.IO) {
                    val available = packageManager.getAvailablePackages(forceRefresh = true)
                    val imported = packageManager.getImportedPackages()
                    val errors = packageManager.getPackageLoadErrors()
                    Triple(available, imported, errors)
                }

            availablePackages.value = loadResult.first
            importedPackages.value = loadResult.second
            packageLoadErrors.value = loadResult.third
            // 初始化UI显示状态
            visibleImportedPackages.value = importedPackages.value.toList()
        } catch (e: Exception) {
            AppLogger.e("PackageManagerScreen", "Failed to load packages", e)
        } finally {
            isLoading = false
        }
    }

    CustomScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.package_manager_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    // Import/Export All Data button
                    IconButton(onClick = { showExportAllDataDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = stringResource(R.string.import_export_export_all_title)
                        )
                    }
                    // Import/Export button
                    IconButton(onClick = { showImportExportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.ImportExport,
                            contentDescription = stringResource(R.string.import_export_title)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    snackbarData = data
                )
            }
        },
        floatingActionButton = {
            // FAB removed since PACKAGES tab was removed
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
        ) {
            // 优化标签栏布局 - 直接使用TabRow，不再使用Card包裹，移除边距完全贴满
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth(),
                divider = {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                },
                indicator = { tabPositions ->
                    if (selectedTab.ordinal < tabPositions.size) {
                        TabRowDefaults.PrimaryIndicator(
                            modifier =
                                Modifier.tabIndicatorOffset(
                                    tabPositions[selectedTab.ordinal]
                                ),
                            height = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                // Skills标签
                Tab(
                    selected = selectedTab == PackageTab.SKILLS,
                    onClick = { selectedTab = PackageTab.SKILLS },
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == PackageTab.SKILLS)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            context.getString(R.string.skills),
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = false,
                            color = if (selectedTab == PackageTab.SKILLS)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // MCP标签
                Tab(
                    selected = selectedTab == PackageTab.MCP,
                    onClick = { selectedTab = PackageTab.MCP },
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == PackageTab.MCP)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            context.getString(R.string.mcp),
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = false,
                            color = if (selectedTab == PackageTab.MCP)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 内容区域添加水平padding
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when (selectedTab) {
                    PackageTab.SKILLS -> {
                        SkillManagerScreen(
                            skillRepository = skillRepository,
                            snackbarHostState = snackbarHostState,
                            onNavigateToSkillMarket = onNavigateToSkillMarket
                        )
                    }

                    PackageTab.MCP -> {
                        MCPConfigScreen(
                            onNavigateToMCPMarket = onNavigateToMCPMarket
                        )
                    }
                }
            }

            // Package Details Dialog
            if (showDetails && selectedPackage != null) {
                PackageDetailsDialog(
                    packageName = selectedPackage!!,
                    packageDescription = availablePackages.value[selectedPackage]?.description?.resolve(context)
                        ?: "",
                    toolPackage = availablePackages.value[selectedPackage],
                    packageManager = packageManager,
                    onRunScript = { tool ->
                        selectedTool = tool
                        showScriptExecution = true
                    },
                    onDismiss = { showDetails = false },
                    onPackageDeleted = {
                        showDetails = false
                        scope.launch {
                            AppLogger.d(
                                "PackageManagerScreen",
                                "onPackageDeleted callback triggered. Refreshing package lists."
                            )
                            // Refresh the package lists after deletion
                            isLoading = true
                            val loadResult =
                                withContext(Dispatchers.IO) {
                                    val available = packageManager.getAvailablePackages(forceRefresh = true)
                                    val imported = packageManager.getImportedPackages()
                                    available to imported
                                }

                            availablePackages.value = loadResult.first
                            importedPackages.value = loadResult.second
                            visibleImportedPackages.value = importedPackages.value.toList()
                            AppLogger.d(
                                "PackageManagerScreen",
                                "Lists refreshed. Available: ${availablePackages.value.keys}, Imported: ${importedPackages.value}"
                            )
                            isLoading = false
                            snackbarHostState.showSnackbar("Package deleted successfully.")
                        }
                    }
                )
            }

            // Script Execution Dialog
            if (showScriptExecution && selectedTool != null && selectedPackage != null) {
                ScriptExecutionDialog(
                    packageName = selectedPackage!!,
                    tool = selectedTool!!,
                    packageManager = packageManager,
                    initialResult = scriptExecutionResult,
                    onExecuted = { result -> scriptExecutionResult = result },
                    onDismiss = {
                        showScriptExecution = false
                        scriptExecutionResult = null
                    }
                )
            }

            // Environment Variables Dialog for packages
            if (showEnvDialog) {
                PackageEnvironmentVariablesDialog(
                    requiredEnvByPackage = requiredEnvByPackage,
                    currentValues = envVariables,
                    onDismiss = { showEnvDialog = false },
                    onConfirm = { updated ->
                        val merged = envPreferences.getAllEnv().toMutableMap().apply {
                            updated.forEach { (key, value) ->
                                if (value.isBlank()) {
                                    remove(key)
                                } else {
                                    this[key] = value
                                }
                            }
                        }
                        envPreferences.setAllEnv(merged)
                        envVariables = updated
                        showEnvDialog = false
                    }
                )
            }

            if (showPackageLoadErrorsDialog) {
                PackageLoadErrorsDialog(
                    errors = packageLoadErrors.value,
                    onDismiss = { showPackageLoadErrorsDialog = false }
                )
            }

            // Import/Export Dialogs
            if (showImportExportDialog) {
                ImportExportDialog(
                    onDismiss = { showImportExportDialog = false },
                    onExportComplete = { path ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.import_export_exported_to, path)
                            )
                        }
                    },
                    onImportComplete = { message ->
                        scope.launch {
                            snackbarHostState.showSnackbar(message = message)
                        }
                    }
                )
            }

            if (showExportAllDataDialog) {
                ExportAllDataDialog(
                    onDismiss = { showExportAllDataDialog = false },
                    onExportComplete = { path ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = context.getString(R.string.import_export_exported_to, path)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PackageLoadErrorsDialog(
    errors: Map<String, String>,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.error_occurred_simple)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState)
            ) {
                errors.toSortedMap().forEach { (packageName, errorText) ->
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ok))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PackageEnvironmentVariablesDialog(
    requiredEnvByPackage: Map<String, List<EnvVar>>,
    currentValues: Map<String, String>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    
    val requiredEnvKeys = remember(requiredEnvByPackage) {
        requiredEnvByPackage.values
            .flatten()
            .map { it.name }
            .toSet()
            .toList()
            .sorted()
    }

    val editableValuesState =
        remember(requiredEnvKeys, currentValues) {
            mutableStateOf(
                requiredEnvKeys.associateWith { key: String -> currentValues[key] ?: "" }
            )
        }
    val editableValues by editableValuesState

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.pkg_config_env_vars)) },
        text = {
            if (requiredEnvKeys.isEmpty()) {
                Text(
                    text = stringResource(R.string.pkg_no_env_vars),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    requiredEnvByPackage.forEach { (packageName, envVars) ->
                        stickyHeader(key = "header:$packageName") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(24.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = packageName.first().uppercaseChar().toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Text(
                                    text = packageName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
 
                        items(
                            items = envVars,
                            key = { envVar -> "${packageName}:${envVar.name}" }
                        ) { envVar ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = envVar.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            // 显示是否必需的标记
                                            if (envVar.required) {
                                                Surface(
                                                    modifier = Modifier.size(16.dp),
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.error
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.size(16.dp)
                                                    ) {
                                                        Text(
                                                            text = "!",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onError
                                                        )
                                                    }
                                                }
                                            } else {
                                                Surface(
                                                    modifier = Modifier.size(16.dp),
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.size(16.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "Optional",
                                                            modifier = Modifier.size(10.dp),
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        // 显示描述
                                        val description = envVar.description.resolve(context)
                                        if (description.isNotBlank()) {
                                            Text(
                                                text = description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                // 显示默认值（如果有）
                                if (envVar.defaultValue != null) {
                                    Text(
                                        text = stringResource(R.string.pkg_default, envVar.defaultValue),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = editableValues[envVar.name] ?: "",
                                onValueChange = { newValue ->
                                    val currentMap = editableValuesState.value
                                    val newMap = currentMap.toMutableMap()
                                    newMap[envVar.name] = newValue
                                    editableValuesState.value = newMap
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        text = if (envVar.required) stringResource(R.string.pkg_input_required) else stringResource(R.string.pkg_input_optional),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                shape = RoundedCornerShape(6.dp),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(editableValues) }) {
                Text(text = stringResource(R.string.pkg_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.pkg_cancel))
            }
        }
    )
}

@Composable
private fun PackageListItemWithTag(
    packageName: String,
    toolPackage: ToolPackage?,
    isImported: Boolean,
    categoryTag: String?,
    category: String, // 新增分类参数
    categoryColor: Color,
    onPackageClick: () -> Unit,
    onToggleImport: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Surface(
        onClick = onPackageClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // 分类标签（仅在有标签时显示）
            if (categoryTag != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .width(3.dp)
                            .height(12.dp),
                        color = categoryColor,
                        shape = RoundedCornerShape(1.5.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = categoryTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 主要内容行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = if (categoryTag != null) 4.dp else 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (category) {
                        "Automatic" -> Icons.Default.AutoMode
                        "Experimental" -> Icons.Default.Science
                        "Other" -> Icons.Default.Widgets
                        else -> Icons.Default.Extension
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = categoryColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = toolPackage?.name ?: packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val description = toolPackage?.description?.resolve(context).orEmpty()
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isImported,
                    onCheckedChange = onToggleImport,
                    modifier = Modifier.scale(0.8f)
                )
            }
        }
    }
}
