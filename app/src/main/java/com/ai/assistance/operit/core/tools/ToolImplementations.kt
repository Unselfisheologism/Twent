package com.ai.assistance.operit.core.tools

import android.content.Context
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult

interface ToolImplementations {
    suspend fun tap(tool: AITool): ToolResult
    suspend fun longPress(tool: AITool): ToolResult
    suspend fun clickElement(tool: AITool): ToolResult
    suspend fun doubleTap(tool: AITool): ToolResult
    suspend fun setInputText(tool: AITool): ToolResult
    suspend fun pressKey(tool: AITool): ToolResult
    suspend fun swipe(tool: AITool): ToolResult
    suspend fun swipeLeft(tool: AITool): ToolResult
    suspend fun swipeRight(tool: AITool): ToolResult
    suspend fun swipeUp(tool: AITool): ToolResult
    suspend fun swipeDown(tool: AITool): ToolResult
    suspend fun scrollLeft(tool: AITool): ToolResult
    suspend fun scrollRight(tool: AITool): ToolResult
    suspend fun scrollUp(tool: AITool): ToolResult
    suspend fun scrollDown(tool: AITool): ToolResult
    suspend fun hold(tool: AITool): ToolResult
    suspend fun openApp(tool: AITool): ToolResult
    suspend fun back(tool: AITool): ToolResult
    suspend fun home(tool: AITool): ToolResult
    suspend fun getCurrentActivity(tool: AITool): ToolResult
    suspend fun captureScreenshot(tool: AITool): Pair<String?, Pair<Int, Int>?>
}