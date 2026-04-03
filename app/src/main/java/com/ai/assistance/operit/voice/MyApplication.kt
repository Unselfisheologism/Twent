package com.ai.assistance.operit.voice

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PurchasesUpdatedListener
import com.ai.assistance.operit.voice.intents.IntentRegistry
import com.ai.assistance.operit.voice.intents.impl.DialIntent
import com.ai.assistance.operit.voice.intents.impl.EmailComposeIntent
import com.ai.assistance.operit.voice.intents.impl.ShareTextIntent
import com.ai.assistance.operit.voice.intents.impl.ViewUrlIntent

object MyApplication {
    private var _appContext: Context? = null
    val appContext: Context
        get() = _appContext ?: throw IllegalStateException("MyApplication not initialized. Call init() first.")
    
    private var _billingClient: BillingClient? = null
    val billingClient: BillingClient
        get() = _billingClient ?: throw IllegalStateException("BillingClient not initialized. Call init() first.")
    
    private var initialized = false
    
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        
        _appContext = context.applicationContext
        
        _billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener(object : PurchasesUpdatedListener {
                override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<com.android.billingclient.api.Purchase>?) {
                    // Handled by FreemiumManager
                }
            })
            .enablePendingPurchases()
            .build()
        
        _billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {}
            override fun onBillingServiceDisconnected() {}
        })
        
        IntentRegistry.register(DialIntent())
        IntentRegistry.register(ViewUrlIntent())
        IntentRegistry.register(ShareTextIntent())
        IntentRegistry.register(EmailComposeIntent())
        IntentRegistry.init(context.applicationContext)
    }
}
