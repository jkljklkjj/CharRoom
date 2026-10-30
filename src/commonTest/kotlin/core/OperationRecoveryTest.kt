package core

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException

class OperationRecoveryTest {
    @Test
    fun successfulOperationsDoNotRunTheFallback() {
        assertEquals(42, recoverOperation({ error("Unexpected fallback") }) { 42 })
    }

    @Test
    fun checkedAndRuntimeFailuresKeepTheirOriginalIdentity() {
        for (failure in listOf(IOException("fixture"), IllegalStateException("fixture"))) {
            val result =
                recoverOperation({
                    assertSame(failure, it)
                    "recovered"
                }) {
                    throw failure
                }
            assertEquals("recovered", result)
        }
    }

    @Test
    fun cancellationNeverBecomesANormalFailureResult() {
        val cancelled = CancellationException("fixture")
        assertSame(
            cancelled,
            assertFailsWith<CancellationException> {
                recoverOperation({ error("Cancellation reached the fallback") }) { throw cancelled }
            },
        )
    }

    @Test
    fun jvmErrorsNeverReachApplicationFallbacks() {
        val fatal = AssertionError("fixture")
        assertSame(
            fatal,
            assertFailsWith<AssertionError> {
                recoverOperation({ error("JVM error reached the fallback") }) { throw fatal }
            },
        )
    }
}
