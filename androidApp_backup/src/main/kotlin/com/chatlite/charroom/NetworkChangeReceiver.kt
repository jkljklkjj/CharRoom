package com.chatlite.charroom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NetworkChangeReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastNetworkState: Boolean = true

    // 网络状态变化监听器
    interface NetworkStateListener {
        fun onNetworkConnected()
        fun onNetworkDisconnected()
    }

    companion object {
        var listener: NetworkStateListener? = null
        private var receiver: NetworkChangeReceiver? = null

        /**
         * 注册网络状态回调（Android 7.0+推荐使用）
         */
        fun registerNetworkCallback(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val networkRequest = android.net.NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                receiver = NetworkChangeReceiver()
                connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        super.onAvailable(network)
                        listener?.onNetworkConnected()
                        // 通知NetworkRepository网络已恢复
                        NetworkRepository.getInstance().reconnect()
                    }

                    override fun onLost(network: android.net.Network) {
                        super.onLost(network)
                        listener?.onNetworkDisconnected()
                        // 通知NetworkRepository网络已断开
                        NetworkRepository.getInstance().onNetworkDisconnected()
                    }
                })
            }
        }

        /**
         * 注销网络状态回调
         */
        fun unregisterNetworkCallback(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && receiver != null) {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(receiver as ConnectivityManager.NetworkCallback)
                receiver = null
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        when (intent.action) {
            ConnectivityManager.CONNECTIVITY_ACTION,
            WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                val isConnected = isNetworkAvailable(context)
                if (isConnected != lastNetworkState) {
                    lastNetworkState = isConnected
                    onNetworkStateChanged(isConnected)
                }
            }
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            networkInfo != null && networkInfo.isConnected
        }
    }

    private fun onNetworkStateChanged(isConnected: Boolean) {
        scope.launch {
            // 通知应用网络状态变化
            if (isConnected) {
                // 网络恢复，尝试重连
                listener?.onNetworkConnected()
            } else {
                // 网络断开，更新状态
                listener?.onNetworkDisconnected()
            }
        }
    }
}
