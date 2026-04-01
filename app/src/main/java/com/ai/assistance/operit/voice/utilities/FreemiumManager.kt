package com.ai.assistance.operit.voice.utilities

import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryPurchasesAsync
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

class FreemiumManager(private val context: Context) {

    private val db = Firebase.firestore
    private val auth = Firebase.auth
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
    }

    suspend fun getDeveloperMessage(): String {
        return try {
            val document = db.collection("settings").document("freemium").get().await()
            document.getString("developerMessage") ?: ""
        } catch (e: Exception) {
            Log.e("FreemiumManager", "Error fetching developer message from Firestore.", e)
            ""
        }
    }
    suspend fun isUserSubscribed(): Boolean {
        val currentUser = auth.currentUser ?: return false // If not logged in, not pro
        return try {
            val document = db.collection("users").document(currentUser.uid).get().await()
            if (document.exists()) {
                val plan = document.getString("plan")
                plan == "pro"
            } else {
                false // Document doesn't exist, so user can't be pro
            }
        } catch (e: Exception) {
            Logger.e("FreemiumManager", "Error checking user plan from Firestore", e)
            false // In case of error, default to not pro
        }
    }



    suspend fun provisionUserIfNeeded() {
        val currentUser = auth.currentUser ?: return
        val userDocRef = db.collection("users").document(currentUser.uid)

        try {
            val document = userDocRef.get().await()
            if (!document.exists()) {
                Logger.d("FreemiumManager", "Provisioning new user: ${currentUser.uid}")
                val newUser = hashMapOf(
                    "email" to currentUser.email,
                    "plan" to "free",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                userDocRef.set(newUser).await()
            }
        } catch (e: Exception) {
            Logger.e("FreemiumManager", "Error provisioning user", e)
        }
    }

    suspend fun getTasksRemaining(): Long? {
        if (isUserSubscribed()) return Long.MAX_VALUE
        val currentUser = auth.currentUser ?: return null
        return try {
            val document = db.collection("users").document(currentUser.uid).get().await()
            document.getLong("tasksRemaining")
        } catch (e: Exception) {
            Logger.e("FreemiumManager", "Error fetching tasks remaining", e)
            null
        }
    }

    suspend fun canPerformTask(): Boolean {
        if (isUserSubscribed()) return true
        val currentUser = auth.currentUser ?: return false

        return try {
            val document = db.collection("users").document(currentUser.uid).get().await()
            val tasksRemaining = document.getLong("tasksRemaining") ?: 0
            Logger.d("FreemiumManager", "User has $tasksRemaining tasks remaining today.")
            tasksRemaining > 0
        } catch (e: Exception) {
            Logger.e("FreemiumManager", "Error fetching user task count", e)
            false
        }
    }

    suspend fun decrementTaskCount() {
        if (isUserSubscribed()) return
        val currentUser = auth.currentUser ?: return

        val userDocRef = db.collection("users").document(currentUser.uid)
        
        try {
            userDocRef.update("tasksRemaining", FieldValue.increment(-1)).await()
            Logger.d("FreemiumManager", "Successfully decremented task count for user ${currentUser.uid}.")
        } catch (e: Exception) {
            Logger.e("FreemiumManager", "Failed to decrement task count.", e)
        }
    }
}