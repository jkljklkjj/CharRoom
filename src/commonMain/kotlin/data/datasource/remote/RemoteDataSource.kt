package data.datasource.remote

import model.Group
import model.Message
import model.User
import core.ApiService as CoreApiService

/**
 * Remote data source interface
 * Defines all remote API operations — network requests only, without business logic or data transformation
 */
interface RemoteDataSource {
    /**
     * Login, returns token pair
     */
    suspend fun login(account: String, password: String): CoreApiService.LoginTokens?

    /**
     * Register, returns account ID
     */
    suspend fun register(username: String, password: String): Int

    /**
     * Verify registration (consistent with web logic), returns account ID
     */
    suspend fun verifyRegister(username: String, password: String, email: String = "", verifyCode: String = ""): Int

    /**
     * Send registration verification code
     */
    suspend fun sendRegisterVerifyCode(email: String): Boolean

    /**
     * Validate token, returns new token pair
     */
    suspend fun validateToken(token: String): CoreApiService.LoginTokens?

    /**
     * Refresh token, returns new token pair
     */
    suspend fun refreshToken(refreshToken: String): CoreApiService.LoginTokens?

    /**
     * Get user profile
     */
    suspend fun getUserInfo(token: String): User?

    /**
     * Get friend list
     */
    suspend fun getFriendList(token: String): List<User>

    /**
     * Get group list
     */
    suspend fun getGroupList(token: String): List<Group>

    /**
     * Add friend
     */
    suspend fun addFriend(token: String, account: String): Boolean

    /**
     * Join group
     */
    suspend fun addGroup(token: String, groupId: String): Boolean

    /**
     * Get user details
     */
    suspend fun getUserDetail(token: String, userId: String): User?

    /**
     * Get group details
     */
    suspend fun getGroupDetail(token: String, groupId: String): Group?

    /**
     * Get offline messages
     */
    suspend fun getOfflineMessages(token: String): List<Message>

    /**
     * Incremental sync messages
     */
    suspend fun syncMessages(token: String, conversationId: String, lastSeqId: Long, limit: Int = 50): core.SyncMessagesResult

    /**
     * Send email update verification code
     */
    suspend fun sendEmailUpdateVerifyCode(token: String, email: String): Boolean

    /**
     * Update user profile
     */
    suspend fun updateUserProfile(
        token: String,
        username: String,
        phone: String,
        signature: String,
        password: String? = null
    ): Boolean

    /**
     * Update email
     */
    suspend fun updateEmail(token: String, newEmail: String, verifyCode: String): Boolean

    /**
     * Get friend request list
     */
    suspend fun getFriendRequests(token: String): List<User>

    /**
     * Get group request list
     */
    suspend fun getGroupRequests(token: String): List<User>

    /**
     * Accept friend request
     */
    suspend fun acceptFriend(token: String, requestId: String): Boolean

    /**
     * Reject friend request
     */
    suspend fun rejectFriend(token: String, requestId: String): Boolean

    /**
     * Delete friend
     */
    suspend fun deleteFriend(token: String, friendId: Int): Boolean

    /**
     * Accept group application
     */
    suspend fun acceptGroupApplication(token: String, groupId: String, userId: String): Boolean

    /**
     * Reject group application
     */
    suspend fun rejectGroupApplication(token: String, groupId: String, userId: String): Boolean
}
