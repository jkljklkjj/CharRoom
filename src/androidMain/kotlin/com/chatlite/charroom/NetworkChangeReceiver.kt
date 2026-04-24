package com.chatlite.charroom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import core.Chat

/**
 * 网络状态变化广播接收器
 * 监听网络连接状态变化，自动重连WebSocket
 */
class NetworkChangeReceiver : BroadcastReceiver() {
    private var isConnected = true

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo

        val hasNetwork = networkInfo?.isConnected == true

        if (hasNetwork && !isConnected) {
            // 网络从断开变为连接，尝试重连
            if (!Chat.isConnected()) {
                Thread {
                    try {
                        Chat.reconnect()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
            }
        }

        isConnected = hasNetwork
    }

    companion object {
        /**
         * 注册网络状态监听器（Android N及以上推荐使用）
         */
        fun registerNetworkCallback(context: Context) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val networkRequest = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        // 网络可用，尝试重连
                        if (!Chat.isConnected()) {
                            Thread {
                                try {
                                    Chat.reconnect()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }.start()
                        }
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        // 网络丢失，更新连接状态
                        Chat.isServerConnected.value = false
                    }
                })
            }
        }
    }
}