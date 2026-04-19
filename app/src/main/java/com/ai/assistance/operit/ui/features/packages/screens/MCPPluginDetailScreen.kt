package com.ai.assistance.operit.ui.features.packages.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubComment
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPMarketViewModel
import com.ai.assistance.operit.ui.features.packages.utils.MCPPluginParser
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPPluginDetailScreen(
    issue: GitHubIssue,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mcpRepository = remember { MCPRepository(context.applicationContext) }
    val viewModel: MCPMarketViewModel = viewModel(
        factory = MCPMarketViewModel.Factory(context.applicationContext, mcpRepository)
    )

    val githubAuth = remember { GitHubAuthPreferences.getInstance(context) }
    val isLoggedIn by githubAuth.isLoggedInFlow.collectAsState(initial = false)
    val currentUser by githubAuth.userInfoFlow.collectAsState(initial = null)

    val errorMessage by viewModel.errorMessage.collectAsState()

    val pluginInfo = remember(issue) {
        MCPPluginParser.parsePluginInfo(issue)
    }

    errorMessage?.let { error ->
        LaunchedEffect(error) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    CustomScaffold { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                PluginHeader(issue, pluginInfo, viewModel)
            }
            item {
                // Show mcp.json config with copy button
                McpConfigSection(issue = issue, viewModel = viewModel, context = context)
            }
            if (pluginInfo.description.isNotBlank()) {
                item {
                    PluginDescription(pluginInfo.description)
                }
            }
            item {
                PluginMetadata(issue = issue, pluginInfo = pluginInfo, viewModel = viewModel)
            }
        }
    }

    
}

