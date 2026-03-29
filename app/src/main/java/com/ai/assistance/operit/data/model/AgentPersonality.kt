package com.ai.assistance.operit.data.model

import androidx.compose.ui.graphics.Color

data class AgentPersonality(
    val id: String,
    val name: String,
    val description: String,
    val avatarEmoji: String = "🤖",
    val customAvatarUri: String? = null,
    val voiceName: String = "default",
    val voicePitch: Float = 1.0f,
    val voiceRate: Float = 1.0f,
    val useLocalTTS: Boolean = true,
    val ttsVoiceId: String? = null,
    val themeId: String = "default",
    val iconStyle: String = "default"
)

object AgentPersonalities {
    val DEFAULT = AgentPersonality(
        id = "default",
        name = "Default Assistant",
        description = "Standard AI assistant",
        avatarEmoji = "🤖",
        voiceName = "default",
        useLocalTTS = true
    )

    val GHOST = AgentPersonality(
        id = "ghost",
        name = "Friendly Ghost",
        description = "A playful and friendly ghost assistant",
        avatarEmoji = "👻",
        voiceName = "soft",
        voicePitch = 1.2f,
        voiceRate = 0.9f,
        useLocalTTS = true,
        themeId = "cotton_candy",
        iconStyle = "ghost"
    )

    val CAT = AgentPersonality(
        id = "cat",
        name = "Cat Assistant",
        description = "A cute cat companion for your tasks",
        avatarEmoji = "🐱",
        voiceName = "bright",
        voicePitch = 1.3f,
        voiceRate = 1.0f,
        useLocalTTS = true,
        themeId = "pastel_pink",
        iconStyle = "cat"
    )

    val ROBOT = AgentPersonality(
        id = "robot",
        name = "Robot Assistant",
        description = "A reliable robotic assistant",
        avatarEmoji = "🤖",
        voiceName = "mechanical",
        voicePitch = 0.8f,
        voiceRate = 1.1f,
        useLocalTTS = true,
        themeId = "midnight",
        iconStyle = "robot"
    )

    val UNICORN = AgentPersonality(
        id = "unicorn",
        name = "Magical Unicorn",
        description = "A magical unicorn companion",
        avatarEmoji = "🦄",
        voiceName = "magical",
        voicePitch = 1.4f,
        voiceRate = 0.85f,
        useLocalTTS = true,
        themeId = "forest_green",
        iconStyle = "unicorn"
    )

    val DRAGON = AgentPersonality(
        id = "dragon",
        name = "Wise Dragon",
        description = "A wise and powerful dragon assistant",
        avatarEmoji = "🐉",
        voiceName = "deep",
        voicePitch = 0.7f,
        voiceRate = 1.05f,
        useLocalTTS = true,
        themeId = "ocean_blue",
        iconStyle = "dragon"
    )

    val ALIEN = AgentPersonality(
        id = "alien",
        name = "Space Alien",
        description = "An extraterrestrial AI from beyond",
        avatarEmoji = "👽",
        voiceName = "alien",
        voicePitch = 1.1f,
        voiceRate = 0.95f,
        useLocalTTS = true,
        themeId = "space_purple",
        iconStyle = "alien"
    )

    val DOG = AgentPersonality(
        id = "dog",
        name = "Loyal Dog",
        description = "A loyal and friendly dog companion",
        avatarEmoji = "🐕",
        voiceName = "warm",
        voicePitch = 1.0f,
        voiceRate = 1.0f,
        useLocalTTS = true,
        themeId = "sunset_orange",
        iconStyle = "dog"
    )

    val ALL_PERSONALITIES = listOf(
        DEFAULT, GHOST, CAT, ROBOT, UNICORN, DRAGON, ALIEN, DOG
    )

    fun getPersonalityById(id: String): AgentPersonality {
        return ALL_PERSONALITIES.find { it.id == id } ?: DEFAULT
    }
}

data class LocalVoiceVariant(
    val id: String,
    val name: String,
    val description: String,
    val pitch: Float,
    val rate: Float,
    val locale: String = "en-US"
)

object LocalVoiceVariants {
    val DEFAULT = LocalVoiceVariant(
        id = "default",
        name = "Default",
        description = "System default voice",
        pitch = 1.0f,
        rate = 1.0f
    )

    val SOFT = LocalVoiceVariant(
        id = "soft",
        name = "Soft",
        description = "Gentle and soft spoken",
        pitch = 1.2f,
        rate = 0.9f
    )

    val BRIGHT = LocalVoiceVariant(
        id = "bright",
        name = "Bright",
        description = "Light and cheerful voice",
        pitch = 1.3f,
        rate = 1.0f
    )

    val MECHANICAL = LocalVoiceVariant(
        id = "mechanical",
        name = "Mechanical",
        description = "Robot-like monotone",
        pitch = 0.8f,
        rate = 1.1f
    )

    val DEEP = LocalVoiceVariant(
        id = "deep",
        name = "Deep",
        description = "Deep and authoritative",
        pitch = 0.7f,
        rate = 1.05f
    )

    val ALIEN = LocalVoiceVariant(
        id = "alien",
        name = "Alien",
        description = "Otherworldly synthesized",
        pitch = 1.1f,
        rate = 0.95f
    )

    val WARM = LocalVoiceVariant(
        id = "warm",
        name = "Warm",
        description = "Friendly and warm tone",
        pitch = 1.0f,
        rate = 1.0f
    )

    val MAGICAL = LocalVoiceVariant(
        id = "magical",
        name = "Magical",
        description = "Enchanting and mystical",
        pitch = 1.4f,
        rate = 0.85f
    )

    val ALL_VARIANTS = listOf(
        DEFAULT, SOFT, BRIGHT, MECHANICAL, DEEP, ALIEN, WARM, MAGICAL
    )

    fun getVariantById(id: String): LocalVoiceVariant {
        return ALL_VARIANTS.find { it.id == id } ?: DEFAULT
    }
}