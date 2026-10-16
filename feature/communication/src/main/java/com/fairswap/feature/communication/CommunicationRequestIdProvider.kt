package com.fairswap.feature.communication

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface CommunicationRequestIdProvider {
    fun generateRequestId(): String
}

@Singleton
class DefaultCommunicationRequestIdProvider @Inject constructor() : CommunicationRequestIdProvider {
    override fun generateRequestId(): String = UUID.randomUUID().toString()
}
