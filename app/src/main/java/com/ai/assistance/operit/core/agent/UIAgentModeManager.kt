package com.ai.assistance.operit.core.agent

import android.content.Context
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UIAgentModeManager {
    private const val TAG = "UIAgentModeManager"
    
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private var _onCompleteCallback: ((Boolean) -> Unit)? = null
    
    val uiAutomationKeywords = listOf(
        "open", "tap", "click", "scroll", "swipe", "type", "input",
        "navigate", "press", "back", "home", "switch", "delete",
        "settings", "app", "screen", "menu", "button", "text field",
        "打开", "点击", "滑动", "输入", "导航", "按", "设置", "应用", "屏幕", "菜单", "按钮"
    )
    
    fun setEnabled(enabled: Boolean) {
        Log.d(TAG, "UI Agent Mode ${if (enabled) "enabled" else "disabled"}")
        _isEnabled.value = enabled
    }
    
    fun toggle() {
        setEnabled(!_isEnabled.value)
    }
    
    fun isUIAutomationTask(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return uiAutomationKeywords.any { keyword ->
            lowerMessage.contains(keyword)
        } || message.contains("automation") || message.contains("automate")
    }
    
    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }
    
    fun onComplete(callback: ((Boolean) -> Unit)?) {
        _onCompleteCallback = callback
    }
    
    fun notifyComplete(success: Boolean) {
        _onCompleteCallback?.invoke(success)
    }
}