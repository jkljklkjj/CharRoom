package core.di

import data.datasource.local.LocalDataSource
import data.datasource.local.LocalDataSourceImpl
import data.repository.AuthRepository
import data.repository.ChatRepository
import org.koin.core.module.Module
import org.koin.dsl.module
import presentation.viewmodel.AuthViewModel
import presentation.viewmodel.ChatViewModel
import presentation.viewmodel.ProfileViewModel

/**
 * 应用依赖模块
 * 注意: ViewModels 目前仍使用 Global 单例，未通过 Koin 注入
 */
val appModule: Module = module {
    // 数据源
    single<LocalDataSource> { LocalDataSourceImpl() }

    // Repository
    single { AuthRepository(get()) }
    single { ChatRepository(get(), get()) }

    // ViewModel（暂未使用，保留供后续迁移）
    single { AuthViewModel(get()) }
    single { ChatViewModel(get(), get()) }
    single { ProfileViewModel(get(), get()) }
}

/**
 * 所有模块列表
 */
val allModules = listOf(appModule)
