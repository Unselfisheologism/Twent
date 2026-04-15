package com.ai.assistance.operit.ui.features.assistant.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.voice.api.TTSVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Simplified Assistant Config ViewModel
 * Only handles: System prompt, Voice settings, Profile picture
 */
class SimplifiedAssistantConfigViewModel(
    private val context: Context
) : ViewModel() {

    // UI state
    data class UiState(
        val profilePictureUri: Uri? = null,
        val systemPrompt: String = "",
        val selectedVoice: TTSVoice = TTSVoice.CHIRP_ALGIEBA, // Default male voice
        val speechRate: Float = 1.0f,
        val speechPitch: Float = 1.0f,
        val isSaving: Boolean = false,
        val saveSuccess: Boolean = false,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val apiPreferences = ApiPreferences(context)
    private val userPreferencesManager = UserPreferencesManager(context)

    init {
        loadCurrentSettings()
    }

    private fun loadCurrentSettings() {
        viewModelScope.launch {
            try {
                // Load system prompt
                val systemPrompt = apiPreferences.getCustomSystemPromptTemplate() ?: ""
                
                // Load voice settings
                val voiceId = userPreferencesManager.getAgentTTSVoiceId()
                val selectedVoice = try {
                    TTSVoice.entries.find { it.voiceName == voiceId } ?: TTSVoice.CHIRP_ALGIEBA
                } catch (e: Exception) {
                    TTSVoice.CHIRP_ALGIEBA
                }
                
                // Load speech settings
                val speechRate = userPreferencesManager.getAgentTTSSpeechRate()
                val speechPitch = userPreferencesManager.getAgentTTSSpeechPitch()
                
                // Load profile picture (if stored)
                val profilePictureUri = userPreferencesManager.getAgentProfilePictureUri()?.let {
                    Uri.parse(it)
                }

                _uiState.value = _uiState.value.copy(
                    profilePictureUri = profilePictureUri,
                    systemPrompt = systemPrompt,
                    selectedVoice = selectedVoice,
                    speechRate = speechRate,
                    speechPitch = speechPitch
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to load settings: ${e.message}"
                )
            }
        }
    }

    fun updateProfilePicture(uri: Uri) {
        _uiState.value = _uiState.value.copy(profilePictureUri = uri)
    }

    fun removeProfilePicture() {
        _uiState.value = _uiState.value.copy(profilePictureUri = null)
    }

    fun updateSystemPrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(systemPrompt = prompt)
    }

    fun updateSelectedVoice(voice: TTSVoice) {
        _uiState.value = _uiState.value.copy(selectedVoice = voice)
    }

    fun updateSpeechRate(rate: Float) {
        _uiState.value = _uiState.value.copy(speechRate = rate)
    }

    fun updateSpeechPitch(pitch: Float) {
        _uiState.value = _uiState.value.copy(speechPitch = pitch)
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            
            try {
                // Save system prompt
                apiPreferences.setCustomSystemPromptTemplate(_uiState.value.systemPrompt)
                
                // Save voice settings
                userPreferencesManager.saveAgentTTSVoiceId(_uiState.value.selectedVoice.voiceName)
                userPreferencesManager.saveAgentTTSSpeechRate(_uiState.value.speechRate)
                userPreferencesManager.saveAgentTTSSpeechPitch(_uiState.value.speechPitch)
                
                // Save profile picture URI
                val profilePictureUriString = _uiState.value.profilePictureUri?.toString()
                userPreferencesManager.saveAgentProfilePictureUri(profilePictureUriString)
                
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveSuccess = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = "Failed to save settings: ${e.message}"
                )
            }
        }
    }

    /**
     * Factory for creating SimplifiedAssistantConfigViewModel
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SimplifiedAssistantConfigViewModel::class.java)) {
                return SimplifiedAssistantConfigViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}