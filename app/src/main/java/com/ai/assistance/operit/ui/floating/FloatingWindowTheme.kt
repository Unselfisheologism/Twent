package com.ai.assistance.operit.ui.floating

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ColorScheme
import com.ai.assistance.operit.data.model.AssistantThemes
import com.ai.assistance.operit.data.preferences.UserPreferencesManager

/**
 * 为悬浮窗提供的独立主题
 * 使用静态颜色，避免对Activity上下文的依赖
 * 支持从用户偏好读取自定义助手主题
 */
@Composable
fun FloatingWindowTheme(
    context: Context? = null,
    colorScheme: ColorScheme? = null,
    typography: Typography? = null,
    content: @Composable () -> Unit
) {
    // 如果提供了context，尝试从用户偏好读取主题设置
    val preferencesManager = remember(context) {
        context?.let { UserPreferencesManager.getInstance(it) }
    }

    val assistantThemeMode by preferencesManager?.assistantThemeMode?.collectAsState(
        initial = UserPreferencesManager.ASSISTANT_THEME_MODE_FOLLOW_SYSTEM
    ) ?: remember { mutableStateOf(UserPreferencesManager.ASSISTANT_THEME_MODE_FOLLOW_SYSTEM) }

    val assistantCustomThemeId by preferencesManager?.assistantCustomThemeId?.collectAsState(
        initial = UserPreferencesManager.ASSISTANT_THEME_DEFAULT
    ) ?: remember { mutableStateOf(UserPreferencesManager.ASSISTANT_THEME_DEFAULT) }

    val useAssistantCustomColors by preferencesManager?.useAssistantCustomColors?.collectAsState(
        initial = false
    ) ?: remember { mutableStateOf(false) }

    val assistantCustomPrimaryColor by preferencesManager?.assistantCustomPrimaryColor?.collectAsState(
        initial = null
    ) ?: remember { mutableStateOf<Int?>(null) }

    val assistantCustomSecondaryColor by preferencesManager?.assistantCustomSecondaryColor?.collectAsState(
        initial = null
    ) ?: remember { mutableStateOf<Int?>(null) }

    // 确定是否使用暗色主题
    val systemDarkTheme = isSystemInDarkTheme()
    val isDarkMode = when (assistantThemeMode) {
        UserPreferencesManager.ASSISTANT_THEME_MODE_LIGHT -> false
        UserPreferencesManager.ASSISTANT_THEME_MODE_DARK -> true
        else -> systemDarkTheme // follow system
    }

    // 获取选中的主题
    val selectedTheme = AssistantThemes.getThemeById(assistantCustomThemeId)

    // 计算最终的ColorScheme
    val baseColorScheme = colorScheme ?: run {
        // 如果有选中主题且不是默认主题，使用主题颜色
        if (selectedTheme != null && selectedTheme.id != UserPreferencesManager.ASSISTANT_THEME_DEFAULT) {
            if (isDarkMode) {
                darkColorScheme(
                    primary = Color(selectedTheme.primaryColor),
                    onPrimary = Color(selectedTheme.onPrimaryColor),
                    secondary = Color(selectedTheme.secondaryColor),
                    background = Color(selectedTheme.backgroundColor),
                    surface = Color(selectedTheme.surfaceColor),
                    onBackground = Color(selectedTheme.onBackgroundColor),
                    onSurface = Color(selectedTheme.onBackgroundColor)
                )
            } else {
                lightColorScheme(
                    primary = Color(selectedTheme.primaryColor),
                    onPrimary = Color(selectedTheme.onPrimaryColor),
                    secondary = Color(selectedTheme.secondaryColor),
                    background = Color(selectedTheme.backgroundColor),
                    surface = Color(selectedTheme.surfaceColor),
                    onBackground = Color(selectedTheme.onBackgroundColor),
                    onSurface = Color(selectedTheme.onBackgroundColor)
                )
            }
        } else if (useAssistantCustomColors && assistantCustomPrimaryColor != null) {
            // 使用自定义颜色
            val primary = Color(assistantCustomPrimaryColor)
            val secondary = assistantCustomSecondaryColor?.let { Color(it) } ?: Color(0xFF625b71)

            if (isDarkMode) {
                darkColorScheme(
                    primary = primary,
                    onPrimary = Color.White,
                    secondary = secondary,
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onBackground = Color(0xFFE0E0E0),
                    onSurface = Color(0xFFE0E0E0)
                )
            } else {
                lightColorScheme(
                    primary = primary,
                    onPrimary = Color.White,
                    secondary = secondary,
                    background = Color(0xFFFFFBFE),
                    surface = Color(0xFFFFFBFE),
                    onBackground = Color(0xFF1C1B1F),
                    onSurface = Color(0xFF1C1B1F)
                )
            }
        } else {
            // 使用默认颜色
            if (isDarkMode) {
                darkColorScheme(
                    primary = Color(0xFF6650a4),
                    onPrimary = Color.White,
                    secondary = Color(0xFF625b71),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onBackground = Color(0xFFE0E0E0),
                    onSurface = Color(0xFFE0E0E0)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF6650a4),
                    onPrimary = Color.White,
                    secondary = Color(0xFF625b71),
                    background = Color(0xFFFFFBFE),
                    surface = Color(0xFFFFFBFE),
                    onBackground = Color(0xFF1C1B1F),
                    onSurface = Color(0xFF1C1B1F)
                )
            }
        }
    }

    val finalColorScheme = colorScheme ?: baseColorScheme
    
    // 创建调整大小后的默认Typography，如果没有传入typography参数则使用此默认值
    val defaultSmallTypography = Typography(
        // 正文大字号
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.5.sp
        ),
        // 正文中字号 
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.25.sp
        ),
        // 正文小字号 
        bodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.4.sp
        ),
        // 标签小字号
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.5.sp
        ),
        // 标题小字号
        titleSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.5.sp
        ),
        // 按钮文本样式
        labelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        // 按钮大文本样式
        labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.5.sp
        )
    )

    // 优先使用传入的typography，如果没有则使用默认的小型typography
    val finalTypography = typography ?: defaultSmallTypography
    
    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = finalTypography,
        content = content
    )
} 