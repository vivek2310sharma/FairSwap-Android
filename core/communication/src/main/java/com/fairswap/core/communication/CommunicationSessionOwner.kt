package com.fairswap.core.communication

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

class CommunicationSessionOwner {
    private val generationCounter = AtomicLong(0L)
    private val _session = MutableStateFlow<CommunicationSession?>(null)
    val session: StateFlow<CommunicationSession?> = _session.asStateFlow()

    @Synchronized
    fun updateSession(userId: String?): CommunicationSession? {
        if (userId == null) {
            _session.value = null
            return null
        }

        val current = _session.value
        if (current != null && current.userId == userId) {
            return current
        }

        val newSession = CommunicationSession(userId, generationCounter.incrementAndGet())
        _session.value = newSession
        return newSession
    }

    @Synchronized
    fun currentSession(): CommunicationSession? = _session.value

    @Synchronized
    fun isSessionActive(session: CommunicationSession?): Boolean {
        if (session == null) return false
        val current = _session.value ?: return false
        return current.userId == session.userId && current.generation == session.generation
    }
}
