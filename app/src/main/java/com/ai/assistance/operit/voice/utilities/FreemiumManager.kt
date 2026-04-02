package com.ai.assistance.operit.voice.utilities

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import java.util.Calendar

class FreemiumManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener { _, _ -> }
        .build()
    
    init {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingClientStateListener) {}
            override fun onBillingServiceDisconnected() {}
        })
    }

    companion object {
        @Volatile
        private var INSTANCE: FreemiumManager? = null
        
        fun getInstance(context: Context): FreemiumManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FreemiumManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        const val DAILY_TASK_LIMIT = 15
        private const val PRO_SKU = "pro"
        private const val PREFS_NAME = "freemium_prefs"
        private const val KEY_IS_PRO = "is_pro"
        private const val KEY_TASKS_REMAINING = "tasks_remaining"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
    }

    suspend fun getDeveloperMessage(): String {
        return prefs.getString("developerMessage", "") ?: ""
    }

    suspend fun isUserSubscribed(): Boolean {
        return prefs.getBoolean(KEY_IS_PRO, false)
    }

    suspend fun setUserSubscribed(isPro: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PRO, isPro).apply()
    }

    suspend fun provisionUserIfNeeded() {
        resetDailyTasksIfNeeded()
    }

    private fun resetDailyTasksIfNeeded() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastReset = prefs.getInt(KEY_LAST_RESET_DATE, -1)
        
        if (lastReset != today) {
            prefs.edit()
                .putInt(KEY_LAST_RESET_DATE, today)
                .putLong(KEY_TASKS_REMAINING, DAILY_TASK_LIMIT.toLong())
                .apply()
            Logger.d("FreemiumManager", "Reset daily tasks for day $today")
        }
    }

    suspend fun getTasksRemaining(): Long? {
        if (isUserSubscribed()) return Long.MAX_VALUE
        resetDailyTasksIfNeeded()
        return prefs.getLong(KEY_TASKS_REMAINING, DAILY_TASK_LIMIT.toLong())
    }

    suspend fun canPerformTask(): Boolean {
        if (isUserSubscribed()) return true
        val tasksRemaining = getTasksRemaining() ?: 0
        return tasksRemaining > 0
    }

    suspend fun decrementTaskCount() {
        if (isUserSubscribed()) return
        
        val current = prefs.getLong(KEY_TASKS_REMAINING, DAILY_TASK_LIMIT.toLong())
        if (current > 0) {
            prefs.edit().putLong(KEY_TASKS_REMAINING, current - 1).apply()
            Logger.d("FreemiumManager", "Decremented task count to ${current - 1}")
        }
    }
}
