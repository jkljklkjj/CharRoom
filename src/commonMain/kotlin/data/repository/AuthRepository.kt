package data.repository

import com.chatlite.i18n.currentStrings
import core.register
import core.sendRegisterVerifyCode
import core.state.AuthState
import core.state.GlobalAppState
import core.verifyRegister
import data.datasource.local.LocalDataSource
import data.datasource.local.LocalDataSourceImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Authentication Repository Handles authentication business logic, coordinates remote and local
 * data sources 直接调用 ApiClient 函数，移除 RemoteDataSource 中间层
 */
class AuthRepository(
    private val localDataSource: LocalDataSource,
    private val requests: AuthRequests = AuthRequests(),
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** Restore credentials once, then publish one authenticated state. */
    suspend fun init() {
        val savedToken = localDataSource.getSavedAccessToken()
        val savedAccount = localDataSource.getSavedAccount()
        val savedRefresh = localDataSource.getSavedRefreshToken()
        if (savedToken.isNullOrBlank() || savedAccount.isNullOrBlank()) {
            _authState.value = AuthState.Unauthenticated
            return
        }
        _authState.value = AuthState.Loading
        core.recoverOperation(
            onFailure = { failure ->
                localDataSource.clearAuth()
                val detail = failure.message ?: currentStrings["auth.network.failed"]
                _authState.value =
                    AuthState.Error(currentStrings["auth.auto.login.error"].format(detail))
            }
        ) {
            val restored = restoreTokens(savedToken, savedRefresh)
            if (restored == null) {
                localDataSource.clearAuth()
                _authState.value = AuthState.Error(currentStrings["auth.auto.login.failed"])
            } else {
                localDataSource.saveAuth(savedAccount, restored.accessToken, restored.refreshToken)
                val profile = loadProfile(restored.accessToken)
                if (profile == null) restoreSavedUserId()
                publish(savedAccount, restored, profile)
            }
        }
    }

    private suspend fun restoreTokens(access: String, refresh: String?): core.LoginTokenBundle? {
        val validated = requests.validate(access)?.takeIf { it.accessToken.isNotBlank() }
        return validated
            ?: refresh
                ?.takeIf { it.isNotBlank() }
                ?.let { requests.refresh(it) }
                ?.takeIf { it.accessToken.isNotBlank() }
    }

    private suspend fun loadProfile(access: String): model.User? =
        core.recoverOperation({ null }) { requests.profile(access) }

    private suspend fun restoreSavedUserId() {
        val userId = localDataSource.getSavedUserId()
        if (userId > 0) GlobalAppState.setCurrentUserId(userId)
    }

    private fun publish(
        account: String,
        tokens: core.LoginTokenBundle,
        profile: model.User?,
    ): AuthState.Authenticated {
        val state =
            AuthState.Authenticated(account, tokens.accessToken, tokens.refreshToken, profile)
        _authState.value = state
        GlobalAppState.updateAuthState(state)
        return state
    }

    /** Login */
    suspend fun login(account: String, password: String, rememberMe: Boolean = false): AuthState {
        _authState.value = AuthState.Loading

        return core.recoverOperation(
            onFailure = { e ->
                // Catch all login exceptions to avoid crash
                val errorMessage = e.message ?: currentStrings["auth.network.retry"]
                val errorState =
                    AuthState.Error(currentStrings["auth.login.error"].format(errorMessage))
                _authState.value = errorState
                errorState
            }
        ) {
            val result = requests.login(account, password)
            if (result != null && result.accessToken.isNotBlank()) {
                val profile = loadProfile(result.accessToken)
                if (rememberMe) {
                    localDataSource.saveAuth(
                        account,
                        result.accessToken,
                        result.refreshToken,
                        profile?.id ?: 0,
                    )
                } else {
                    localDataSource.clearAuth()
                }
                publish(account, result, profile)
            } else {
                val errorState = AuthState.Error(currentStrings["auth.login.failed"])
                _authState.value = errorState
                errorState
            }
        }
    }

    /** Register */
    suspend fun register(username: String, password: String): Result<Int> {
        val result = core.register(username, password)
        return if (result != null && result != -1) {
            Result.success(result)
        } else {
            Result.failure(Exception(currentStrings["auth.register.failed"]))
        }
    }

    /** Verify registration (consistent with web logic) */
    suspend fun verifyRegister(
        username: String,
        password: String,
        email: String = "",
        verifyCode: String = "",
    ): Result<Int> {
        val result = core.verifyRegister(username, password, email, verifyCode)
        return if (result != null && result != -1) {
            Result.success(result)
        } else {
            Result.failure(Exception(currentStrings["auth.register.failed"]))
        }
    }

    /** Send registration verification code */
    suspend fun sendRegisterVerifyCode(email: String): Result<Boolean> =
        core.recoverOperation(
            onFailure = { e ->
                Result.failure(e)
            }
        ) {
            val success = core.sendRegisterVerifyCode(email)
            if (success) {
                Result.success(true)
            } else {
                Result.failure(Exception(currentStrings["auth.code.send.failed"]))
            }
        }

    /** Logout */
    suspend fun logout() {
        localDataSource.clearAuth()
        _authState.value = AuthState.Unauthenticated
        // Sync to GlobalAppState
        GlobalAppState.clearAuth()
    }

    /** Get current auth state */
    fun getCurrentAuthState(): AuthState = _authState.value

    /** Get current token */
    fun getCurrentToken(): String? = (_authState.value as? AuthState.Authenticated)?.accessToken

    /** Get current account */
    fun getCurrentAccount(): String? = (_authState.value as? AuthState.Authenticated)?.account
}

/** Global singleton, compatible with legacy code */
val GlobalAuthRepository = AuthRepository(localDataSource = LocalDataSourceImpl())
