package core.di

import data.datasource.local.LocalDataSource
import data.datasource.local.LocalDataSourceImpl
import data.datasource.remote.RemoteDataSource
import data.datasource.remote.RemoteDataSourceImpl
import data.repository.AuthRepository
import data.repository.ChatRepository
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
    single<RemoteDataSource> { RemoteDataSourceImpl() }
    single<LocalDataSource> { LocalDataSourceImpl() }

    // Repository
    single { AuthRepository(get(), get()) }
    single { ChatRepository(get(), get(), get()) }

    // ViewModel
    single { AuthViewModel(get()) }
    single { ChatViewModel(get(), get()) }
    single { ProfileViewModel(get(), get()) }
}

/**
 * 所有模块列表
 */
val allModules = listOf(appModule)
