package com.ai.assistance.operit.ui.floating

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    // 如果没有preferencesManager，使用默认主题
    if (preferencesManager == null) {
        MaterialTheme(
            colorScheme = colorScheme ?: getDefaultLightColorScheme(),
            typography = typography ?: getDefaultTypography(),
            content = content
        )
        return
    }

    val assistantThemeMode by preferencesManager.assistantThemeMode.collectAsState(
        initial = UserPreferencesManager.ASSISTANT_THEME_MODE_FOLLOW_SYSTEM
    )
    val assistantCustomThemeId by preferencesManager.assistantCustomThemeId.collectAsState(
        initial = UserPreferencesManager.ASSISTANT_THEME_DEFAULT
    )
    val useAssistantCustomColors by preferencesManager.useAssistantCustomColors.collectAsState(
        initial = false
    )
    val assistantCustomPrimaryColor by preferencesManager.assistantCustomPrimaryColor.collectAsState(
        initial = null
    )
    val assistantCustomSecondaryColor by preferencesManager.assistantCustomSecondaryColor.collectAsState(
        initial = null
    )

    // 确定是否使用暗色主题
    val systemDarkTheme = isSystemInDarkTheme()
    val isExtraDark = assistantThemeMode == UserPreferencesManager.ASSISTANT_THEME_MODE_AMOLED
    val isDarkMode = when (assistantThemeMode) {
        UserPreferencesManager.ASSISTANT_THEME_MODE_LIGHT -> false
        UserPreferencesManager.ASSISTANT_THEME_MODE_DARK,
        UserPreferencesManager.ASSISTANT_THEME_MODE_AMOLED -> true
        else -> systemDarkTheme // follow system
    }

    // 获取选中的主题
    val selectedTheme = AssistantThemes.getThemeById(assistantCustomThemeId)

    // 计算最终的ColorScheme
    val finalColorScheme = colorScheme ?: run {
        // 如果有选中主题且不是默认主题，使用主题颜色
        if (selectedTheme.id != UserPreferencesManager.ASSISTANT_THEME_DEFAULT) {
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
            val primary = Color(assistantCustomPrimaryColor!!)
            val secondaryColor = assistantCustomSecondaryColor
            val secondary = if (secondaryColor != null) Color(secondaryColor) else Color(0xFF625b71)

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
                getDefaultDarkColorScheme()
            } else {
                getDefaultLightColorScheme()
            }
        }
    }

    // 创建调整大小后的默认Typography
    val defaultTypography = typography ?: getDefaultTypography()

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = defaultTypography,
        content = content
    )
}

private fun getDefaultLightColorScheme() = lightColorScheme(
    primary = Color(0xFFFF6B35), // Orange Primary
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFC4AD),
    onPrimaryContainer = Color(0xFF301000),
    secondary = Color(0xFF7FE8E8), // Cyan Primary
    onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFFA5F0F0),
    onSecondaryContainer = Color(0xFF001F1F),
    tertiary = Color(0xFF4A7C9B), // Steel Primary
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF6BA3C4),
    onTertiaryContainer = Color(0xFF001E2E),
    error = Color(0xFFFF4444),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFBFC),
    onBackground = Color(0xFF0D1117),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D1117),
    surfaceVariant = Color(0xFFE1E4E8),
    onSurfaceVariant = Color(0xFF484F58),
    outline = Color(0xFF6E7681)
)

private fun getDefaultDarkColorScheme() = darkColorScheme(
    primary = Color(0xFFFF6B35), // Orange Primary
    onPrimary = Color(0xFF301000),
    primaryContainer = Color(0xFFD34B17),
    onPrimaryContainer = Color(0xFFFFC4AD),
    secondary = Color(0xFF7FE8E8), // Cyan Primary
    onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFF2DB5B5),
    onSecondaryContainer = Color(0xFFA5F0F0),
    tertiary = Color(0xFF4A7C9B), // Steel Primary
    onTertiary = Color(0xFF001E2E),
    tertiaryContainer = Color(0xFF2D5A7B),
    onTertiaryContainer = Color(0xFF6BA3C4),
    error = Color(0xFFFF4444),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1F2E),
    onBackground = Color(0xFFC9D1D9),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFC9D1D9),
    surfaceVariant = Color(0xFF30363D),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF6E7681)
)

private fun getDefaultTypography() = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp
    )
)
