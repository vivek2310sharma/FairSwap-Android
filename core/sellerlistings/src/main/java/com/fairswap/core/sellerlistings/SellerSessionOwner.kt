package com.fairswap.core.sellerlistings

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SellerSessionOwner {
    private val generationCounter = AtomicLong(0L)
    private val _session = MutableStateFlow<SellerSession?>(null)
    val session: StateFlow<SellerSession?> = _session.asStateFlow()

    @Synchronized
    fun updateSession(userId: String?): SellerSession? {
        val current = _session.value
        if (userId == null) {
            _session.value = null
            return null
        }
        if (current != null && current.userId == userId) {
            return current
        }
        val newSession = SellerSession(userId, generationCounter.incrementAndGet())
        _session.value = newSession
        return newSession
    }

    @Synchronized
    fun currentSession(): SellerSession? = _session.value
}
