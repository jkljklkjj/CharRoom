package core

import core.state.ChatState
import kotlinx.coroutines.runBlocking
import model.Message
import model.MessageDeliveryState
import kotlin.test.*

class MessageDeliveryStateTest {
    @Test fun pendingIsNotAcceptedAndLateFailureCannotRollBackAConfirmedMessage() = runBlocking {
        val state = ChatState()
        state.addMessage(Message(1, "fixture", true, 2, 1, false, "fixture", deliveryState = MessageDeliveryState.QUEUED))
        assertFalse(state.messages.value.single().isSent)
        state.updateDeliveryState("fixture", MessageDeliveryState.SENDING)
        assertEquals(MessageDeliveryState.SENDING, state.messages.value.single().deliveryState)
        state.updateMessageSentStatus("fixture", true)
        state.updateMessageSentStatus("fixture", false)
        assertEquals(MessageDeliveryState.ACCEPTED, state.messages.value.single().deliveryState)
        assertTrue(state.messages.value.single().isSent)
    }
    @Test fun interruptedSendsAreRecoverableAfterRestartWithoutClaimingTheyWereAccepted() {
        assertEquals(MessageDeliveryState.FAILED, MessageDeliveryState.QUEUED.afterRestart())
        assertEquals(MessageDeliveryState.FAILED, MessageDeliveryState.SENDING.afterRestart())
        assertEquals(MessageDeliveryState.ACCEPTED, MessageDeliveryState.ACCEPTED.afterRestart())
    }
}
