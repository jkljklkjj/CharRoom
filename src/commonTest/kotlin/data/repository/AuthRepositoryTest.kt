package data.repository

import core.LoginTokenBundle
import core.state.AuthState
import core.state.GlobalAppState
import data.datasource.local.LocalDataSource
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import model.GroupMessage
import model.Message
import model.User

class AuthRepositoryTest {
    private class MemoryStorage : LocalDataSource {
        data class Saved(
            val account: String,
            val access: String,
            val refresh: String,
            val user: Int,
        )

        var saved: Saved? = null
        var writes = 0

        override suspend fun saveAuth(
            account: String,
            accessToken: String,
            refreshToken: String,
            userId: Int,
        ) {
            saved = Saved(account, accessToken, refreshToken, userId)
            writes++
        }

        override suspend fun getSavedAccount() = saved?.account

        override suspend fun getSavedAccessToken() = saved?.access

        override suspend fun getSavedRefreshToken() = saved?.refresh

        override suspend fun getSavedUserId() = saved?.user ?: 0

        override suspend fun clearAuth() {
            saved = null
        }

        override suspend fun saveUserProfile(user: User) = Unit

        override suspend fun getUserProfile(): User? = null

        override suspend fun saveFriends(friends: List<User>) = Unit

        override suspend fun getFriends() = emptyList<User>()

        override suspend fun saveGroups(groups: List<User>) = Unit

        override suspend fun getGroups() = emptyList<User>()

        override suspend fun saveMessages(messages: List<Message>) = Unit

        override suspend fun getMessages(userId: Int) = emptyList<Message>()

        override suspend fun saveGroupMessages(messages: List<GroupMessage>) = Unit

        override suspend fun getGroupMessages(groupId: Int) = emptyList<GroupMessage>()

        override suspend fun clearAll() = clearAuth()
    }

    private fun requests() =
        AuthRequests(
            login = { _, _ -> LoginTokenBundle("fixture-access", "fixture-refresh") },
            profile = { User(42, "fixture") },
            validate = { null },
            refresh = { null },
        )

    @Test
    fun rememberedLoginPersistsCredentialsAndProfileId() = runBlocking {
        val local = MemoryStorage()
        try {
            val repository = AuthRepository(local, requests())
            assertIs<AuthState.Authenticated>(repository.login("42", "fixture", rememberMe = true))
            assertEquals("fixture-access", local.saved?.access)
            assertEquals(42, local.saved?.user)
        } finally {
            GlobalAppState.clearAuth()
        }
    }

    @Test
    fun sessionOnlyLoginClearsOldStoredCredentialsButKeepsTheLiveSession() = runBlocking {
        val local = MemoryStorage()
        local.saved = MemoryStorage.Saved("old", "old-access", "old-refresh", 7)
        try {
            val repository = AuthRepository(local, requests())
            assertIs<AuthState.Authenticated>(repository.login("42", "fixture", rememberMe = false))
            assertNull(local.saved)
            assertEquals(0, local.writes)
            assertEquals("fixture-access", repository.getCurrentToken())
        } finally {
            GlobalAppState.clearAuth()
        }
    }

    @Test
    fun cancellationWhileLoadingAProfileCannotPersistOrPublishALogin() = runBlocking {
        val local = MemoryStorage()
        val cancelled = CancellationException("fixture")
        val network = requests().copy(profile = { throw cancelled })
        try {
            val repository = AuthRepository(local, network)
            assertSame(
                cancelled,
                assertFailsWith<CancellationException> {
                    repository.login("42", "fixture", rememberMe = true)
                },
            )
            assertNull(local.saved)
            assertNull(repository.getCurrentToken())
        } finally {
            GlobalAppState.clearAuth()
        }
    }

    @Test
    fun restorationUsesRefreshOnlyAfterValidationFails() = runBlocking {
        val local = MemoryStorage()
        local.saved = MemoryStorage.Saved("42", "old-access", "fixture-refresh", 42)
        var refreshed = 0
        val network =
            requests()
                .copy(
                    refresh = {
                        assertEquals("fixture-refresh", it)
                        refreshed++
                        LoginTokenBundle("new-access", "new-refresh")
                    }
                )
        try {
            val repository = AuthRepository(local, network)
            repository.init()
            assertIs<AuthState.Authenticated>(repository.getCurrentAuthState())
            assertEquals(1, refreshed)
            assertEquals("new-access", repository.getCurrentToken())
        } finally {
            GlobalAppState.clearAuth()
        }
    }
}
