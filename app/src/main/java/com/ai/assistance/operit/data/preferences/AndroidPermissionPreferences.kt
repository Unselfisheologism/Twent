package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.ai.assistance.operit.util.AppLogger
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.androidPermissionDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "android_permission_preferences")

/** 全局单例实例 */
lateinit var androidPermissionPreferences: AndroidPermissionPreferences
    private set

/** 初始化Android权限偏好管理器 */
fun initAndroidPermissionPreferences(context: Context) {
    androidPermissionPreferences = AndroidPermissionPreferences(context)
}

/** Android权限偏好管理器 负责管理应用全局的权限级别偏好设置 */
class AndroidPermissionPreferences(private val context: Context) {
    companion object {
        private const val TAG = "AndroidPermissionPrefs"
        
        // SharedPreferences keys as backup
        private const val PREFS_NAME = "android_permission_prefs"
        private const val KEY_PERMISSION_LEVEL = "permission_level"

        // 权限相关键 (DataStore)
        private val PREFERRED_PERMISSION_LEVEL = stringPreferencesKey("preferred_permission_level")
    }
    
    // SharedPreferences for synchronous access
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** 首选权限级别Flow 返回用户配置的首选Android权限级别，如果未设置则返回null */
    val preferredPermissionLevelFlow: Flow<AndroidPermissionLevel?> =
            context.androidPermissionDataStore.data.map { preferences ->
                val levelString = preferences[PREFERRED_PERMISSION_LEVEL]
                if (levelString != null) AndroidPermissionLevel.fromString(levelString) else null
            }

    /**
     * 获取当前首选的权限级别 - 先尝试从DataStore读取，如果失败则使用SharedPreferences
     * 这是一个阻塞调用，应在非UI线程使用或谨慎使用
     * @return 当前配置的首选权限级别，如果未设置则返回ACCESSIBILITY
     */
    fun getPreferredPermissionLevel(): AndroidPermissionLevel? {
        // First try DataStore
        try {
            val level = runBlocking {
                try {
                    preferredPermissionLevelFlow.first()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "DataStore read failed, falling back to SharedPreferences", e)
                    null
                }
            }
            if (level != null) {
                AppLogger.d(TAG, "getPreferredPermissionLevel: from DataStore = $level")
                // Sync to SharedPreferences for backup
                prefs.edit().putString(KEY_PERMISSION_LEVEL, level.name).apply()
                return level
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error reading from DataStore", e)
        }

        // Fallback to SharedPreferences
        val savedLevel = prefs.getString(KEY_PERMISSION_LEVEL, null)
        if (savedLevel != null) {
            val level = AndroidPermissionLevel.fromString(savedLevel)
            AppLogger.d(TAG, "getPreferredPermissionLevel: from SharedPreferences = $level")
            return level
        }

        AppLogger.d(TAG, "getPreferredPermissionLevel: no value found, returning null")
        return null
    }

    /**
     * 保存首选权限级别 - 同时写入DataStore和SharedPreferences
     * @param permissionLevel 要设置的权限级别
     */
    suspend fun savePreferredPermissionLevel(permissionLevel: AndroidPermissionLevel) {
        AppLogger.d(TAG, "Saving preferred permission level: $permissionLevel")
        
        // Save to SharedPreferences immediately (synchronous, for backup)
        prefs.edit().putString(KEY_PERMISSION_LEVEL, permissionLevel.name).apply()
        AppLogger.d(TAG, "Saved to SharedPreferences: ${permissionLevel.name}")
        
        // Also save to DataStore (async)
        try {
            context.androidPermissionDataStore.edit { preferences ->
                preferences[PREFERRED_PERMISSION_LEVEL] = permissionLevel.name
            }
            AppLogger.d(TAG, "Saved to DataStore: ${permissionLevel.name}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save to DataStore, SharedPreferences backup exists", e)
        }
    }

    /**
     * 同步保存首选权限级别 - 可在主线程调用
     * @param permissionLevel 要设置的权限级别
     */
    fun savePreferredPermissionLevelSync(permissionLevel: AndroidPermissionLevel) {
        AppLogger.d(TAG, "Saving preferred permission level (sync): $permissionLevel")
        prefs.edit().putString(KEY_PERMISSION_LEVEL, permissionLevel.name).apply()
        AppLogger.d(TAG, "Saved to SharedPreferences (sync): ${permissionLevel.name}")
    }

    /**
     * 检查是否已设置权限级别
     * @return 是否已设置权限级别
     */
    fun isPermissionLevelSet(): Boolean {
        // Check SharedPreferences first (synchronous)
        val hasSharedPrefs = prefs.getString(KEY_PERMISSION_LEVEL, null) != null
        if (hasSharedPrefs) return true
        
        // Fallback to DataStore check
        return runBlocking {
            try {
                preferredPermissionLevelFlow.first() != null
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error checking if permission level is set", e)
                false
            }
        }
    }

    /** 重置权限级别（清除设置） */
    suspend fun resetPermissionLevel() {
        AppLogger.d(TAG, "Resetting permission level")
        // Clear both DataStore and SharedPreferences
        prefs.edit().remove(KEY_PERMISSION_LEVEL).apply()
        try {
            context.androidPermissionDataStore.edit { preferences ->
                preferences.remove(PREFERRED_PERMISSION_LEVEL)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error resetting DataStore", e)
        }
    }
}
