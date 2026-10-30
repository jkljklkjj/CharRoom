package core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryPagingTest {
    @Test
    fun loadRequiresAnInitializedIdleViewportAtTheBeginning() {
        assertTrue(shouldLoadEarlierHistory(true, false, true, 0, 0))
        assertFalse(shouldLoadEarlierHistory(false, false, true, 0, 0))
        assertFalse(shouldLoadEarlierHistory(true, true, true, 0, 0))
        assertFalse(shouldLoadEarlierHistory(true, false, false, 0, 0))
        assertFalse(shouldLoadEarlierHistory(true, false, true, 1, 0))
        assertFalse(shouldLoadEarlierHistory(true, false, true, 0, 1))
    }
}
