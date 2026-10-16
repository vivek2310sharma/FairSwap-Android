package com.fairswap.core.communication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommunicationSessionOwnerTest {

    private lateinit var sessionOwner: CommunicationSessionOwner

    @Before
    fun setUp() {
        sessionOwner = CommunicationSessionOwner()
    }

    @Test
    fun sameUserUpdatePreservesGeneration() {
        val s1 = sessionOwner.updateSession("user1")
        assertNotNull(s1)
        val s2 = sessionOwner.updateSession("user1")

        assertEquals(s1?.generation, s2?.generation)
        assertEquals("user1", s2?.userId)
    }

    @Test
    fun identitySwitchChangesGeneration() {
        val s1 = sessionOwner.updateSession("user1")
        val s2 = sessionOwner.updateSession("user2")

        assertNotNull(s1)
        assertNotNull(s2)
        assertTrue(s2!!.generation > s1!!.generation)
        assertEquals("user2", s2.userId)
    }

    @Test
    fun signOutClearsSession() {
        sessionOwner.updateSession("user1")
        val nullSession = sessionOwner.updateSession(null)

        assertNull(nullSession)
        assertNull(sessionOwner.currentSession())
    }

    @Test
    fun signOutThenSameUserLoginCreatesNewGeneration() {
        val s1 = sessionOwner.updateSession("user1")
        sessionOwner.updateSession(null)
        val s2 = sessionOwner.updateSession("user1")

        assertNotNull(s1)
        assertNotNull(s2)
        assertTrue(s2!!.generation > s1!!.generation)
    }

    @Test
    fun staleExpectedSessionRejected() {
        val s1 = sessionOwner.updateSession("user1")
        assertTrue(sessionOwner.isSessionActive(s1!!))

        val s2 = sessionOwner.updateSession("user2")
        assertFalse(sessionOwner.isSessionActive(s1))
        assertTrue(sessionOwner.isSessionActive(s2!!))
    }
}
