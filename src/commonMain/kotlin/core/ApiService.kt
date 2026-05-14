package core

import core.state.GlobalAppState
import model.Group
import model.Message
import model.User
import model.toUiUser

/**
 * Api服务
 * @param authToken 可选的固定token，如果不传则动态使用GlobalAppState中的最新token
 */
class ApiService(private val authToken: String? = null) {
    data class LoginTokens(
        val accessToken: String,
        val refreshToken: String
    )

    /**
     * 获取当前用户信息
     */
    suspend fun getCurrentUserProfile(): User? {
        return core.getUserInfo(authToken ?: GlobalAppState.currentToken.orEmpty())
    }

    /**
     * 获取好友列表（对 UI 友好）
     */
    suspend fun fetchFriends(): List<User> {
        return core.getFriendList(authToken ?: GlobalAppState.currentToken.orEmpty())
    }

    /**
     * 获取群组列表（对 UI 友好）
     */
    suspend fun fetchGroups(): List<User> {
        return core.getGroupList(authToken ?: GlobalAppState.currentToken.orEmpty()).map { it.toUiUser() }
    }

    /**
     * 获取好友列表
     */
    suspend fun getFriendList(): List<User> {
        return core.getFriendList(authToken ?: GlobalAppState.currentToken.orEmpty())
    }

    /**
     * 获取群组列表
     */
    suspend fun getGroupList(): List<Group> {
        return core.getGroupList(authToken ?: GlobalAppState.currentToken.orEmpty())
    }

    /**
     * 添加好友
     */
    suspend fun addFriend(account: String): Boolean {
        return core.addFriend(authToken ?: GlobalAppState.currentToken.orEmpty(), account)
    }

    /**
     * 加入群组
     */
    suspend fun addGroup(groupId: String): Boolean {
        return core.addGroup(authToken ?: GlobalAppState.currentToken.orEmpty(), groupId)
    }

    /**
     * 查询用户详情
     */
    suspend fun getUserDetail(userId: String): User? {
        return core.getUserDetail(authToken ?: GlobalAppState.currentToken.orEmpty(), userId)
    }

    /**
     * 查询群组详情
     */
    suspend fun getGroupDetail(groupId: String): User? {
        return core.getGroupDetail(authToken ?: GlobalAppState.currentToken.orEmpty(), groupId)?.toUiUser()
    }

    /**
     * 获取离线消息
     */
    suspend fun getOfflineMessages(): List<Message> {
        return core.getOfflineMessages(authToken ?: GlobalAppState.currentToken.orEmpty())
    }

    /**
     * 发送邮箱更新验证码
     */
    suspend fun sendEmailUpdateVerifyCode(email: String): Boolean {
        return core.sendEmailUpdateVerifyCode(authToken ?: GlobalAppState.currentToken.orEmpty(), email)
    }

    /**
     * 更新用户个人资料
     */
    suspend fun updateUserProfile(
        username: String,
        phone: String,
        signature: String,
        password: String? = null
    ): Boolean {
        return core.updateUserProfile(authToken ?: GlobalAppState.currentToken.orEmpty(), username, phone, signature, password)
    }

    /**
     * 更新邮箱
     */
    suspend fun updateEmail(newEmail: String, verifyCode: String): Boolean {
        return core.updateEmail(authToken ?: GlobalAppState.currentToken.orEmpty(), newEmail, verifyCode)
    }

    /**
     * 获取好友申请列表（UI 用 User 类型）
     */
    suspend fun fetchFriendRequests(): List<User> {
        return core.getFriendRequests(authToken ?: GlobalAppState.currentToken.orEmpty())
            .map { request ->
                User(
                    id = request.id,
                    username = request.senderName,
                    avatarUrl = request.senderAvatar
                )
            }
    }

    /**
     * 获取群聊申请列表（UI 用 User 类型）
     */
    suspend fun fetchGroupRequests(): List<User> {
        return core.getGroupRequests(authToken ?: GlobalAppState.currentToken.orEmpty())
            .map { request ->
                User(
                    id = request.id,
                    username = request.senderName,
                    avatarUrl = request.senderAvatar
                )
            }
    }

    /**
     * 同意好友申请
     */
    suspend fun acceptFriend(requestId: String): Boolean {
        return core.acceptFriend(authToken ?: GlobalAppState.currentToken.orEmpty(), requestId)
    }

    /**
     * 拒绝好友申请
     */
    suspend fun rejectFriend(requestId: String): Boolean {
        return core.rejectFriend(authToken ?: GlobalAppState.currentToken.orEmpty(), requestId)
    }

    /**
     * 同意群聊申请
     */
    suspend fun acceptGroupApplication(groupId: String, userId: String): Boolean {
        return core.acceptGroupApplication(authToken ?: GlobalAppState.currentToken.orEmpty(), groupId, userId)
    }

