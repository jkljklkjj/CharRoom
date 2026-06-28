package core.di

import core.ChatTransport
import core.QuicClientImpl
import org.koin.dsl.module

/**
 * 桌面平台 DI 模块：QUIC 传输层实现。
 */
val desktopModule = module {
    single<ChatTransport> { QuicClientImpl() }
}
