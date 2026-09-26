package com.fairswap.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthCallbackCoordinatorTest {
    @Test
    fun staleCallbackCannotReplaceNewerResult() {
        val coordinator = AuthCallbackCoordinator()
        val first = coordinator.begin()!!
        assertEquals(null, coordinator.begin())
        coordinator.fail(first)
        val second = coordinator.begin()!!
        coordinator.succeed(first, AuthCallbackKind.Recovery)
        assertEquals(AuthCallbackEvent.Processing(second), coordinator.events.value)
        coordinator.fail(second)
        assertEquals(AuthCallbackEvent.Failed(second), coordinator.events.value)
        coordinator.acknowledge(second)
        assertEquals(null, coordinator.events.value)
    }

    @Test
    fun staleSuccessCannotChangeRecoveryState() {
        val coordinator = AuthCallbackCoordinator()
        val first = coordinator.begin()!!
        coordinator.fail(first)
        coordinator.begin()
        var changed = false
        val accepted = coordinator.succeed(first, AuthCallbackKind.Recovery) { changed = true }
        assertEquals(false, accepted)
        assertEquals(false, changed)
    }

    @Test
    fun terminalCallbackCannotBeOverwritten() {
        val coordinator = AuthCallbackCoordinator()
        val sequence = coordinator.begin()!!
        assertEquals(true, coordinator.succeed(sequence, AuthCallbackKind.Confirmation))
        coordinator.fail(sequence)
        assertEquals(
            AuthCallbackEvent.Succeeded(sequence, AuthCallbackKind.Confirmation),
            coordinator.events.value,
        )
        assertEquals(false, coordinator.succeed(sequence, AuthCallbackKind.Recovery))
    }
}