    /**
     * 拒绝群聊申请
     */
    suspend fun rejectGroupApplication(groupId: String, userId: String): Boolean {
        return core.rejectGroupApplication(authToken ?: GlobalAppState.currentToken.orEmpty(), groupId, userId)
    }

    /**
     * 发送私聊消息
     */
    suspend fun sendPrivateMessage(
        receiverId: String,
        content: String,
        messageType: String,
        fileUrl: String? = null,
        fileName: String? = null,
        fileSize: Long? = null,
        replyToMessageId: String? = null
    ): Boolean {
        return core.sendPrivateMessage(
            token = authToken ?: GlobalAppState.currentToken.orEmpty(),
            receiverId = receiverId,
            content = content,
            messageType = messageType,
            fileUrl = fileUrl,
            fileName = fileName,
            fileSize = fileSize,
            replyToMessageId = replyToMessageId
        )
    }

    /**
     * 发送群聊消息
     */
    suspend fun sendGroupMessage(
        groupId: String,
        content: String,
        messageType: String,
        fileUrl: String? = null,
        fileName: String? = null,
        fileSize: Long? = null,
        replyToMessageId: String? = null
    ): Boolean {
        return core.sendGroupMessage(
            token = authToken ?: GlobalAppState.currentToken.orEmpty(),
            groupId = groupId,
            content = content,
            messageType = messageType,
            fileUrl = fileUrl,
            fileName = fileName,
            fileSize = fileSize,
            replyToMessageId = replyToMessageId
        )
    }

