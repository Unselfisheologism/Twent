package com.ai.assistance.operit.voice.utilities

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.URL
import java.net.URLConnection

class NetworkConnectivityManager(private val context: Context) {
    
    companion object {
        private const val TAG = "NetworkConnectivityManager"
        private const val CONNECTIVITY_TIMEOUT_MS = 5000L
        private const val TEST_URL = "https://www.google.com"
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    suspend fun isNetworkAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isNetworkConnected()) {
                Log.d(TAG, "Network is not connected")
                return@withContext false
            }
            // Try connectivity check but don't block the request if it fails
            return@withContext try {
                checkInternetConnectivity()
            } catch (e: Exception) {
                Log.d(TAG, "Connectivity check threw exception, assuming connected: ${e.message}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network availability", e)
            return@withContext false
        }
    }
    
    private fun isNetworkConnected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }
    
    private suspend fun checkInternetConnectivity(): Boolean = withTimeoutOrNull(CONNECTIVITY_TIMEOUT_MS) {
        try {
            val url = URL(TEST_URL)
            val connection: URLConnection = url.openConnection()
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.connect()
            Log.d(TAG, "Internet connectivity confirmed")
            true
        } catch (e: IOException) {
            Log.d(TAG, "Internet connectivity check failed: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during internet connectivity check", e)
            false
        }
    } ?: false
    
    suspend fun checkConnectivityWithTimeout(timeoutMs: Long = CONNECTIVITY_TIMEOUT_MS): ConnectivityResult {
        return try {
            withTimeout(timeoutMs) {
                val isAvailable = isNetworkAvailable()
                if (isAvailable) {
                    ConnectivityResult.Success
                } else {
                    ConnectivityResult.NoInternet
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connectivity check failed with timeout", e)
            ConnectivityResult.Timeout
        }
    }
    
    fun registerNetworkCallback(callback: NetworkCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network became available")
                    callback.onNetworkAvailable()
                }
                
                override fun onLost(network: Network) {
                    Log.d(TAG, "Network became unavailable")
                    callback.onNetworkLost()
                }
            })
        }
    }
    
    fun unregisterNetworkCallback(callback: ConnectivityManager.NetworkCallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
    
    sealed class ConnectivityResult {
        object Success : ConnectivityResult()
        object NoInternet : ConnectivityResult()
        object Timeout : ConnectivityResult()
    }
    
    interface NetworkCallback {
        fun onNetworkAvailable()
        fun onNetworkLost()
    }
}
