package presentation.viewmodel

import core.state.AuthState
import data.repository.AuthRepository
import data.repository.GlobalAuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 认证ViewModel
 * 处理登录注册相关的UI逻辑和状态
 */
class AuthViewModel(
    private val authRepository: AuthRepository = GlobalAuthRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {
    // 认证状态，暴露给UI层观察
    val authState: StateFlow<AuthState> = authRepository.authState

    /**
     * 初始化，尝试自动登录
     */
    fun init() {
        coroutineScope.launch {
            authRepository.init()
        }
    }

    /**
     * 登录
     */
    fun login(account: String, password: String, rememberMe: Boolean = false) {
        coroutineScope.launch {
            authRepository.login(account, password, rememberMe)
        }
    }

    /**
     * 注册
     */
    fun register(username: String, password: String, onResult: (Result<Int>) -> Unit) {
        coroutineScope.launch {
            val result = authRepository.register(username, password)
            onResult(result)
        }
    }

    /**
     * 验证注册（与网页端逻辑一致）
     */
    fun verifyRegister(username: String, password: String, email: String = "", verifyCode: String = "", onResult: (Result<Int>) -> Unit) {
        coroutineScope.launch {
            val result = authRepository.verifyRegister(username, password, email, verifyCode)
            onResult(result)
        }
    }

    /**
     * 发送注册验证码
     */
    fun sendRegisterVerifyCode(email: String, onResult: (Result<Boolean>) -> Unit) {
        coroutineScope.launch {
            val result = authRepository.sendRegisterVerifyCode(email)
            onResult(result)
        }
    }

    /**
     * 退出登录
     */
    fun logout() {
        coroutineScope.launch {
            authRepository.logout()
        }
    }

    /**
     * 获取当前token
     */
    fun getCurrentToken(): String? = authRepository.getCurrentToken()

    /**
     * 获取当前账号
     */
    fun getCurrentAccount(): String? = authRepository.getCurrentAccount()
}

// 全局单例，兼容旧代码
val GlobalAuthViewModel = AuthViewModel()
