package core

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlinx.coroutines.CancellationException

/**
 * Converts an operation failure at an API, UI or callback boundary into its documented fallback.
 * Cancellation and JVM errors are control flow, so they always escape the boundary.
 */
@OptIn(ExperimentalContracts::class)
@Suppress("TooGenericExceptionCaught") // A boundary accepts arbitrary collaborator Exceptions.
inline fun <T> recoverOperation(onFailure: (Exception) -> T, operation: () -> T): T {
    contract {
        callsInPlace(operation, InvocationKind.AT_MOST_ONCE)
        callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE)
    }
    return try {
        operation()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        onFailure(failure)
    }
}
