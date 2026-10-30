package core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ApiCancellationTest {
    @Test fun uploadDoesNotConvertCancellationToNull() = cancelledRequest {
        uploadFile("/files", byteArrayOf(1), "fixture.txt", "fixture")
    }

    @Test fun privateSendDoesNotConvertCancellationToFailure() = cancelledRequest {
        sendPrivateMessage("fixture", "42", "fixture", "TEXT")
    }

    @Test fun groupSendDoesNotConvertCancellationToFailure() = cancelledRequest {
        sendGroupMessage("fixture", "7", "fixture", "TEXT")
    }

    @Test fun onlineStatusDoesNotConvertCancellationToEmptyResults() = cancelledRequest {
        batchOnlineStatus("fixture", listOf(42))
    }

    private fun cancelledRequest(request: suspend () -> Unit) = runBlocking<Unit> {
        val originalBase = ApiEndpoints.url("")
        ApiEndpoints.setBaseUrl("http://127.0.0.1:1/api")
        try {
            var completed = false
            var cancellation: CancellationException? = null
            val task = launch(start = CoroutineStart.UNDISPATCHED) {
                currentCoroutineContext().cancel()
                try {
                    request()
                    completed = true
                } catch (expected: CancellationException) {
                    cancellation = expected
                }
            }
            task.join()
            assertFalse(completed, "A cancelled request must not return a normal API result")
            assertNotNull(cancellation)
        } finally {
            ApiEndpoints.setBaseUrl(originalBase)
        }
    }
}
