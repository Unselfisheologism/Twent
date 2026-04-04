package com.ai.assistance.operit.voice.utilities

import java.util.concurrent.atomic.AtomicInteger

/**
 * A thread-safe, singleton object to manage and rotate a list of API keys.
 * Keys are loaded from user preferences.
 */
object ApiKeyManager {

    private val apiKeys = mutableListOf<String>()
    private val currentIndex = AtomicInteger(0)

    fun setApiKeys(keys: List<String>) {
        apiKeys.clear()
        apiKeys.addAll(keys)
        currentIndex.set(0)
    }

    fun getNextKey(): String {
        if (apiKeys.isEmpty()) {
            throw IllegalStateException("API key list is empty. Please configure an API key in settings.")
        }
        val index = currentIndex.getAndIncrement() % apiKeys.size
        return apiKeys[index]
    }
}
