package com.ai.assistance.operit.core.tools.system

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import java.io.File

/**
 * 用于管理内置Shizuku应用的安装 (STUB - Shizuku support removed)
 * All methods return false/null/empty.
 */
class ShizukuInstaller {
    companion object {
        private const val TAG = "ShizukuInstaller"

        fun extractApkFromAssets(context: Context): File? {
            AppLogger.d(TAG, "Shizuku APK extraction stub - returning null")
            return null
        }

        fun isApkExtracted(context: Context): Boolean {
            return false
        }

        fun installBundledShizuku(context: Context): Boolean {
            AppLogger.d(TAG, "Shizuku install stub - returning false")
            return false
        }

        fun getBundledShizukuVersion(context: Context): String {
            return "N/A (Shizuku removed)"
        }

        fun getInstalledShizukuVersion(context: Context): String? {
            return null
        }

        fun isShizukuUpdateNeeded(context: Context): Boolean {
            return false
        }

        fun clearCache() {
            // no-op
        }
    }
}
