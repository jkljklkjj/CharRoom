package core.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import model.User

/**
 * 统一操作结果封装
 */
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: Exception, val message: String? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

/**
 * 认证状态
 */
sealed class AuthState {
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class Authenticated(
        val account: String,// 账号
        val accessToken: String,
        val refreshToken: String,
        val userProfile: User? = null
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * 全局应用状态
 * 所有全局状态统一在这里管理，通过StateFlow暴露，只能通过公开方法修改
 */
class AppState {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /**
     * 更新认证状态
     */
    fun updateAuthState(newState: AuthState) {
        _authState.value = newState
    }

    /**
     * 获取当前认证令牌，仅在已认证状态下有效
     */
    val currentToken: String?
        get() = (_authState.value as? AuthState.Authenticated)?.accessToken

    /**
     * 获取当前用户账号，仅在已认证状态下有效
     */
    val currentAccount: String?
        get() = (_authState.value as? AuthState.Authenticated)?.account

    /**
     * 获取当前用户ID，仅在已认证状态下有效
     */
    val currentUserId: Int?
        get() = (_authState.value as? AuthState.Authenticated)?.userProfile?.id

    /**
     * 清除认证状态（退出登录）
     */
    fun clearAuth() {
        _authState.value = AuthState.Unauthenticated
    }
}

// 全局单例AppState，用于兼容旧代码，后续逐步改为依赖注入
val GlobalAppState = AppState()
