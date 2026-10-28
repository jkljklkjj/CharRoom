package core
import kotlin.test.*
class BusinessPollingTest {
    @Test fun idleRoomsBackOffAndFailuresHaveBoundedExponentialDelay(){
        assertEquals(1200,encounterPollDelay("ACTIVE",0))
        assertEquals(15000,encounterPollDelay("IDLE",0))
        assertEquals(4000,encounterPollDelay("ACTIVE",1))
        assertEquals(60000,encounterPollDelay("ACTIVE",100))
    }
}
