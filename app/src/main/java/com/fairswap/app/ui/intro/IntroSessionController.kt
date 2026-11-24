package com.fairswap.app.ui.intro

import java.util.concurrent.atomic.AtomicBoolean

object IntroSessionController {
    private val hasPlayedInProcess = AtomicBoolean(false)

    /**
     * Determines whether the cold-launch intro should play.
     * Returns true ONLY on the first cold process call when reduced motion is disabled.
     * Subsequent calls within the same process session return false.
     */
    fun shouldPlayIntro(isReducedMotion: Boolean): Boolean {
        if (isReducedMotion) {
            hasPlayedInProcess.set(true)
            return false
        }
        return !hasPlayedInProcess.getAndSet(true)
    }

    /**
     * Resets process memory flag for unit testing.
     */
    fun resetForTest() {
        hasPlayedInProcess.set(false)
    }
}
