package com.ai.assistance.operit.voice.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.voice.MyApplication
import com.ai.assistance.operit.voice.utilities.NetworkConnectivityManager
import com.ai.assistance.operit.voice.utilities.NetworkNotifier

/**
 * A stub memory service. Mem0 API integration has been removed.
 */
class MemoryService {

    suspend fun addMemory(instruction: String, userId: String) {
        Log.d("MemoryService", "Memory storage disabled")
    }

    suspend fun getMemories(userId: String): List<String> {
        return emptyList()
    }

    suspend fun deleteAllMemories(userId: String) {
        Log.d("MemoryService", "Memory deletion disabled")
    }
}
