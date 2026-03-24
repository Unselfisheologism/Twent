package com.ai.assistance.operit.data.model

import androidx.compose.ui.graphics.Color

/**
 * Assistant Theme data model - represents a custom theme for the AI assistant/overlay
 */
data class AssistantTheme(
    val id: String,
    val name: String,
    val description: String,
    val primaryColor: Long,
    val secondaryColor: Long,
    val backgroundColor: Long,
    val surfaceColor: Long,
    val onPrimaryColor: Long,
    val onBackgroundColor: Long,
    val isDarkMode: Boolean,
    val iconStyle: String = "default",
    val wallpaperUrl: String? = null
)

/**
 * Predefined assistant themes
 */
object AssistantThemes {
    val DEFAULT = AssistantTheme(
        id = "default",
        name = "Default",
        description = "Default assistant theme",
        primaryColor = 0xFF6650a4,
        secondaryColor = 0xFF625b71,
        backgroundColor = 0xFFFAFAFA,
        surfaceColor = 0xFFFFFFFF,
        onPrimaryColor = 0xFFFFFFFF,
        onBackgroundColor = 0xFF1C1B1F,
        isDarkMode = false
    )

    val PASTEL_PINK = AssistantTheme(
        id = "pastel_pink",
        name = "Pastel Pink",
        description = "Soft pink theme for a gentle AI personality",
        primaryColor = 0xFFFFB6C1,
        secondaryColor = 0xFFFFD1DC,
        backgroundColor = 0xFFFFF0F5,
        surfaceColor = 0xFFFFFAFE,
        onPrimaryColor = 0xFF5D3F3F,
        onBackgroundColor = 0xFF4A3F3F,
        isDarkMode = false,
        iconStyle = "cat"
    )

    val OCEAN_BLUE = AssistantTheme(
        id = "ocean_blue",
        name = "Ocean Blue",
        description = "Cool blue theme inspired by the ocean",
        primaryColor = 0xFF4A90D9,
        secondaryColor = 0xFF74B9FF,
        backgroundColor = 0xFFF0F8FF,
        surfaceColor = 0xFFE6F3FF,
        onPrimaryColor = 0xFFFFFFFF,
        onBackgroundColor = 0xFF1A3A5C,
        isDarkMode = false,
        iconStyle = "dragon"
    )

    val FOREST_GREEN = AssistantTheme(
        id = "forest_green",
        name = "Forest Green",
        description = "Nature-inspired green theme",
        primaryColor = 0xFF4CAF50,
        secondaryColor = 0xFF81C784,
        backgroundColor = 0xFFF1F8E9,
        surfaceColor = 0xFFFFFFFF,
        onPrimaryColor = 0xFFFFFFFF,
        onBackgroundColor = 0xFF1B5E20,
        isDarkMode = false,
        iconStyle = "unicorn"
    )

    val SUNSET_ORANGE = AssistantTheme(
        id = "sunset_orange",
        name = "Sunset Orange",
        description = "Warm orange theme like a beautiful sunset",
        primaryColor = 0xFFFF7043,
        secondaryColor = 0xFFFFAB91,
        backgroundColor = 0xFFFBE9E7,
        surfaceColor = 0xFFFFF3E0,
        onPrimaryColor = 0xFFFFFFFF,
        onBackgroundColor = 0xFF4E342E,
        isDarkMode = false,
        iconStyle = "robot"
    )

    val SPACE_PURPLE = AssistantTheme(
        id = "space_purple",
        name = "Space Purple",
        description = "Cosmic purple theme for an intelligent AI",
        primaryColor = 0xFF7E57C2,
        secondaryColor = 0xFFB39DDB,
        backgroundColor = 0xFFEDE7F6,
        surfaceColor = 0xFFFCFAFF,
        onPrimaryColor = 0xFFFFFFFF,
        onBackgroundColor = 0xFF311B92,
        isDarkMode = false,
        iconStyle = "alien"
    )

    val COTTON_CANDY = AssistantTheme(
        id = "cotton_candy",
        name = "Cotton Candy",
        description = "Fun and playful pink-blue gradient theme",
        primaryColor = 0xFFBA68C8,
        secondaryColor = 0xFF64B5F6,
        backgroundColor = 0xFFF3E5F5,
        surfaceColor = 0xFFFFFFFF,
        onPrimaryColor = 0xFFFFFFFF,
        onBackgroundColor = 0xFF4A148C,
        isDarkMode = false,
        iconStyle = "ghost"
    )

