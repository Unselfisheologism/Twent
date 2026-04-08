package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a mini-app that can be created by the AI agent or user.
 * Mini-apps are HTML/CSS/JS applications stored in the app's files directory
 * and served via the local web server inside a WebView.
 */
@Serializable
data class MiniApp(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val icon: String? = null, // Emoji or base64 icon string
    val type: MiniAppType = MiniAppType.PERSISTENT,
    val entryFile: String = "index.html",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val requiredPermissions: Set<String> = emptySet(), // Android permissions (e.g. "CAMERA")
    val webPermissions: Set<String> = emptySet(), // WebView/web permissions (e.g. "microphone", "geolocation")
    val metadata: Map<String, String> = emptyMap() // AI prompt, tags, generation info, etc.
)

/**
 * The type of a mini-app, determining its lifecycle.
 */
@Serializable
enum class MiniAppType {
    /** Temporary; may be cleared on app restart or storage cleanup */
    EPHEMERAL,
    /** Permanently stored until explicitly deleted by the user */
    PERSISTENT
}

/**
 * Represents a permission that a mini-app may require.
 * Used for documentation, UI hints, and permission checking.
 */
@Serializable
enum class MiniAppPermission(
    val androidPermission: String?,
    val webPermission: String?,
    val displayName: String,
    val description: String
) {
    CAMERA(
        androidPermission = "CAMERA",
        webPermission = "camera",
        displayName = "Camera",
        description = "Access the device camera for photos and video"
    ),
    MICROPHONE(
        androidPermission = "RECORD_AUDIO",
        webPermission = "microphone",
        displayName = "Microphone",
        description = "Record audio from the device microphone"
    ),
    LOCATION(
        androidPermission = "ACCESS_FINE_LOCATION",
        webPermission = "geolocation",
        displayName = "Location",
        description = "Access the device's GPS location"
    ),
    NOTIFICATIONS(
        androidPermission = "POST_NOTIFICATIONS",
        webPermission = "notifications",
        displayName = "Notifications",
        description = "Show system notifications"
    ),
    CONTACTS(
        androidPermission = "READ_CONTACTS",
        webPermission = null,
        displayName = "Contacts",
        description = "Read device contacts"
    ),
    PHONE(
        androidPermission = "READ_PHONE_STATE",
        webPermission = null,
        displayName = "Phone",
        description = "Access phone state and call logs"
    ),
    STORAGE(
        androidPermission = "READ_EXTERNAL_STORAGE",
        webPermission = null,
        displayName = "Storage",
        description = "Read from device storage"
    )
}
