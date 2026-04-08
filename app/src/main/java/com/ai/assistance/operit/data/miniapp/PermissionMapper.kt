package com.ai.assistance.operit.data.miniapp

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Maps between Android system permissions and WebView/Web API permissions.
 * Also provides utilities for checking which permissions the Operit app currently holds.
 */
object PermissionMapper {

    /**
     * Mapping from web permission names to Android permission constants.
     * Only includes web permissions that have a direct Android equivalent.
     */
    val WEB_TO_ANDROID: Map<String, String> = mapOf(
        "geolocation" to android.Manifest.permission.ACCESS_FINE_LOCATION,
        "microphone" to android.Manifest.permission.RECORD_AUDIO,
        "camera" to android.Manifest.permission.CAMERA,
        "notifications" to android.Manifest.permission.POST_NOTIFICATIONS
    )

    /**
     * Reverse mapping: Android permission to web permission name.
     */
    val ANDROID_TO_WEB: Map<String, String> = WEB_TO_ANDROID.entries.associate { (k, v) -> v to k }

    /**
     * Human-readable display names for Android permissions.
     */
    private val PERMISSION_DISPLAY_NAMES: Map<String, String> = mapOf(
        android.Manifest.permission.CAMERA to "Camera",
        android.Manifest.permission.RECORD_AUDIO to "Microphone",
        android.Manifest.permission.ACCESS_FINE_LOCATION to "Location",
        android.Manifest.permission.ACCESS_COARSE_LOCATION to "Location",
        android.Manifest.permission.POST_NOTIFICATIONS to "Notifications",
        android.Manifest.permission.READ_CONTACTS to "Contacts",
        android.Manifest.permission.WRITE_CONTACTS to "Contacts",
        android.Manifest.permission.READ_PHONE_STATE to "Phone",
        android.Manifest.permission.READ_CALL_LOG to "Call Logs",
        android.Manifest.permission.READ_EXTERNAL_STORAGE to "Storage",
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE to "Storage",
        android.Manifest.permission.READ_MEDIA_IMAGES to "Photos",
        android.Manifest.permission.READ_MEDIA_VIDEO to "Videos"
    )

    /**
     * Convert a web permission name to the corresponding Android permission constant.
     * Returns null if there's no direct mapping.
     */
    fun webToAndroid(webPermission: String): String? = WEB_TO_ANDROID[webPermission]

    /**
     * Convert an Android permission constant to the corresponding web permission name.
     * Returns null if there's no direct mapping.
     */
    fun androidToWeb(androidPermission: String): String? = ANDROID_TO_WEB[androidPermission]

    /**
     * Get a human-readable display name for an Android permission.
     * Falls back to the permission constant itself if no name is defined.
     */
    fun getDisplayName(androidPermission: String): String {
        return PERMISSION_DISPLAY_NAMES[androidPermission] ?: androidPermission.substringAfterLast(".")
    }

    /**
     * Get human-readable display names for a set of Android permissions.
     */
    fun getDisplayNames(permissions: Set<String>): List<String> {
        return permissions.map { getDisplayName(it) }
    }

    /**
     * Check if the Operit app has been granted a specific Android permission.
     */
    fun isPermissionGranted(context: Context, androidPermission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            androidPermission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check which permissions from the given set are NOT granted.
     * Returns the list of missing permissions.
     */
    fun getMissingPermissions(context: Context, requestedPermissions: Set<String>): List<String> {
        return requestedPermissions.filter { !isPermissionGranted(context, it) }
    }

    /**
     * Given a set of web permissions, return the corresponding Android permissions
     * that need to be granted for them to work.
     */
    fun resolveAndroidPermissions(webPermissions: Set<String>): Set<String> {
        return webPermissions.mapNotNull { webToAndroid(it) }.toSet()
    }

    /**
     * Given a set of Android permissions, return the corresponding web permissions.
     */
    fun resolveWebPermissions(androidPermissions: Set<String>): Set<String> {
        return androidPermissions.mapNotNull { androidToWeb(it) }.toSet()
    }

    /**
     * Check if a web permission can be satisfied by the currently granted Android permissions.
     */
    fun isWebPermissionGranted(context: Context, webPermission: String): Boolean {
        val androidPerm = webToAndroid(webPermission) ?: return true // No Android permission needed
        return isPermissionGranted(context, androidPerm)
    }

    /**
     * Check all web permissions and return which ones are missing.
     */
    fun getMissingWebPermissions(context: Context, webPermissions: Set<String>): List<String> {
        return webPermissions.filter { !isWebPermissionGranted(context, it) }
    }
}
