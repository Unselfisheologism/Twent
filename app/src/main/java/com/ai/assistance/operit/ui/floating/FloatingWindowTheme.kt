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
    primary = Color(0xFF6650a4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005E),
    secondary = Color(0xFF625b71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF370B1E),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E)
)

private fun getDefaultDarkColorScheme() = darkColorScheme(
    primary = Color(0xFF6650a4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99)
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