@Composable
private fun PluginHeader(
    issue: GitHubIssue,
    pluginInfo: MCPPluginParser.ParsedPluginInfo,
    viewModel: MCPMarketViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = issue.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val avatarUrl by viewModel.userAvatarCache.collectAsState()
            LaunchedEffect(pluginInfo.repositoryOwner) {
                if (pluginInfo.repositoryOwner.isNotBlank()) {
                    viewModel.fetchUserAvatar(pluginInfo.repositoryOwner)
                }
            }
            val userAvatarUrl = avatarUrl[pluginInfo.repositoryOwner]
            
            if (userAvatarUrl != null) {
                Image(
                    painter = rememberAsyncImagePainter(userAvatarUrl),
                    contentDescription = stringResource(R.string.mcp_plugin_author),
                    modifier = Modifier.size(24.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Text(
                text = stringResource(
                    R.string.mcp_plugin_author,
                    pluginInfo.repositoryOwner.ifBlank { stringResource(R.string.mcp_plugin_unknown_author) }
                ),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 4.dp)
        ) {
             Image(
                painter = rememberAsyncImagePainter(issue.user.avatarUrl),
                contentDescription = stringResource(R.string.mcp_plugin_shared_by),
                modifier = Modifier.size(20.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Text(
                text = stringResource(R.string.mcp_plugin_shared_by, issue.user.login),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun McpConfigSection(
    issue: GitHubIssue,
    viewModel: MCPMarketViewModel,
    context: Context
) {
    var configDisplay by remember { mutableStateOf<MCPMarketViewModel.McpConfigDisplay?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(issue.number) {
        isLoading = true
        try {
            configDisplay = viewModel.getMcpConfigForDisplay(issue)
        } catch (e: Exception) {
            errorMessage = e.message
        }
        isLoading = false
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "mcp.json Config",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (configDisplay != null) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("mcp.json", configDisplay!!.configJson)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Config")
                }
            }
        }
        
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Failed to load config: $errorMessage",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            configDisplay != null -> {
                // Config code block
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = configDisplay!!.configJson,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Auth instructions
                if (configDisplay!!.authFields.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Authentication Required",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            configDisplay!!.authFields.forEach { field ->
                                Text(
                                    text = "• ${field.name}: ${field.description}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Replace the placeholder value with your actual API key after pasting into Config Import.",
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                
                // Usage instructions
                Text(
                    text = "Copy this config and paste it into the Config Import tab (📋 icon) in the MCP Management screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                Text(
                    text = "Could not generate config for this server.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun PluginDescription(description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.mcp_plugin_description_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginMetadata(
    issue: GitHubIssue,
    pluginInfo: MCPPluginParser.ParsedPluginInfo,
    viewModel: MCPMarketViewModel
) {
    val repositoryCache by viewModel.repositoryCache.collectAsState()
    val repositoryInfo = repositoryCache[pluginInfo.repositoryUrl]
    val installedPluginIds by viewModel.installedPluginIds.collectAsState()
    
    // 生成插件ID以检查安装状态
    val pluginId = remember(issue) {
        pluginInfo.title.replace("[^a-zA-Z0-9_]".toRegex(), "_")
    }
    val isInstalled = installedPluginIds.contains(pluginId)

    LaunchedEffect(pluginInfo.repositoryUrl) {
        if (pluginInfo.repositoryUrl.isNotBlank()) {
            viewModel.fetchRepositoryInfo(pluginInfo.repositoryUrl)
        }
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetadataChip(
                icon = Icons.Default.Info,
                text = if (issue.state == "open") stringResource(R.string.mcp_plugin_status_available) else stringResource(R.string.mcp_plugin_status_closed),
                color = if (issue.state == "open") Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isInstalled) {
                MetadataChip(
                    icon = Icons.Default.CheckCircle,
                    text = stringResource(R.string.installed),
                    color = Color(0xFF22C55E)
                )
            }
            if (repositoryInfo != null) {
                MetadataChip(
                    icon = Icons.Default.Star,
                    text = stringResource(R.string.mcp_plugin_stars, repositoryInfo.stargazers_count)
                )
            }
            MetadataChip(
                icon = Icons.Default.CalendarToday,
                text = stringResource(R.string.mcp_plugin_created_at, formatDate(issue.created_at))
            )
            MetadataChip(
                icon = Icons.Default.Update,
                text = stringResource(R.string.mcp_plugin_updated_at, formatDate(issue.updated_at))
            )
        }
    }
}

@Composable
private fun MetadataChip(icon: ImageVector, text: String, color: Color = LocalContentColor.current) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun PluginReactions(
    issue: GitHubIssue,
    viewModel: MCPMarketViewModel,
    currentUser: GitHubUser?
) {
    LaunchedEffect(issue.number) {
        viewModel.loadIssueReactions(issue.number)
    }

    val reactionsMap by viewModel.issueReactions.collectAsState()
    val reactions = reactionsMap[issue.number] ?: emptyList()
    val isReacting by viewModel.isReacting.collectAsState()
    
    val thumbsUpCount = remember(reactions) { reactions.count { it.content == "+1" } }
    val heartCount = remember(reactions) { reactions.count { it.content == "heart" } }

    var hasThumbsUp by remember { mutableStateOf(false) }
    var hasHeart by remember { mutableStateOf(false) }

    LaunchedEffect(reactions, currentUser) {
        currentUser?.let { user ->
            hasThumbsUp = reactions.any { it.content == "+1" && it.user.login == user.login }
            hasHeart = reactions.any { it.content == "heart" && it.user.login == user.login }
        }
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.mcp_plugin_community_feedback),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        if (currentUser == null) {
            Text(
                text = stringResource(R.string.mcp_plugin_login_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReactionButton(
                icon = Icons.Default.ThumbUp,
                count = thumbsUpCount,
                isReacted = hasThumbsUp,
                enabled = currentUser != null && !isReacting.contains(issue.number),
                onClick = {
                    if (!hasThumbsUp) viewModel.addReactionToIssue(issue.number, "+1")
                },
                reactedColor = MaterialTheme.colorScheme.primary
            )
            ReactionButton(
                icon = Icons.Default.Favorite,
                count = heartCount,
                isReacted = hasHeart,
                enabled = currentUser != null && !isReacting.contains(issue.number),
                onClick = {
                    if (!hasHeart) viewModel.addReactionToIssue(issue.number, "heart")
                },
                reactedColor = Color(0xFFE91E63)
            )
        }
    }
}

@Composable
private fun ReactionButton(
    icon: ImageVector,
    count: Int,
    isReacted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    reactedColor: Color
) {
    val buttonColors = if (isReacted) {
        ButtonDefaults.filledTonalButtonColors(containerColor = reactedColor.copy(alpha = 0.12f), contentColor = reactedColor)
    } else {
        ButtonDefaults.filledTonalButtonColors()
    }

    FilledTonalButton(
        onClick = onClick,
        enabled = enabled && !isReacted,
        colors = buttonColors,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            AnimatedContent(targetState = count, label = "reactionCount") { targetCount ->
                Text(
                    text = targetCount.toString(),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CommentsHeader(commentCount: Int, isLoading: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.mcp_plugin_comments, commentCount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.mcp_plugin_refresh_comments))
            }
        }
    }
}

@Composable
private fun CommentCard(comment: GitHubComment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = rememberAsyncImagePainter(comment.user.avatarUrl),
                contentDescription = stringResource(R.string.mcp_plugin_shared_by),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = comment.user.login,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatDate(comment.created_at),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = comment.body,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun EmptyCommentsCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Forum,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                stringResource(R.string.mcp_plugin_no_comments),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.mcp_plugin_be_first_comment),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommentInputDialog(
    commentText: String,
    onCommentTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onPost: () -> Unit,
    isPosting: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mcp_plugin_add_comment)) },
        text = {
            OutlinedTextField(
                value = commentText,
                onValueChange = onCommentTextChange,
                placeholder = { Text(stringResource(R.string.mcp_plugin_comment_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                enabled = !isPosting
            )
        },
        confirmButton = {
            Button(
                onClick = onPost,
                enabled = commentText.isNotBlank() && !isPosting
            ) {
                if (isPosting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.mcp_plugin_post_comment))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.mcp_plugin_cancel))
            }
        }
    )
}

private fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        dateString
    }
} 

