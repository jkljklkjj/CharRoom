package com.chatlite.charroom.presentation.viewmodel

import data.repository.ChatRepository
import data.repository.GlobalChatRepository
import kotlinx.coroutines.launch
import presentation.viewmodel.ChatViewModel

/**
 * Android 端 ChatViewModel
 * 扩展基础 ChatViewModel，添加 Android 特有的逻辑。
 * 实时消息通过 Chat (CronetQuicClient) 全局变量处理。
 */
class AndroidChatViewModel(
    chatRepository: ChatRepository = GlobalChatRepository,
    chatState: core.state.ChatState = core.state.GlobalChatState
) : ChatViewModel(chatRepository, chatState) {

    /**
     * 加载离线消息
     */
    fun loadOfflineMessages() {
        coroutineScope.launch {
            val messages = chatRepository.getOfflineMessages()
            messages.forEach { addMessage(it) }
        }
    }
}
