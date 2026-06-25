package com.chatlite.component.call

/**
 * 通话状态
 */
sealed class CallState {
    /** 空闲 */
    data object Idle : CallState()

    /** 发起通话中（等待对方接受） */
    data class Calling(val calleeId: String, val calleeName: String) : CallState()

    /** 收到来电 */
    data class Incoming(val callerId: String, val callerName: String) : CallState()

    /** 通话中 */
    data object Connected : CallState()

    /** 通话结束 */
    data class Ended(val reason: String = "") : CallState()
}

/**
 * 信令消息类型
 */
object CallSignalingType {
    const val OFFER = "offer"
    const val ANSWER = "answer"
    const val ICE_CANDIDATE = "ice_candidate"
    const val HANGUP = "hangup"
    const val REJECT = "reject"
    const val BUSY = "busy"
}

/**
 * 通话配置
 */
object CallConfig {
    /** STUN 服务器（开发用 Google 公共 STUN） */
    val iceServers = listOf(
        "stun:stun.l.google.com:19302"
    )

    /** TURN 服务器（生产需自行部署 coturn，开发时用 STUN 直连） */
    val turnServers = emptyList<String>()
}
