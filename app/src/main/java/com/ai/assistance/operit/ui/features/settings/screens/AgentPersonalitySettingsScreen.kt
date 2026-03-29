package com.ai.assistance.operit.ui.features.settings.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.AgentPersonalities
import com.ai.assistance.operit.data.model.AgentPersonality
import com.ai.assistance.operit.data.model.LocalVoiceVariants
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentPersonalitySettingsScreen() {
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    val personalityId by preferencesManager.agentPersonalityId.collectAsState(initial = "default")
    val customName by preferencesManager.customAgentName.collectAsState(initial = null)
    val customAvatarUri by preferencesManager.customAgentAvatarUri.collectAsState(initial = null)
    val voiceVariantId by preferencesManager.agentVoiceVariantId.collectAsState(initial = "default")
    val voicePitch by preferencesManager.agentVoicePitch.collectAsState(initial = 1.0f)
    val voiceRate by preferencesManager.agentVoiceRate.collectAsState(initial = 1.0f)
    val useLocalTTS by preferencesManager.agentUseLocalTTS.collectAsState(initial = true)
    val ttsVoiceId by preferencesManager.agentTTSVoiceId.collectAsState(initial = null)
    val customEmoji by preferencesManager.agentCustomEmoji.collectAsState(initial = null)

    var customNameInput by remember(customName) { mutableStateOf(customName ?: "") }
    var customEmojiInput by remember(customEmoji) { mutableStateOf(customEmoji ?: "") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                preferencesManager.saveAgentPersonalitySettings(customAgentAvatarUri = it.toString())
            }
        }
    }

    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.agent_personality_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = stringResource(R.string.agent_personality_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.agent_personality_tab_ai)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.agent_personality_tab_voice)) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(stringResource(R.string.agent_personality_tab_appearance)) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            0 -> PersonalityTab(
                selectedPersonalityId = personalityId,
                customName = customNameInput,
                onCustomNameChange = { customNameInput = it },
                onPersonalitySelect = { id ->
                    scope.launch {
                        preferencesManager.saveAgentPersonalitySettings(personalityId = id)
                    }
                },
                onSaveCustomName = {
                    scope.launch {
                        preferencesManager.saveAgentPersonalitySettings(
                            customAgentName = customNameInput.takeIf { it.isNotBlank() }
                        )
                    }
                }
            )
            1 -> VoiceTab(
                voiceVariantId = voiceVariantId,
                voicePitch = voicePitch,
                voiceRate = voiceRate,
                useLocalTTS = useLocalTTS,
                ttsVoiceId = ttsVoiceId,
                onVoiceVariantSelect = { id ->
                    scope.launch {
                        preferencesManager.saveAgentPersonalitySettings(voiceVariantId = id)
                    }
                },
                onPitchChange = { pitch ->
                    scope.launch {
                        preferencesManager.saveAgentPersonalitySettings(voicePitch = pitch)
                    }
                },
                onRateChange = { rate ->
                    scope.launch {
                        preferencesManager.saveAgentPersonalitySettings(voiceRate = rate)
                    }
                },
                onUseLocalTTSChange = { use ->
                    scope.launch {
                        preferencesManager.saveAgentPersonalitySettings(useLocalTTS = use)
                    }
                },
                onTTSVoiceChange = { voiceId ->
                    scope.launch {
                        preferencesManager.saveAgentPersonalitySettings(ttsVoiceId = voiceId)
                    }
                }
            )
            2 -> AppearanceTab(
                customAvatarUri = customAvatarUri,
                customEmoji = customEmojiInput,
                onEmojiChange = { customEmojiInput = it },
                onSelectAvatar = { imagePickerLauncher.launch("image/*") },
                onRemoveAvatar = {
                    scope.launch {
                        preferencesManager.saveAgentPersonalitySettings(customAgentAvatarUri = null)
                    }
                },
                onSaveEmoji = {
                    scope.launch {
                        preferencesManager.saveAgentPersonalitySettings(
                            customEmoji = customEmojiInput.takeIf { it.isNotBlank() }
                        )
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PersonalityTab(
    selectedPersonalityId: String,
    customName: String,
    onCustomNameChange: (String) -> Unit,
    onPersonalitySelect: (String) -> Unit,
    onSaveCustomName: () -> Unit
) {
    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.agent_personality_select),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.agent_personality_select_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(280.dp)
                ) {
                    items(AgentPersonalities.ALL_PERSONALITIES) { personality ->
                        PersonalityCard(
                            personality = personality,
                            isSelected = personality.id == selectedPersonalityId,
                            onClick = { onPersonalitySelect(personality.id) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.agent_custom_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.agent_custom_name_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                OutlinedTextField(
                    value = customName,
                    onValueChange = onCustomNameChange,
                    label = { Text(stringResource(R.string.agent_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (customName.isNotBlank()) {
                            IconButton(onClick = onSaveCustomName) {
                                Icon(Icons.Default.Check, contentDescription = "Save")
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PersonalityCard(
    personality: AgentPersonality,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected)
            CardDefaults.outlinedCardBorder()
        else
            null
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = personality.avatarEmoji,
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = personality.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = personality.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun VoiceTab(
    voiceVariantId: String,
    voicePitch: Float,
    voiceRate: Float,
    useLocalTTS: Boolean,
    ttsVoiceId: String?,
    onVoiceVariantSelect: (String) -> Unit,
    onPitchChange: (Float) -> Unit,
    onRateChange: (Float) -> Unit,
    onUseLocalTTSChange: (Boolean) -> Unit,
    onTTSVoiceChange: (String?) -> Unit
) {
    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.agent_voice_variant),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.agent_voice_variant_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(LocalVoiceVariants.ALL_VARIANTS) { variant ->
                        VoiceVariantChip(
                            variant = variant,
                            isSelected = variant.id == voiceVariantId,
                            onClick = { onVoiceVariantSelect(variant.id) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.agent_voice_adjustments),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.agent_voice_pitch, (voicePitch * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = voicePitch,
                    onValueChange = onPitchChange,
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.agent_voice_rate, (voiceRate * 100).toInt()),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = voiceRate,
                    onValueChange = onRateChange,
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.agent_use_local_tts),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.agent_use_local_tts_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useLocalTTS,
                        onCheckedChange = onUseLocalTTSChange
                    )
                }

                if (useLocalTTS) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.agent_tts_voice),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = ttsVoiceId ?: stringResource(R.string.agent_tts_voice_default),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceVariantChip(
    variant: com.ai.assistance.operit.data.model.LocalVoiceVariant,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Column {
                Text(variant.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = variant.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun AppearanceTab(
    customAvatarUri: String?,
    customEmoji: String,
    onEmojiChange: (String) -> Unit,
    onSelectAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit,
    onSaveEmoji: () -> Unit
) {
    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.agent_custom_avatar),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.agent_custom_avatar_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                if (customAvatarUri != null) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                    ) {
                        AsyncImage(
                            model = customAvatarUri,
                            contentDescription = "Custom avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = onRemoveAvatar,
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = Color.White
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onSelectAvatar,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.agent_select_avatar))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.agent_custom_emoji),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.agent_custom_emoji_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                OutlinedTextField(
                    value = customEmoji,
                    onValueChange = { if (it.length <= 4) onEmojiChange(it) },
                    label = { Text(stringResource(R.string.agent_emoji_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (customEmoji.isNotBlank()) {
                            IconButton(onClick = onSaveEmoji) {
                                Icon(Icons.Default.Check, contentDescription = "Save")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.agent_emoji_presets),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listOf("🤖", "👻", "🐱", "🦄", "👽", "🐕", "🐉", "✨")) { emoji ->
                        FilterChip(
                            selected = customEmoji == emoji,
                            onClick = { onEmojiChange(emoji) },
                            label = { Text(emoji, style = MaterialTheme.typography.headlineSmall) }
                        )
                    }
                }
            }
        }
    }
}