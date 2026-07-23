package core.di

import data.datasource.local.LocalDataSource
import data.datasource.local.LocalDataSourceImpl
import data.repository.AuthRepository
import data.repository.ChatRepository
import core.state.ChatState
import org.koin.core.module.Module
import org.koin.dsl.module
import presentation.viewmodel.AuthViewModel
import presentation.viewmodel.ChatViewModel
import presentation.viewmodel.ProfileViewModel

/**
 * 应用依赖模块
 */
val appModule: Module = module {
    // 数据源
    single<LocalDataSource> { LocalDataSourceImpl() }

    // 共享状态
    single { ChatState() }

    // Repository
    single { AuthRepository(get()) }
    single { ChatRepository(get(), get()) }

    // ViewModel
    single { AuthViewModel(get()) }
    single { ChatViewModel(get(), get()) }
    single { ProfileViewModel(get(), get()) }
}

/**
 * 所有模块列表
 */
val allModules = listOf(appModule)
