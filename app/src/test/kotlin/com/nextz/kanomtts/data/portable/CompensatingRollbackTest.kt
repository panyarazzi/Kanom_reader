package com.nextz.kanomtts.data.portable

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CompensatingRollbackTest {
    @Test
    fun failureRestoresTheSnapshotBeforePropagating() = runTest {
        var storedValue = "before"
        val failure = IllegalStateException("database transaction failed")

        val thrown = try {
            withCompensatingRollback(
                snapshot = { storedValue },
                operation = {
                    storedValue = "restored"
                    throw failure
                },
                rollback = { previous -> storedValue = previous },
            )
            error("Expected restore failure")
        } catch (caught: IllegalStateException) {
            caught
        }

        assertSame(failure, thrown)
        assertEquals("before", storedValue)
    }

    @Test
    fun successfulOperationDoesNotRunRollback() = runTest {
        var storedValue = "before"
        val result = withCompensatingRollback(
            snapshot = { storedValue },
            operation = {
                storedValue = "restored"
                "ok"
            },
            rollback = { previous -> storedValue = previous },
        )

        assertEquals("ok", result)
        assertEquals("restored", storedValue)
    }

    @Test
    fun cancellationAlsoRunsCompensatingRollback() = runTest {
        var storedValue = "before"
        val thrown = try {
            withCompensatingRollback(
                snapshot = { storedValue },
                operation = {
                    storedValue = "partially-restored"
                    throw CancellationException("cancel restore")
                },
                rollback = { previous -> storedValue = previous },
            )
            error("Expected cancellation")
        } catch (cancelled: CancellationException) {
            cancelled
        }

        assertEquals("cancel restore", thrown.message)
        assertEquals("before", storedValue)
    }

    @Test
    fun rollbackFailureIsAttachedToOriginalFailure() = runTest {
        var storedValue = "before"
        val original = IllegalStateException("primary failure")
        val rollbackFailure = IllegalArgumentException("rollback failure")

        val thrown = try {
            withCompensatingRollback(
                snapshot = { storedValue },
                operation = { storedValue = "changed"; throw original },
                rollback = { throw rollbackFailure },
            )
            error("Expected restore failure")
        } catch (failure: IllegalStateException) {
            failure
        }

        assertSame(original, thrown)
        assertEquals(listOf(rollbackFailure), thrown.suppressed.toList())
    }
}
