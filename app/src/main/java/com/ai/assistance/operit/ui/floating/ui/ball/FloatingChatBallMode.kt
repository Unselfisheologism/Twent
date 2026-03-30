package com.ai.assistance.operit.ui.floating.ui.ball

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.agent.UIAgentModeManager
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 
 * 渲染悬浮窗的聊天球模式界面 - Siri风格动感球体
 * 逻辑已提取到 SiriBall.kt
 * 点击切换到窗口模式
 */
@Composable
fun FloatingChatBallMode(floatContext: FloatContext) {
    // UI Agent Mode state
    val uiAgentEnabled by UIAgentModeManager.isEnabled.collectAsState()
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // UI Agent Mode toggle chip (above the ball)
        FilterChip(
            selected = uiAgentEnabled,
            onClick = { UIAgentModeManager.toggle() },
            label = { 
                Text(
                    text = "UI Agent",
                    style = MaterialTheme.typography.labelSmall
                ) 
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = "UI Agent Mode",
                    modifier = Modifier.padding(start = 2.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        SiriBall(
            floatContext = floatContext,
            onClick = {
                floatContext.onModeChange(FloatingMode.WINDOW)
            },
            onTriggerResult = {
                // 切换到结果展示模式显示结果
                floatContext.onModeChange(FloatingMode.RESULT_DISPLAY)
                
                // 3秒后自动切回球模式
                floatContext.coroutineScope.launch {
                    delay(3000)
                    // 只有当前还是结果展示模式时才切回（避免用户已经切换到其他模式）
                    if (floatContext.currentMode == FloatingMode.RESULT_DISPLAY) {
                        floatContext.onModeChange(FloatingMode.BALL)
                    }
                }
            }
        )
    }
}
