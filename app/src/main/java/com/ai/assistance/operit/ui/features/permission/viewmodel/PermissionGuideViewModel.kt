package com.ai.assistance.operit.ui.features.permission.viewmodel

import com.ai.assistance.operit.util.AppLogger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PermissionGuideViewModel : ViewModel() {

    private val TAG = "PermissionGuideVM"

    // UI状态
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 步骤枚举
    enum class Step {
        WELCOME
    }

    // 初始化
    init {
        AppLogger.d(TAG, "ViewModel initialized")
    }

    // 更新当前步骤
    fun setCurrentStep(step: Step) {
        _uiState.update { it.copy(currentStep = step) }
        AppLogger.d(TAG, "Current step set to: $step")
    }

    // 保存权限级别 (always ACCESSIBILITY)
    fun savePermissionLevel() {
        AppLogger.d(TAG, "Saving permission level: ACCESSIBILITY")

        viewModelScope.launch {
            try {
                // Always save ACCESSIBILITY as the permission level
                androidPermissionPreferences.savePreferredPermissionLevel(AndroidPermissionLevel.ACCESSIBILITY)

                // 更新完成状态
                _uiState.update { it.copy(isCompleted = true) }

                AppLogger.d(TAG, "Permission level saved, guide completed")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error saving permission level", e)
            }
        }
    }

    // UI状态数据类
    data class UiState(
        val currentStep: Step = Step.WELCOME,
        val isCompleted: Boolean = false
    )
}