package component.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposerInteractionTest {
    @Test fun candidateConfirmationDoesNotSend() {
        assertFalse(shouldSubmitComposer(true, true, false, false, false, false))
    }
    @Test fun modifiersKeepEditingAndEmptyInputDoesNotSend() {
        assertFalse(shouldSubmitComposer(false, false, false, false, false, false))
        assertFalse(shouldSubmitComposer(true, false, true, false, false, false))
        assertFalse(shouldSubmitComposer(true, false, false, true, false, false))
        assertFalse(shouldSubmitComposer(true, false, false, false, true, false))
        assertFalse(shouldSubmitComposer(true, false, false, false, false, true))
    }
    @Test fun plainEnterSendsReadyInput() {
        assertTrue(shouldSubmitComposer(true, false, false, false, false, false))
    }
}
