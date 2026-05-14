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

/**
 * 认证Repository
 * 处理认证相关业务逻辑，协调远程和本地数据源
 */
class AuthRepository(
    private val remoteDataSource: RemoteDataSource,
    private val localDataSource: LocalDataSource
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /**
     * 初始化：尝试自动登录
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
            // 尝试验证token
            val validated = remoteDataSource.validateToken(savedToken)
            if (validated != null && validated.accessToken.isNotBlank()) {
                // token有效，更新本地存储
                localDataSource.saveAuth(savedAccount, validated.accessToken, validated.refreshToken)
                // 获取用户信息
                val userProfile = remoteDataSource.getUserInfo(validated.accessToken)
                val authState = AuthState.Authenticated(
                    account = savedAccount,
                    accessToken = validated.accessToken,
                    refreshToken = validated.refreshToken,
                    userProfile = userProfile
                )
                _authState.value = authState
                // 同步到GlobalAppState
                GlobalAppState.updateAuthState(authState)
                return
            }

            // token无效，尝试刷新
            if (!savedRefreshToken.isNullOrBlank()) {
                val refreshed = remoteDataSource.refreshToken(savedRefreshToken)
                if (refreshed != null && refreshed.accessToken.isNotBlank()) {
                    localDataSource.saveAuth(savedAccount, refreshed.accessToken, refreshed.refreshToken)
                    // 获取用户信息
                    val userProfile = remoteDataSource.getUserInfo(refreshed.accessToken)
                    val authState = AuthState.Authenticated(
                        account = savedAccount,
                        accessToken = refreshed.accessToken,
                        refreshToken = refreshed.refreshToken,
                        userProfile = userProfile
                    )
                    _authState.value = authState
                    // 同步到GlobalAppState
                    GlobalAppState.updateAuthState(authState)
                    return
                }
            }

            // 刷新失败，清除本地存储
            localDataSource.clearAuth()
            _authState.value = AuthState.Error("自动登录失败，请重新登录")
        } catch (e: Exception) {
            // 捕获自动登录异常，避免崩溃
            localDataSource.clearAuth()
            val errorMessage = e.message ?: "网络连接失败"
            _authState.value = AuthState.Error("自动登录失败: $errorMessage")
        }
    }

    /**
     * 登录
     */
    suspend fun login(account: String, password: String, rememberMe: Boolean = false): AuthState {
        _authState.value = AuthState.Loading

        return try {
            val result = remoteDataSource.login(account, password)
            if (result != null && result.accessToken.isNotBlank()) {
                // 登录成功，保存到本地
                localDataSource.saveAuth(account, result.accessToken, result.refreshToken)
                // 获取用户信息
                val userProfile = remoteDataSource.getUserInfo(result.accessToken)
                val authState = AuthState.Authenticated(
                    account = account,
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                    userProfile = userProfile
                )
                _authState.value = authState
                // 同步到GlobalAppState
                GlobalAppState.updateAuthState(authState)
                authState
            } else {
                val errorState = AuthState.Error("登录失败，请检查账号或密码")
                _authState.value = errorState
                errorState
            }
        } catch (e: Exception) {
            // 捕获所有登录异常，避免崩溃
            val errorMessage = e.message ?: "网络连接失败，请检查网络后重试"
            val errorState = AuthState.Error("登录失败: $errorMessage")
            _authState.value = errorState
            errorState
        }
    }

    /**
     * 注册
     */
    suspend fun register(username: String, password: String): Result<Int> {
        val result = remoteDataSource.register(username, password)
        return if (result != -1) {
            Result.success(result)
        } else {
            Result.failure(Exception("注册失败，请稍后重试"))
        }
    }

    /**
     * 验证注册（与网页端逻辑一致）
     */
    suspend fun verifyRegister(username: String, password: String, email: String = "", verifyCode: String = ""): Result<Int> {
        val result = remoteDataSource.verifyRegister(username, password, email, verifyCode)
        return if (result != -1) {
            Result.success(result)
        } else {
            Result.failure(Exception("注册失败，请稍后重试"))
        }
    }

    /**
     * 发送注册验证码
     */
    suspend fun sendRegisterVerifyCode(email: String): Result<Boolean> {
        return try {
            val success = remoteDataSource.sendRegisterVerifyCode(email)
            if (success) {
                Result.success(true)
            } else {
                Result.failure(Exception("验证码发送失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 退出登录
     */
    suspend fun logout() {
        localDataSource.clearAuth()
        _authState.value = AuthState.Unauthenticated
        // 同步到GlobalAppState
        GlobalAppState.clearAuth()
    }

    /**
     * 获取当前认证状态
     */
    fun getCurrentAuthState(): AuthState = _authState.value

    /**
     * 获取当前token
     */
    fun getCurrentToken(): String? {
        return (_authState.value as? AuthState.Authenticated)?.accessToken
    }

    /**
     * 获取当前账号
     */
    fun getCurrentAccount(): String? {
        return (_authState.value as? AuthState.Authenticated)?.account
    }
}

/**
 * 全局单例，兼容旧代码
 */
val GlobalAuthRepository = AuthRepository(
    remoteDataSource = RemoteDataSourceImpl(),
    localDataSource = LocalDataSourceImpl()
)
