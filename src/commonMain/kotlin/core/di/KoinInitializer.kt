package core.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/**
 * Koin 初始化工具
 */
object KoinInitializer {
    private var isInitialized = false

    /**
     * 初始化Koin
     * @param platformModules 平台特定模块（如 desktop/CLI 的传输层实现）
     * @param appDeclaration 额外的 Koin 配置
     */
    fun init(
        platformModules: List<Module> = emptyList(),
        appDeclaration: KoinAppDeclaration = {}
    ): KoinApplication {
        if (isInitialized) {
            stopKoin()
        }
        val koinApp = startKoin {
            appDeclaration()
            modules(allModules + platformModules)
        }
        isInitialized = true
        return koinApp
    }

    /**
     * 销毁Koin
     */
    fun destroy() {
        if (isInitialized) {
            stopKoin()
            isInitialized = false
        }
    }
}
