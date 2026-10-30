package core

import core.model.VersionCheckResult
import core.state.GlobalAppState
import model.Message
import model.User
import model.toUiUser

/**
 * Api服务
 *
 * @param authToken 可选的固定token，如果不传则动态使用GlobalAppState中的最新token
 */
class ApiService(private val authToken: String? = null) {
    data class LoginTokens(
        val accessToken: String,
        val refreshToken: String,
    )

    /** 获取当前用户信息 */
    suspend fun getCurrentUserProfile(): User? =
        core.getUserInfo(authToken ?: GlobalAppState.currentToken.orEmpty())

    /** 获取好友列表（对 UI 友好） */
    suspend fun fetchFriends(): List<User> =
        core.getFriendList(authToken ?: GlobalAppState.currentToken.orEmpty())

    /** 获取群组列表（对 UI 友好） */
    suspend fun fetchGroups(): List<User> =
        core.getGroupList(authToken ?: GlobalAppState.currentToken.orEmpty()).map {
            it.toUiUser()
        }

    /** 添加好友 */
    suspend fun addFriend(account: String): Boolean =
        core.addFriend(authToken ?: GlobalAppState.currentToken.orEmpty(), account)

    /** 加入群组（仅返回是否成功） */
    suspend fun addGroup(groupId: String): Boolean =
        core.addGroup(authToken ?: GlobalAppState.currentToken.orEmpty(), groupId).isSuccess

    /** 加入群组（返回完整响应，包含 code/message，可用于区分"需要审核"状态） */
    suspend fun addGroupWithResponse(groupId: String): ApiResponse<Unit> =
        core.addGroup(authToken ?: GlobalAppState.currentToken.orEmpty(), groupId)

    /** 查询用户详情 */
    suspend fun getUserDetail(userId: String): User? =
        core.getUserDetail(authToken ?: GlobalAppState.currentToken.orEmpty(), userId)

    /** 查询群组详情 */
    suspend fun getGroupDetail(groupId: String): User? =
        core.getGroupDetail(authToken ?: GlobalAppState.currentToken.orEmpty(), groupId)?.toUiUser()

    /** 获取离线消息 */
    suspend fun getOfflineMessages(): List<Message> =
        core.getOfflineMessages(authToken ?: GlobalAppState.currentToken.orEmpty())

    /** 发送邮箱更新验证码 */
    suspend fun sendEmailUpdateVerifyCode(email: String): Boolean =
        core.sendEmailUpdateVerifyCode(
            authToken ?: GlobalAppState.currentToken.orEmpty(),
            email,
        )

    /** 更新用户个人资料 */
    suspend fun updateUserProfile(
        username: String,
        phone: String,
        signature: String,
        password: String? = null,
    ): Boolean =
        core.updateUserProfile(
            authToken ?: GlobalAppState.currentToken.orEmpty(),
            username,
            phone,
            signature,
            password,
        )

    /** 更新邮箱 */
    suspend fun updateEmail(newEmail: String, verifyCode: String): Boolean =
        core.updateEmail(
            authToken ?: GlobalAppState.currentToken.orEmpty(),
            newEmail,
            verifyCode,
        )

    /** 获取好友申请列表（UI 用 User 类型） */
    suspend fun fetchFriendRequests(): List<User> =
        core.getFriendRequests(authToken ?: GlobalAppState.currentToken.orEmpty()).map { request ->
            User(
                id = request.id,
                username = request.senderName,
                avatarUrl = request.senderAvatar,
                avatarKey = request.avatarKey,
                friendRequestReason = request.message,
            )
        }

    /** 获取群聊申请列表（UI 用 User 类型） */
    suspend fun fetchGroupRequests(): List<User> =
        core.getGroupRequests(authToken ?: GlobalAppState.currentToken.orEmpty()).map { request ->
            User(
                id = request.id,
                username = request.senderName,
                avatarUrl = request.senderAvatar,
            )
        }

    /** 同意好友申请 */
    suspend fun acceptFriend(requestId: String): Boolean =
        core.acceptFriend(authToken ?: GlobalAppState.currentToken.orEmpty(), requestId)

