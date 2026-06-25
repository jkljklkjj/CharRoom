package data.datasource.local

import model.GroupMessage
import model.Message
import model.User

/**
 * Local data source interface
 * Defines all local storage operations
 */
interface LocalDataSource {
    /**
     * Save auth credentials
     */
    suspend fun saveAuth(account: String, accessToken: String, refreshToken: String)

    /**
     * Get saved account
     */
    suspend fun getSavedAccount(): String?

    /**
     * Get saved access token
     */
    suspend fun getSavedAccessToken(): String?

    /**
     * Get saved refresh token
     */
    suspend fun getSavedRefreshToken(): String?

    /**
     * Clear auth credentials
     */
    suspend fun clearAuth()

    /**
     * Save user profile
     */
    suspend fun saveUserProfile(user: User)

    /**
     * Get locally saved user profile
     */
    suspend fun getUserProfile(): User?

    /**
     * Save friend list
     */
    suspend fun saveFriends(friends: List<User>)

    /**
     * Get locally saved friend list
     */
    suspend fun getFriends(): List<User>

    /**
     * Save group list
     */
    suspend fun saveGroups(groups: List<User>)

    /**
     * Get locally saved group list
     */
    suspend fun getGroups(): List<User>

    /**
     * Save private messages
     */
    suspend fun saveMessages(messages: List<Message>)

    /**
     * Get locally saved private messages
     */
    suspend fun getMessages(userId: Int): List<Message>

    /**
     * Save group messages
     */
    suspend fun saveGroupMessages(messages: List<GroupMessage>)

    /**
     * Get locally saved group messages
     */
    suspend fun getGroupMessages(groupId: Int): List<GroupMessage>

    /**
     * Clear all local data
     */
    suspend fun clearAll()
}
