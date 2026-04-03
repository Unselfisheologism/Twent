package com.ai.assistance.operit.voice

import android.app.Application
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

class MyApplication : Application(), PurchasesUpdatedListener {

    companion object {
        lateinit var appContext: Context
            private set

        lateinit var billingClient: BillingClient
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                // Billing setup handled by FreemiumManager
            }
            override fun onBillingServiceDisconnected() {
                // Will reconnect when needed
            }
        })

        IntentRegistry.register(DialIntent())
        IntentRegistry.register(ViewUrlIntent())
        IntentRegistry.register(ShareTextIntent())
        IntentRegistry.register(EmailComposeIntent())
        IntentRegistry.init(this)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<com.android.billingclient.api.Purchase>?) {
        // Handled by FreemiumManager
    }
}