    // 静态方法，兼容旧版本调用
    companion object {
        /**
         * 登录，返回 token
         */
        suspend fun login(account: String, password: String): String {
            return core.login(account, password).orEmpty()
        }

        suspend fun loginTokens(account: String, password: String): LoginTokens? {
            val bundle = core.loginTokens(account, password) ?: return null
            if (bundle.accessToken.isBlank()) return null
            return LoginTokens(bundle.accessToken, bundle.refreshToken)
        }

        /**
         * 注册，返回账号 ID
         */
        suspend fun register(username: String, password: String): Int {
            return core.register(username, password) ?: -1
        }

        /**
         * 验证注册（与网页端逻辑一致），返回账号 ID
         */
        suspend fun verifyRegister(username: String, password: String, email: String = "", verifyCode: String = ""): Int {
            return core.verifyRegister(username, password, email, verifyCode) ?: -1
        }

        /**
         * 发送注册验证码
         */
        suspend fun sendRegisterVerifyCode(email: String): Boolean {
            return core.sendRegisterVerifyCode(email)
        }

        /**
         * 获取用户信息
         */
        suspend fun getUserInfo(token: String): User? {
            return core.getUserInfo(token)
        }

        suspend fun validateToken(token: String): LoginTokens? {
            val bundle = core.validateToken(token) ?: return null
            if (bundle.accessToken.isBlank()) return null
            return LoginTokens(bundle.accessToken, bundle.refreshToken)
        }

        suspend fun refreshAccessToken(refreshToken: String): String {
            return core.refreshAccessToken(refreshToken).orEmpty()
        }

        suspend fun refreshTokenBundle(refreshToken: String): LoginTokens? {
            val bundle = core.refreshTokenBundle(refreshToken) ?: return null
            if (bundle.accessToken.isBlank()) return null
            return LoginTokens(bundle.accessToken, bundle.refreshToken)
        }

        /**
         * 获取好友列表（对 UI 友好）
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).fetchFriends()"))
        suspend fun fetchFriends(): List<User> {
            return GlobalAppState.currentToken?.let { ApiService(it).fetchFriends() } ?: emptyList()
        }

        /**
         * 获取群组列表（对 UI 友好）
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).fetchGroups()"))
        suspend fun fetchGroups(): List<User> {
            return GlobalAppState.currentToken?.let { ApiService(it).fetchGroups() } ?: emptyList()
        }

        /**
         * 获取好友列表
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).getFriendList()"))
        suspend fun getFriendList(token: String): List<User> {
            return ApiService(token).getFriendList()
        }

        /**
         * 获取群组列表
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).getGroupList()"))
        suspend fun getGroupList(token: String): List<Group> {
            return ApiService(token).getGroupList()
        }

        /**
         * 添加好友
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).addFriend(account)"))
        suspend fun addFriend(account: String): Boolean {
            return GlobalAppState.currentToken?.let { ApiService(it).addFriend(account) } ?: false
        }

        /**
         * 加入群组
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).addGroup(groupId)"))
        suspend fun addGroup(groupId: String): Boolean {
            return GlobalAppState.currentToken?.let { ApiService(it).addGroup(groupId) } ?: false
        }

        /**
         * 查询用户详情
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).getUserDetail(userId)"))
        suspend fun getUserDetail(userId: String): User? {
            return GlobalAppState.currentToken?.let { ApiService(it).getUserDetail(userId) }
        }

        /**
         * 查询群组详情
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).getGroupDetail(groupId)"))
        suspend fun getGroupDetail(groupId: String): User? {
            return GlobalAppState.currentToken?.let { ApiService(it).getGroupDetail(groupId) }
        }

        /**
         * 获取离线消息
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).getOfflineMessages()"))
        suspend fun getOfflineMessages(): List<Message> {
            return GlobalAppState.currentToken?.let { ApiService(it).getOfflineMessages() } ?: emptyList()
        }

        /**
         * 发送邮箱更新验证码
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).sendEmailUpdateVerifyCode(email)"))
        suspend fun sendEmailUpdateVerifyCode(email: String): Boolean {
            return GlobalAppState.currentToken?.let { ApiService(it).sendEmailUpdateVerifyCode(email) } ?: false
        }

        /**
         * 更新用户个人资料
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).updateUserProfile(username, phone, signature, password)"))
        suspend fun updateUserProfile(
            username: String,
            phone: String,
            signature: String,
            password: String? = null
        ): Boolean {
            return GlobalAppState.currentToken?.let {
                ApiService(it).updateUserProfile(username, phone, signature, password)
            } ?: false
        }

        /**
         * 更新邮箱
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).updateEmail(newEmail, verifyCode)"))
        suspend fun updateEmail(newEmail: String, verifyCode: String): Boolean {
            return GlobalAppState.currentToken?.let { ApiService(it).updateEmail(newEmail, verifyCode) } ?: false
        }

        /**
         * 获取好友申请列表（UI 用 User 类型）
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).fetchFriendRequests()"))
        suspend fun fetchFriendRequests(): List<User> {
            return GlobalAppState.currentToken?.let { ApiService(it).fetchFriendRequests() } ?: emptyList()
        }

        /**
         * 获取群聊申请列表（UI 用 User 类型）
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).fetchGroupRequests()"))
        suspend fun fetchGroupRequests(): List<User> {
            return GlobalAppState.currentToken?.let { ApiService(it).fetchGroupRequests() } ?: emptyList()
        }

        /**
         * 同意好友申请
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).acceptFriend(requestId)"))
        suspend fun acceptFriend(requestId: String): Boolean {
            return GlobalAppState.currentToken?.let { ApiService(it).acceptFriend(requestId) } ?: false
        }

        /**
         * 拒绝好友申请
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).rejectFriend(requestId)"))
        suspend fun rejectFriend(requestId: String): Boolean {
            return GlobalAppState.currentToken?.let { ApiService(it).rejectFriend(requestId) } ?: false
        }

        /**
         * 同意群聊申请
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).acceptGroupApplication(groupId, userId)"))
        suspend fun acceptGroupApplication(groupId: String, userId: String): Boolean {
            return GlobalAppState.currentToken?.let {
                ApiService(it).acceptGroupApplication(groupId, userId)
            } ?: false
        }

        /**
         * 拒绝群聊申请
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).rejectGroupApplication(groupId, userId)"))
        suspend fun rejectGroupApplication(groupId: String, userId: String): Boolean {
            return GlobalAppState.currentToken?.let {
                ApiService(it).rejectGroupApplication(groupId, userId)
            } ?: false
        }

        /**
         * 发送私聊消息
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).sendPrivateMessage(receiverId, content, messageType, fileUrl, fileName, fileSize, replyToMessageId)"))
        suspend fun sendPrivateMessage(
            receiverId: String,
            content: String,
            messageType: String,
            fileUrl: String? = null,
            fileName: String? = null,
            fileSize: Long? = null,
            replyToMessageId: String? = null
        ): Boolean {
            return GlobalAppState.currentToken?.let {
                ApiService(it).sendPrivateMessage(
                    receiverId = receiverId,
                    content = content,
                    messageType = messageType,
                    fileUrl = fileUrl,
                    fileName = fileName,
                    fileSize = fileSize,
                    replyToMessageId = replyToMessageId
                )
            } ?: false
        }

        /**
         * 发送群聊消息
         * @deprecated 请使用 ApiService 类实例方法
         */
        @Deprecated("Use ApiService instance method instead", ReplaceWith("ApiService(token).sendGroupMessage(groupId, content, messageType, fileUrl, fileName, fileSize, replyToMessageId)"))
        suspend fun sendGroupMessage(
            groupId: String,
            content: String,
            messageType: String,
            fileUrl: String? = null,
            fileName: String? = null,
            fileSize: Long? = null,
            replyToMessageId: String? = null
        ): Boolean {
            return GlobalAppState.currentToken?.let {
                ApiService(it).sendGroupMessage(
                    groupId = groupId,
                    content = content,
                    messageType = messageType,
                    fileUrl = fileUrl,
                    fileName = fileName,
                    fileSize = fileSize,
                    replyToMessageId = replyToMessageId
                )
            } ?: false
        }
    }
}

/**
 * 全局Api服务实例，自动使用最新的认证token
 */
val GlobalApiService = ApiService()