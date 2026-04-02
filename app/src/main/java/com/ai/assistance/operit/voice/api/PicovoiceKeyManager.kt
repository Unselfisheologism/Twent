package com.ai.assistance.operit.voice.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PicovoiceKeyManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PicovoiceKeyManager"
        private const val PREFS_NAME = "PicovoicePrefs"
        private const val KEY_ACCESS_KEY = "access_key"
        private const val KEY_USER_PROVIDED_KEY = "user_provided_access_key"
    }
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    suspend fun getAccessKey(): String? = withContext(Dispatchers.IO) {
        try {
            val userKey = getUserProvidedKey()
            if (!userKey.isNullOrBlank()) {
                Log.d(TAG, "Using user-provided Picovoice access key")
                return@withContext userKey
            }
            
            val cachedKey = getCachedAccessKey()
            if (cachedKey != null) {
                Log.d(TAG, "Using cached Picovoice access key")
                return@withContext cachedKey
            }
            
            Log.w(TAG, "No Picovoice access key available. User must provide one.")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access key", e)
            return@withContext null
        }
    }

    private fun getCachedAccessKey(): String? {
        return sharedPreferences.getString(KEY_ACCESS_KEY, null)
    }
    
    private fun saveAccessKeyToCache(accessKey: String) {
        sharedPreferences.edit {
            putString(KEY_ACCESS_KEY, accessKey)
        }
    }
    
    fun clearCache() {
        sharedPreferences.edit {
            remove(KEY_ACCESS_KEY)
        }
        Log.d(TAG, "Cleared cached Picovoice access key")
    }

    fun saveUserProvidedKey(accessKey: String) {
        sharedPreferences.edit {
            putString(KEY_USER_PROVIDED_KEY, accessKey)
        }
    }

    fun getUserProvidedKey(): String? {
        return sharedPreferences.getString(KEY_USER_PROVIDED_KEY, null)
    }
} 
