package com.ai.assistance.operit.core.tools.system

/**
 * Defines the permission level for the app.
 * - ACCESSIBILITY: Accessibility service permissions (default and only level)
 *
 * All root, adb, shizuku, standard, debugger, and admin permission levels have been removed.
 * Accessibility is now the default and only permission level.
 */
enum class AndroidPermissionLevel {
    ACCESSIBILITY; // Accessibility service permissions (default)

    companion object {
        /**
         * From string to permission level
         * @param value Permission level string
         * @return Corresponding permission level, defaults to ACCESSIBILITY if unrecognized
         */
        fun fromString(value: String?): AndroidPermissionLevel {
            return ACCESSIBILITY
        }
    }
}