    val MIDNIGHT = AssistantTheme(
        id = "midnight",
        name = "Midnight",
        description = "Dark theme for night owls",
        primaryColor = 0xFF5C6BC0,
        secondaryColor = 0xFF7986CB,
        backgroundColor = 0xFF121212,
        surfaceColor = 0xFF1E1E1E,
        onPrimaryColor = 0xFFFFFFFF,
        onBackgroundColor = 0xFFE0E0E0,
        isDarkMode = true,
        iconStyle = "robot"
    )

    val ROSE_GOLD = AssistantTheme(
        id = "rose_gold",
        name = "Rose Gold",
        description = "Elegant rose gold theme",
        primaryColor = 0xFFB76E79,
        secondaryColor = 0xFFD4A5A5,
        backgroundColor = 0xFFFAF0F0,
        surfaceColor = 0xFFFFFafa,
        onPrimaryColor = 0xFFFFFFFF,
        onBackgroundColor = 0xFF5D3F3F,
        isDarkMode = false,
        iconStyle = "cat"
    )

    val NEON_NIGHTS = AssistantTheme(
        id = "neon_nights",
        name = "Neon Nights",
        description = "Vibrant neon theme for a futuristic AI",
        primaryColor = 0xFFE040FB,
        secondaryColor = 0xFF00E5FF,
        backgroundColor = 0xFF0D0D1A,
        surfaceColor = 0xFF1A1A2E,
        onPrimaryColor = 0xFF000000,
        onBackgroundColor = 0xFFE0E0E0,
        isDarkMode = true,
        iconStyle = "robot"
    )

    val MINIMAL_WHITE = AssistantTheme(
        id = "minimal_white",
        name = "Minimal White",
        description = "Clean and simple white theme",
        primaryColor = 0xFF424242,
        secondaryColor = 0xFF757575,
        backgroundColor = 0xFFFFFFFF,
        surfaceColor = 0xFFFAFAFA,
        onPrimaryColor = 0xFFFFFFFF,
        onBackgroundColor = 0xFF212121,
        isDarkMode = false,
        iconStyle = "default"
    )

    val ALL_THEMES = listOf(
        DEFAULT,
        PASTEL_PINK,
        OCEAN_BLUE,
        FOREST_GREEN,
        SUNSET_ORANGE,
        SPACE_PURPLE,
        COTTON_CANDY,
        MIDNIGHT,
        ROSE_GOLD,
        NEON_NIGHTS,
        MINIMAL_WHITE
    )

    fun getThemeById(id: String): AssistantTheme {
        return ALL_THEMES.find { it.id == id } ?: DEFAULT
    }
}

/**
 * Assistant Icon Style data model
 */
data class AssistantIconStyle(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String
)

/**
 * Predefined assistant icon styles
 */
object AssistantIconStyles {
    val DEFAULT = AssistantIconStyle(
        id = "default",
        name = "Default",
        description = "Default assistant icon",
        emoji = "🤖"
    )

    val ROBOT = AssistantIconStyle(
        id = "robot",
        name = "Robot",
        description = "Classic robot assistant",
        emoji = "🤖"
    )

    val GHOST = AssistantIconStyle(
        id = "ghost",
        name = "Ghost",
        description = "Friendly ghost",
        emoji = "👻"
    )

    val CAT = AssistantIconStyle(
        id = "cat",
        name = "Cat",
        description = "Cute cat companion",
        emoji = "🐱"
    )

    val DOG = AssistantIconStyle(
        id = "dog",
        name = "Dog",
        description = "Loyal dog friend",
        emoji = "🐕"
    )

    val UNICORN = AssistantIconStyle(
        id = "unicorn",
        name = "Unicorn",
        description = "Magical unicorn",
        emoji = "🦄"
    )

    val DRAGON = AssistantIconStyle(
        id = "dragon",
        name = "Dragon",
        description = "Wise dragon",
        emoji = "🐉"
    )

    val ALIEN = AssistantIconStyle(
        id = "alien",
        name = "Alien",
        description = "Extraterrestrial AI",
        emoji = "👽"
    )

    val ALL_STYLES = listOf(
        DEFAULT,
        ROBOT,
        GHOST,
        CAT,
        DOG,
        UNICORN,
        DRAGON,
        ALIEN
    )

    fun getStyleById(id: String): AssistantIconStyle {
        return ALL_STYLES.find { it.id == id } ?: DEFAULT
    }
}