package core.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Koin 初始化工具
 */
object KoinInitializer {
    private var isInitialized = false

    /**
     * 初始化Koin
     */
    fun init(appDeclaration: KoinAppDeclaration = {}): KoinApplication {
        if (isInitialized) {
            stopKoin()
        }
        val koinApp = startKoin {
            appDeclaration()
            modules(allModules)
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
