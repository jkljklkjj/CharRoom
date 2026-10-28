package cli

import core.*
import core.state.AuthState
import core.state.GlobalAppState
import data.datasource.local.LocalDataSource

/** Headless authentication uses the same REST/token models without constructing GUI repositories. */
class CliAuthentication(private val storage: LocalDataSource) {
    suspend fun init() {
        val account = storage.getSavedAccount() ?: return
        val access = storage.getSavedAccessToken() ?: return
        val bundle = validateToken(access) ?: storage.getSavedRefreshToken()?.let { refreshTokenBundle(it) } ?: return
        activate(account, bundle)
    }
    suspend fun login(account: String, password: String): AuthState {
        val bundle = loginTokens(account, password) ?: return AuthState.Error("登录失败")
        return activate(account, bundle) ?: AuthState.Error("无法确认账号资料，请稍后重试")
    }
    private suspend fun activate(account: String, bundle: LoginTokenBundle): AuthState.Authenticated? {
        val profile = getUserInfo(bundle.accessToken) ?: return null
        check(profile.id > 0)
        storage.saveAuth(account, bundle.accessToken, bundle.refreshToken, profile.id)
        return AuthState.Authenticated(account, bundle.accessToken, bundle.refreshToken, profile).also { GlobalAppState.updateAuthState(it) }
    }
}
