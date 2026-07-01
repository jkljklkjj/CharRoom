package com.chatlite.charroom.core

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
import org.koin.core.context.GlobalContext
import com.chatlite.charroom.presentation.viewmodel.AndroidChatViewModel
import timber.log.Timber

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
        private var networkCallback: ConnectivityManager.NetworkCallback? = null
        private var broadcastReceiver: NetworkChangeReceiver? = null

        /**
         * 注册网络状态回调（Android 7.0+推荐使用）
         */
        fun registerNetworkCallback(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                // 避免重复注册
                if (networkCallback != null) {
                    unregisterNetworkCallback(context)
                }

                networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        super.onAvailable(network)
                        listener?.onNetworkConnected()
                        // 通知ViewModel网络已恢复
                        try {
                            val chatViewModel: AndroidChatViewModel? = GlobalContext.get().getOrNull()
                            chatViewModel?.reconnect()
                        } catch (e: Exception) {
                            Timber.e(e, "网络恢复时获取ViewModel失败")
                        }
                    }

                    override fun onLost(network: android.net.Network) {
                        super.onLost(network)
                        listener?.onNetworkDisconnected()
                        // 通知ViewModel网络已断开
                        try {
                            val chatViewModel: AndroidChatViewModel? = GlobalContext.get().getOrNull()
                            chatViewModel?.onNetworkDisconnected()
                        } catch (e: Exception) {
                            Timber.e(e, "网络断开时获取ViewModel失败")
                        }
                    }
                }

                // 注册默认网络回调
                connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
            }
        }

        /**
         * 注销网络状态回调
         */
        fun unregisterNetworkCallback(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && networkCallback != null) {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                try {
                    connectivityManager.unregisterNetworkCallback(networkCallback!!)
                } catch (e: IllegalArgumentException) {
                    // 回调未注册，忽略
                    Timber.w(e, "网络回调未注册，无需注销")
                }
                networkCallback = null
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return

        @Suppress("DEPRECATION")
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
