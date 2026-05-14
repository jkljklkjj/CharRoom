package core

import model.GroupMessage
import model.Message

/**
 * WebSocket客户端接口
 */
interface WebSocketClientProvider {
    /**
     * 启动WebSocket连接
     * @param host 服务器地址
     * @param port 服务器端口
     */
    fun start(host: String? = null, port: Int? = null)

    /**
     * 停止WebSocket连接
     */
    fun stop()

    /**
     * 发送二进制消息
     * @param payload 消息内容
     * @param type 消息类型
     * @param targetClientId 目标客户端ID
     * @param expectedResponses 预期响应数量
     * @param callback 发送结果回调
     */
    fun send(
        payload: ByteArray,
        type: MsgType,
        targetClientId: String,
        expectedResponses: Int,
        callback: (Boolean, List<ByteArray>) -> Unit
    )

    /**
     * 发送文本消息
     * @param content 文本内容
     * @param callback 发送结果回调
     */
    fun sendText(content: String, callback: (Boolean) -> Unit)

    /**
     * 检查是否已连接
     * @return 是否连接成功
     */
    fun isConnected(): Boolean

    /**
     * 手动重连
     */
    fun reconnect()

    /**
     * 登出并断开连接
     */
    fun logoutAndDisconnect()

    /**
     * 添加消息接收监听器
     */
    fun addMessageReceiveListener(listener: MessageReceiveListener)

    /**
     * 移除消息接收监听器
     */
    fun removeMessageReceiveListener(listener: MessageReceiveListener)

    /**
     * 添加认证状态监听器
     */
    fun addAuthStateListener(listener: AuthStateListener)

    /**
     * 移除认证状态监听器
     */
    fun removeAuthStateListener(listener: AuthStateListener)

    /**
     * 服务器连接状态
     */
    val isServerConnected: Boolean
}

/**
 * 全局消息接收回调接口
 */
interface MessageReceiveListener {
    /**
     * 收到私聊消息
     */
    fun onPrivateMessageReceived(senderId: Int, message: String, timestamp: Long)

    /**
     * 收到群聊消息
     */
    fun onGroupMessageReceived(groupId: Int, senderId: Int, senderName: String, message: String, timestamp: Long)

    /**
     * 收到聊天助手流式输出（默认空实现，按需覆盖）
     */
    fun onAgentStreamChunk(messageId: String, fullContent: String, done: Boolean, error: Boolean) {}
}

/**
 * 登录状态监听器
 */
fun interface AuthStateListener {
    fun onAuthInvalidated(reason: String)
}

/**
 * 消息类型枚举
 */
enum class MsgType(val wire: String) {
    LOGIN("login"),
    LOGOUT("logout"),
    CHAT("chat"),
    AGENT_CHAT("agentChat"),
    AGENT_CHAT_STREAM("agentChatStream"),
    GROUP_CHAT("groupChat"),
    CHECK("check"),
    HEARTBEAT("heartbeat"),
    ACK("ack"),
    RESPONSE("response"); // 通用响应消息
}

// 全局WebSocket客户端实现，由各平台初始化
lateinit var Chat: WebSocketClientProvider
