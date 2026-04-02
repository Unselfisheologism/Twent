package com.ai.assistance.operit.voice.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryService {
    suspend fun addMemory(instruction: String, userId: String) {
        Log.w("MemoryService", "Memory service not configured. Skipping add memory.")
    }

    suspend fun searchMemory(query: String, userId: String): String {
        Log.w("MemoryService", "Memory service not configured. Skipping search.")
        return "No relevant memories found."
    }
}
