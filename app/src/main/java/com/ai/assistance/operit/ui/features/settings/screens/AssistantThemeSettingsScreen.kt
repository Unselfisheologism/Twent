package com.ai.assistance.operit.ui.features.settings.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.AgentAvatars
import com.ai.assistance.operit.data.model.AgentVoices
import com.ai.assistance.operit.data.model.AssistantIconStyles
import com.ai.assistance.operit.data.model.AssistantThemes
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.launch

/**
 * Assistant Theme Settings Screen
 * Allows users to customize the AI assistant/overlay appearance including:
 * - Theme selection (light/dark/follow system)
 * - Predefined theme packs (fun AI personalities)
 * - Custom colors
 * - Icon style
 * - Chat page wallpaper
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantThemeSettingsScreen() {
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    // Theme mode state
    val assistantThemeMode by preferencesManager.assistantThemeMode.collectAsState(
        initial = UserPreferencesManager.ASSISTANT_THEME_MODE_FOLLOW_SYSTEM
    )
    val assistantCustomThemeId by preferencesManager.assistantCustomThemeId.collectAsState(
        initial = UserPreferencesManager.ASSISTANT_THEME_DEFAULT
    )

    // Chat page wallpaper state
    val useChatPageWallpaper by preferencesManager.useChatPageWallpaper.collectAsState(initial = false)
    val chatPageWallpaperUri by preferencesManager.chatPageWallpaperUri.collectAsState(initial = null)
    val chatPageWallpaperOpacity by preferencesManager.chatPageWallpaperOpacity.collectAsState(initial = 0.3f)
    val chatPageWallpaperBlur by preferencesManager.chatPageWallpaperBlur.collectAsState(initial = false)
    val chatPageWallpaperBlurRadius by preferencesManager.chatPageWallpaperBlurRadius.collectAsState(initial = 10f)

    // Icon style state
    val assistantIconStyle by preferencesManager.assistantIconStyle.collectAsState(
        initial = UserPreferencesManager.ASSISTANT_ICON_DEFAULT
    )

    // Custom colors state
    val useAssistantCustomColors by preferencesManager.useAssistantCustomColors.collectAsState(initial = false)
    val assistantCustomPrimaryColor by preferencesManager.assistantCustomPrimaryColor.collectAsState(initial = null)
    val assistantCustomSecondaryColor by preferencesManager.assistantCustomSecondaryColor.collectAsState(initial = null)

    // Agent customization state (voice/name/avatar)
    val agentVoice by preferencesManager.agentVoice.collectAsState(
        initial = UserPreferencesManager.AGENT_VOICE_DEFAULT
    )
    val agentName by preferencesManager.agentName.collectAsState(
        initial = UserPreferencesManager.AGENT_NAME_DEFAULT
    )
    val agentAvatar by preferencesManager.agentAvatar.collectAsState(
        initial = UserPreferencesManager.AGENT_AVATAR_DEFAULT
    )
    val agentAvatarUri by preferencesManager.agentAvatarUri.collectAsState(initial = null)

    // Agent name editing state
    var agentNameInput by remember(agentName) { mutableStateOf(agentName) }
    var isEditingName by remember { mutableStateOf(false) }

    // Simple color picker state
    var showSimpleColorPicker by remember { mutableStateOf(false) }
    var colorPickerTarget by remember { mutableStateOf("primary") }

    // Predefined colors for simple picker
    val predefinedColors = listOf(
        0xFF6650a4, 0xFF2196F3, 0xFF4CAF50, 0xFFFF9800,
        0xFFF44336, 0xFF9C27B0, 0xFF00BCD4, 0xFF8BC34A,
        0xFFFFEB3B, 0xFFFF5722, 0xFF795548, 0xFF607D8B,
        0xFFE91E63, 0xFF3F51B5, 0xFF009688, 0xFFFFC107
    )

    // Image picker launcher for chat wallpaper
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Store the URI directly (similar to how ThemeSettingsScreen works)
            scope.launch {
                preferencesManager.saveAssistantThemeSettings(
                    useChatPageWallpaper = true,
                    chatPageWallpaperUri = it.toString(),
                    chatPageWallpaperMediaType = UserPreferencesManager.MEDIA_TYPE_IMAGE
                )
            }
        }
    }

    // Image picker launcher for agent avatar
    val avatarImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                preferencesManager.saveAssistantThemeSettings(
                    agentAvatar = UserPreferencesManager.AGENT_AVATAR_CUSTOM,
                    agentAvatarUri = it.toString()
                )
            }
        }
    }

    var expandedVoiceSelector by remember { mutableStateOf(false) }
    var expandedThemeMode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = stringResource(R.string.assistant_theme_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = stringResource(R.string.assistant_theme_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Agent Personalization Section (Voice/Name/Avatar)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.agent_personalization),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.agent_personalization_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Agent Name
                Text(
                    text = stringResource(R.string.agent_name),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (isEditingName) {
                    OutlinedTextField(
                        value = agentNameInput,
                        onValueChange = { agentNameInput = it },
                        placeholder = { Text(stringResource(R.string.agent_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                agentNameInput = agentName
                                isEditingName = false
                            }
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    preferencesManager.saveAssistantThemeSettings(
                                        agentName = agentNameInput.ifBlank { UserPreferencesManager.AGENT_NAME_DEFAULT }
                                    )
                                }
                                isEditingName = false
                            }
                        ) {
                            Text("Save")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = agentName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                agentNameInput = agentName
                                isEditingName = true
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit name")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Agent Voice Selector
                Text(
                    text = stringResource(R.string.agent_voice),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = expandedVoiceSelector,
                    onExpandedChange = { expandedVoiceSelector = it }
                ) {
                    val selectedVoice = AgentVoices.getVoiceById(agentVoice)
                    OutlinedTextField(
                        value = selectedVoice.name,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVoiceSelector) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedVoiceSelector,
                        onDismissRequest = { expandedVoiceSelector = false }
                    ) {
                        AgentVoices.ALL_VOICES.forEach { voice ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(voice.emoji)
                                        Text(voice.name)
                                    }
                                },
                                onClick = {
                                    scope.launch {
                                        preferencesManager.saveAssistantThemeSettings(
                                            agentVoice = voice.id
                                        )
                                    }
                                    expandedVoiceSelector = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Agent Avatar Selector
                Text(
                    text = stringResource(R.string.agent_avatar),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(AgentAvatars.ALL_AVATARS.filter { it.id != "custom" }) { avatar ->
                        val isSelected = agentAvatar == avatar.id
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    scope.launch {
                                        preferencesManager.saveAssistantThemeSettings(
                                            agentAvatar = avatar.id,
                                            agentAvatarUri = null
                                        )
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        2.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = avatar.emoji,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = avatar.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Custom avatar option
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                avatarImagePickerLauncher.launch("image/*")
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        2.dp,
                                        if (agentAvatar == "custom") MaterialTheme.colorScheme.primary
                                        else Color.Transparent,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (agentAvatarUri != null) {
                                    androidx.compose.foundation.Image(
                                        painter = rememberAsyncImagePainter(model = agentAvatarUri),
                                        contentDescription = "Custom avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Add custom avatar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.agent_avatar_choose_image),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Remove custom avatar button
                if (agentAvatarUri != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                preferencesManager.saveAssistantThemeSettings(
                                    agentAvatar = UserPreferencesManager.AGENT_AVATAR_DEFAULT,
                                    agentAvatarUri = null
                                )
                            }
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.agent_avatar_remove_custom))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Theme Mode Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.assistant_theme_mode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Theme mode dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedThemeMode,
                    onExpandedChange = { expandedThemeMode = it }
                ) {
                    OutlinedTextField(
                        value = when (assistantThemeMode) {
                            UserPreferencesManager.ASSISTANT_THEME_MODE_LIGHT -> stringResource(R.string.assistant_theme_light)
                            UserPreferencesManager.ASSISTANT_THEME_MODE_DARK -> stringResource(R.string.assistant_theme_dark)
                            else -> stringResource(R.string.assistant_theme_follow_system)
                        },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedThemeMode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedThemeMode,
                        onDismissRequest = { expandedThemeMode = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.assistant_theme_follow_system)) },
                            onClick = {
                                scope.launch {
                                    preferencesManager.saveAssistantThemeSettings(
                                        assistantThemeMode = UserPreferencesManager.ASSISTANT_THEME_MODE_FOLLOW_SYSTEM
                                    )
                                }
                                expandedThemeMode = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.assistant_theme_light)) },
                            onClick = {
                                scope.launch {
                                    preferencesManager.saveAssistantThemeSettings(
                                        assistantThemeMode = UserPreferencesManager.ASSISTANT_THEME_MODE_LIGHT
                                    )
                                }
                                expandedThemeMode = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.assistant_theme_dark)) },
                            onClick = {
                                scope.launch {
                                    preferencesManager.saveAssistantThemeSettings(
                                        assistantThemeMode = UserPreferencesManager.ASSISTANT_THEME_MODE_DARK
                                    )
                                }
                                expandedThemeMode = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Theme Selection Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.assistant_theme_select),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Theme grid
                AssistantThemeSelector(
                    selectedThemeId = assistantCustomThemeId,
                    onThemeSelected = { themeId ->
                        scope.launch {
                            preferencesManager.saveAssistantThemeSettings(
                                assistantCustomThemeId = themeId
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Icon Style Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.assistant_icon_style),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Icon style selector
                AssistantIconStyleSelector(
                    selectedStyle = assistantIconStyle,
                    onStyleSelected = { styleId ->
                        scope.launch {
                            preferencesManager.saveAssistantThemeSettings(
                                assistantIconStyle = styleId
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom Colors Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.assistant_custom_colors),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Enable custom colors switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.assistant_use_custom_colors),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = useAssistantCustomColors,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                preferencesManager.saveAssistantThemeSettings(
                                    useAssistantCustomColors = enabled
                                )
                            }
                        }
                    )
                }

                if (useAssistantCustomColors) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary color picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                colorPickerTarget = "primary"
                                showSimpleColorPicker = true
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.assistant_primary_color),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    assistantCustomPrimaryColor?.let { Color(it) }
                                        ?: MaterialTheme.colorScheme.primary
                                )
                                .border(1.dp, Color.Gray, CircleShape)
                        )
                    }

                    // Secondary color picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                colorPickerTarget = "secondary"
                                showSimpleColorPicker = true
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.assistant_secondary_color),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    assistantCustomSecondaryColor?.let { Color(it) }
                                        ?: MaterialTheme.colorScheme.secondary
                                )
                                .border(1.dp, Color.Gray, CircleShape)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chat Page Wallpaper Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.chat_page_wallpaper_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.chat_page_wallpaper_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Enable wallpaper switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.chat_page_use_wallpaper),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = useChatPageWallpaper,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                preferencesManager.saveAssistantThemeSettings(
                                    useChatPageWallpaper = enabled
                                )
                            }
                        }
                    )
                }

                if (useChatPageWallpaper) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Wallpaper preview
                    if (chatPageWallpaperUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            androidx.compose.foundation.Image(
                                painter = rememberAsyncImagePainter(model = chatPageWallpaperUri),
                                contentDescription = "Wallpaper preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Remove button
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        preferencesManager.saveAssistantThemeSettings(
                                            useChatPageWallpaper = false,
                                            chatPageWallpaperUri = null
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color.White
                                )
                            }
                        }
                    } else {
                        // Select wallpaper button
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.theme_select_image))
                        }
                    }

                    if (chatPageWallpaperUri != null) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Opacity slider
                        Text(
                            text = stringResource(R.string.chat_page_wallpaper_opacity, (chatPageWallpaperOpacity * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = chatPageWallpaperOpacity,
                            onValueChange = { value ->
                                scope.launch {
                                    preferencesManager.saveAssistantThemeSettings(
                                        chatPageWallpaperOpacity = value
                                    )
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Blur switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.chat_page_wallpaper_blur),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = chatPageWallpaperBlur,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        preferencesManager.saveAssistantThemeSettings(
                                            chatPageWallpaperBlur = enabled
                                        )
                                    }
                                }
                            )
                        }

                        if (chatPageWallpaperBlur) {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Blur radius slider
                            Text(
                                text = stringResource(R.string.chat_page_wallpaper_blur_radius),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = chatPageWallpaperBlurRadius,
                                onValueChange = { value ->
                                    scope.launch {
                                        preferencesManager.saveAssistantThemeSettings(
                                            chatPageWallpaperBlurRadius = value
                                        )
                                    }
                                },
                                valueRange = 0f..30f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }


    // Simple color picker dialog
    if (showSimpleColorPicker) {
        AlertDialog(
            onDismissRequest = { showSimpleColorPicker = false },
            title = {
                Text(
                    if (colorPickerTarget == "primary")
                        stringResource(R.string.assistant_primary_color)
                    else
                        stringResource(R.string.assistant_secondary_color)
                )
            },
            text = {
                Column {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(predefinedColors) { colorValue ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorValue))
                                    .border(
                                        2.dp,
                                        if ((colorPickerTarget == "primary" && assistantCustomPrimaryColor == colorValue.toInt()) ||
                                            (colorPickerTarget == "secondary" && assistantCustomSecondaryColor == colorValue.toInt())
                                        ) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable {
                                        scope.launch {
                                            val colorInt = colorValue.toInt()
                                            if (colorPickerTarget == "primary") {
                                                preferencesManager.saveAssistantThemeSettings(
                                                    assistantCustomPrimaryColor = colorInt
                                                )
                                            } else {
                                                preferencesManager.saveAssistantThemeSettings(
                                                    assistantCustomSecondaryColor = colorInt
                                                )
                                            }
                                        }
                                        showSimpleColorPicker = false
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSimpleColorPicker = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun AssistantThemeSelector(
    selectedThemeId: String,
    onThemeSelected: (String) -> Unit
) {
    val themes = AssistantThemes.ALL_THEMES

    Column {
        themes.chunked(2).forEach { rowThemes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowThemes.forEach { theme ->
                    AssistantThemeItem(
                        theme = theme,
                        isSelected = theme.id == selectedThemeId,
                        onClick = { onThemeSelected(theme.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill empty space if odd number
                if (rowThemes.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AssistantThemeItem(
    theme: com.ai.assistance.operit.data.model.AssistantTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(12.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(theme.backgroundColor)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Color preview circles
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(theme.primaryColor))
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(theme.secondaryColor))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = theme.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = Color(theme.onBackgroundColor)
            )

            if (theme.isDarkMode) {
                Text(
                    text = "🌙",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AssistantIconStyleSelector(
    selectedStyle: String,
    onStyleSelected: (String) -> Unit
) {
    val styles = AssistantIconStyles.ALL_STYLES

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(styles) { style ->
            Card(
                modifier = Modifier
                    .clickable { onStyleSelected(style.id) }
                    .then(
                        if (style.id == selectedStyle) Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(12.dp)
                        ) else Modifier
                    ),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = style.emoji,
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = style.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (style.id == selectedStyle) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}