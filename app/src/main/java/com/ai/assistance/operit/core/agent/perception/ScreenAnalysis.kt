package com.ai.assistance.operit.core.agent.perception

import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo

data class ScreenAnalysis(
    val uiRepresentation: String,
    val isKeyboardOpen: Boolean,
    val activityName: String,
    val elementMap: Map<Int, AccessibilityNodeInfo>,
    val scrollUp: Int,
    val scrollDown: Int,
    val screenshot: Bitmap? = null
)