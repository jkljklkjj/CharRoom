package core

import com.chatlite.proto.MessageProtos
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class MessageProtocolTest {
    @Test
    fun privateMessagesHaveAnIdentityForAckAndDeduplication() {
        val wrapper = MessageProtos.MessageWrapper.parseFrom(buildChatPayload("42", "fixture", timestamp = 123L))
        assertTrue(wrapper.chat.messageId.isNotBlank())
    }

    @Test
    fun groupMessagesHaveAnIdentityAndTimestamp() {
        val wrapper = MessageProtos.MessageWrapper.parseFrom(buildGroupChatPayload("7", "fixture"))
        assertTrue(wrapper.groupChat.messageId.isNotBlank())
        assertTrue(wrapper.groupChat.timestamp > 0)
    }

    @Test
    fun retryPreservesTheMessageIdChosenByTheViewModel() {
        val privateMessage = MessageProtos.MessageWrapper.parseFrom(
            buildChatPayload("42", "fixture", timestamp = 123L, messageId = "stable-private"))
        val groupMessage = MessageProtos.MessageWrapper.parseFrom(
            buildGroupChatPayload("7", "fixture", messageId = "stable-group", timestamp = 123L))
        assertEquals("stable-private", privateMessage.chat.messageId)
        assertEquals("stable-group", groupMessage.groupChat.messageId)
        assertEquals(123L, groupMessage.groupChat.timestamp)
    }

    @Test
    fun routingTypeIsTheGeneratedProtocolEnum() {
        assertEquals(MessageProtos.MessageWrapperType.CHAT, MsgType.CHAT)
        assertEquals(MessageProtos.MessageWrapperType.FRIEND_ACCEPTED, MsgType.FRIEND_ACCEPTED)
    }
}
