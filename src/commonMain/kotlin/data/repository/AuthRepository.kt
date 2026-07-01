package data.repository

import core.state.AuthState
import core.state.GlobalAppState
import data.datasource.local.LocalDataSource
import data.datasource.local.LocalDataSourceImpl
import data.datasource.remote.RemoteDataSource
import data.datasource.remote.RemoteDataSourceImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.chatlite.i18n.currentStrings

/**
 * Authentication Repository
 * Handles authentication business logic, coordinates remote and local data sources
 */
class AuthRepository(
    private val remoteDataSource: RemoteDataSource,
    private val localDataSource: LocalDataSource
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /**
     * Initialize: attempt auto login
     */
    suspend fun init() {
        val savedToken = localDataSource.getSavedAccessToken()
        val savedAccount = localDataSource.getSavedAccount()
        val savedRefreshToken = localDataSource.getSavedRefreshToken()

        if (savedToken.isNullOrBlank() || savedAccount.isNullOrBlank()) {
            _authState.value = AuthState.Unauthenticated
            return
        }

        _authState.value = AuthState.Loading

        try {
            // Try to validate token
            val validated = remoteDataSource.validateToken(savedToken)
            if (validated != null && validated.accessToken.isNotBlank()) {
                // Token is valid, update local storage
                localDataSource.saveAuth(savedAccount, validated.accessToken, validated.refreshToken)
                // Get user profile (失败时兜底用本地缓存的 userId)
                val userProfile = runCatching {
                    remoteDataSource.getUserInfo(validated.accessToken)
                }.getOrNull()
                if (userProfile == null) {
                    // getUserInfo 失败时从本地存储恢复 userId
                    val savedUserId = localDataSource.getSavedUserId()
                    if (savedUserId > 0) {
                        GlobalAppState.setCurrentUserId(savedUserId)
                    }
                }
                val authState = AuthState.Authenticated(
                    account = savedAccount,
                    accessToken = validated.accessToken,
                    refreshToken = validated.refreshToken,
                    userProfile = userProfile
                )
                _authState.value = authState
                GlobalAppState.updateAuthState(authState)
                return
            }

            // Token is invalid, try to refresh
            if (!savedRefreshToken.isNullOrBlank()) {
                val refreshed = remoteDataSource.refreshToken(savedRefreshToken)
                if (refreshed != null && refreshed.accessToken.isNotBlank()) {
                    localDataSource.saveAuth(savedAccount, refreshed.accessToken, refreshed.refreshToken)
                    val userProfile = runCatching {
                        remoteDataSource.getUserInfo(refreshed.accessToken)
                    }.getOrNull()
                    if (userProfile == null) {
                        val savedUserId = localDataSource.getSavedUserId()
                        if (savedUserId > 0) {
                            GlobalAppState.setCurrentUserId(savedUserId)
                        }
                    }
                    val authState = AuthState.Authenticated(
                        account = savedAccount,
                        accessToken = refreshed.accessToken,
                        refreshToken = refreshed.refreshToken,
                        userProfile = userProfile
                    )
                    _authState.value = authState
                    GlobalAppState.updateAuthState(authState)
                    return
                }
            }

            // Refresh failed, clear local storage
            localDataSource.clearAuth()
            _authState.value = AuthState.Error(currentStrings["auth.auto.login.failed"])
        } catch (e: Exception) {
            // Catch auto-login exception to avoid crash
            localDataSource.clearAuth()
            val errorMessage = e.message ?: currentStrings["auth.network.failed"]
            _authState.value = AuthState.Error(currentStrings["auth.auto.login.error"].format(errorMessage))
        }
    }

    /**
     * Login
     */
    suspend fun login(account: String, password: String, rememberMe: Boolean = false): AuthState {
        _authState.value = AuthState.Loading

        return try {
            val result = remoteDataSource.login(account, password)
            if (result != null && result.accessToken.isNotBlank()) {
                // Login successful, save locally
                val userProfile = runCatching {
                    remoteDataSource.getUserInfo(result.accessToken)
                }.getOrNull()
                val userId = userProfile?.id ?: 0
                localDataSource.saveAuth(account, result.accessToken, result.refreshToken, userId)
                val authState = AuthState.Authenticated(
                    account = account,
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                    userProfile = userProfile
                )
                _authState.value = authState
                // Sync to GlobalAppState
                GlobalAppState.updateAuthState(authState)
                authState
            } else {
                val errorState = AuthState.Error(currentStrings["auth.login.failed"])
                _authState.value = errorState
                errorState
            }
        } catch (e: Exception) {
            // Catch all login exceptions to avoid crash
            val errorMessage = e.message ?: currentStrings["auth.network.retry"]
            val errorState = AuthState.Error(currentStrings["auth.login.error"].format(errorMessage))
            _authState.value = errorState
            errorState
        }
    }

    /**
     * Register
     */
    suspend fun register(username: String, password: String): Result<Int> {
        val result = remoteDataSource.register(username, password)
        return if (result != -1) {
            Result.success(result)
        } else {
            Result.failure(Exception(currentStrings["auth.register.failed"]))
        }
    }

    /**
     * Verify registration (consistent with web logic)
     */
    suspend fun verifyRegister(username: String, password: String, email: String = "", verifyCode: String = ""): Result<Int> {
        val result = remoteDataSource.verifyRegister(username, password, email, verifyCode)
        return if (result != -1) {
            Result.success(result)
        } else {
            Result.failure(Exception(currentStrings["auth.register.failed"]))
        }
    }

    /**
     * Send registration verification code
     */
    suspend fun sendRegisterVerifyCode(email: String): Result<Boolean> {
        return try {
            val success = remoteDataSource.sendRegisterVerifyCode(email)
            if (success) {
                Result.success(true)
            } else {
                Result.failure(Exception(currentStrings["auth.code.send.failed"]))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Logout
     */
    suspend fun logout() {
        localDataSource.clearAuth()
        _authState.value = AuthState.Unauthenticated
        // Sync to GlobalAppState
        GlobalAppState.clearAuth()
    }

    /**
     * Get current auth state
     */
    fun getCurrentAuthState(): AuthState = _authState.value

    /**
     * Get current token
     */
    fun getCurrentToken(): String? {
        return (_authState.value as? AuthState.Authenticated)?.accessToken
    }

    /**
     * Get current account
     */
    fun getCurrentAccount(): String? {
        return (_authState.value as? AuthState.Authenticated)?.account
    }
}

/**
 * Global singleton, compatible with legacy code
 */
val GlobalAuthRepository = AuthRepository(
    remoteDataSource = RemoteDataSourceImpl(),
    localDataSource = LocalDataSourceImpl()
)