    /** 拒绝好友申请 */
    suspend fun rejectFriend(requestId: String): Boolean =
        core.rejectFriend(authToken ?: GlobalAppState.currentToken.orEmpty(), requestId)

    /** 同意群聊申请 */
    suspend fun acceptGroupApplication(groupId: String, userId: String): Boolean =
        core.acceptGroupApplication(
            authToken ?: GlobalAppState.currentToken.orEmpty(),
            groupId,
            userId,
        )

    /** 拒绝群聊申请 */
    suspend fun rejectGroupApplication(groupId: String, userId: String): Boolean =
        core.rejectGroupApplication(
            authToken ?: GlobalAppState.currentToken.orEmpty(),
            groupId,
            userId,
        )

    /**
     * 检查应用版本更新
     *
     * @param platform 平台：android/desktop/web
     * @param channel 渠道：official/debug等
     */
    suspend fun checkAppVersion(
        platform: String,
        channel: String = "official",
    ): VersionCheckResult? {
        require(channel == "official") { "Only the official release channel is supported" }
        return core.checkAppVersion(
            appVersion = core.update.UpdateRuntime.installedVersion,
            platform = platform,
        )
    }

    // 静态方法，兼容旧版本调用
    companion object {

        /**
         * 添加好友
         *
         * @deprecated 请使用 ApiService 类实例方法
         */
        suspend fun addFriend(account: String): Boolean =
            GlobalAppState.currentToken?.let { ApiService(it).addFriend(account) } ?: false

        /** 加入群组（返回完整响应） */
        suspend fun addGroupWithResponse(groupId: String): ApiResponse<Unit> {
            val token =
                GlobalAppState.currentToken
                    ?: return ApiResponse(code = -1, message = "No auth token")
            return ApiService(token).addGroupWithResponse(groupId)
        }

        /**
         * 查询用户详情
         *
         * @deprecated 请使用 ApiService 类实例方法
         */
        suspend fun getUserDetail(userId: String): User? =
            GlobalAppState.currentToken?.let { ApiService(it).getUserDetail(userId) }

        /**
         * 查询群组详情
         *
         * @deprecated 请使用 ApiService 类实例方法
         */
        suspend fun getGroupDetail(groupId: String): User? =
            GlobalAppState.currentToken?.let { ApiService(it).getGroupDetail(groupId) }

        /**
         * 获取好友申请列表（UI 用 User 类型）
         *
         * @deprecated 请使用 ApiService 类实例方法
         */
        suspend fun fetchFriendRequests(): List<User> =
            GlobalAppState.currentToken?.let { ApiService(it).fetchFriendRequests() } ?: emptyList()

        /**
         * 获取群聊申请列表（UI 用 User 类型）
         *
         * @deprecated 请使用 ApiService 类实例方法
         */
        suspend fun fetchGroupRequests(): List<User> =
            GlobalAppState.currentToken?.let { ApiService(it).fetchGroupRequests() } ?: emptyList()

        /**
         * 同意好友申请
         *
         * @deprecated 请使用 ApiService 类实例方法
         */
        suspend fun acceptFriend(requestId: String): Boolean =
            GlobalAppState.currentToken?.let { ApiService(it).acceptFriend(requestId) } ?: false

        /**
         * 拒绝好友申请
         *
         * @deprecated 请使用 ApiService 类实例方法
         */
        suspend fun rejectFriend(requestId: String): Boolean =
            GlobalAppState.currentToken?.let { ApiService(it).rejectFriend(requestId) } ?: false

        /**
         * 同意群聊申请
         *
         * @deprecated 请使用 ApiService 类实例方法
         */
        suspend fun acceptGroupApplication(groupId: String, userId: String): Boolean =
            GlobalAppState.currentToken?.let {
                ApiService(it).acceptGroupApplication(groupId, userId)
            } ?: false

        /**
         * 拒绝群聊申请
         *
         * @deprecated 请使用 ApiService 类实例方法
         */
        suspend fun rejectGroupApplication(groupId: String, userId: String): Boolean =
            GlobalAppState.currentToken?.let {
                ApiService(it).rejectGroupApplication(groupId, userId)
            } ?: false
    }
}

/** 全局Api服务实例，自动使用最新的认证token */
val GlobalApiService = ApiService()
