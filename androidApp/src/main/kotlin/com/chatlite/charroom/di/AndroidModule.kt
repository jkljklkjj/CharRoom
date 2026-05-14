package com.chatlite.charroom.di

import com.chatlite.charroom.data.datasource.remote.AndroidRemoteDataSource
import com.chatlite.charroom.presentation.viewmodel.AndroidAuthViewModel
import com.chatlite.charroom.presentation.viewmodel.AndroidChatViewModel
import data.datasource.remote.RemoteDataSource
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module
import presentation.viewmodel.AuthViewModel
import presentation.viewmodel.ChatViewModel

/**
 * Android特有依赖模块
 */
val androidModule = module {
    // 提供 AndroidRemoteDataSource 的具体实现，并把 RemoteDataSource 指向它
    single<AndroidRemoteDataSource> { AndroidRemoteDataSource() }
    single<RemoteDataSource> { get<AndroidRemoteDataSource>() }

    // 提供Android AuthViewModel（使用无反射的viewModelOf）
    viewModelOf(::AndroidAuthViewModel)

    // 暴露AuthViewModel接口
    single<AuthViewModel> { get<AndroidAuthViewModel>().viewModel }

    // 提供Android特有的ChatViewModel
    single {
        AndroidChatViewModel(
            remoteDataSource = get<AndroidRemoteDataSource>()
        )
    }

    // 把基础ChatViewModel指向Android实现
    single<ChatViewModel> { get<AndroidChatViewModel>() }
}
