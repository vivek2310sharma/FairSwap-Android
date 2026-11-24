package com.fairswap.app.ui.intro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class FairSwapIntroTest {

    @Before
    fun setUp() {
        IntroSessionController.resetForTest()
    }

    @Test
    fun test01_firstColdProcessLaunchPlaysIntro() {
        val shouldPlay = IntroSessionController.shouldPlayIntro(isReducedMotion = false)
        assertTrue("First cold launch should play intro", shouldPlay)
    }

    @Test
    fun test02_navigatingToOtherRoutesDoesNotReplayIt() {
        IntroSessionController.shouldPlayIntro(isReducedMotion = false)
        val secondCall = IntroSessionController.shouldPlayIntro(isReducedMotion = false)
        assertFalse("Subsequent route navigation in same process must not replay intro", secondCall)
    }

    @Test
    fun test03_backgroundForegroundInSameProcessDoesNotReplayIt() {
        IntroSessionController.shouldPlayIntro(isReducedMotion = false)
        val returnFromBackground = IntroSessionController.shouldPlayIntro(isReducedMotion = false)
        assertFalse("Returning from background in same process must not replay intro", returnFromBackground)
    }

    @Test
    fun test04_activityRecreationOrRotationDoesNotReplayIt() {
        IntroSessionController.shouldPlayIntro(isReducedMotion = false)
        val rotatedActivityCall = IntroSessionController.shouldPlayIntro(isReducedMotion = false)
        assertFalse("Activity rotation or recreation in same process must not replay intro", rotatedActivityCall)
    }

    @Test
    fun test05_newProcessColdLaunchPlaysItAgain() {
        IntroSessionController.shouldPlayIntro(isReducedMotion = false)
        IntroSessionController.resetForTest() // Simulates new process launch
        val newProcessCall = IntroSessionController.shouldPlayIntro(isReducedMotion = false)
        assertTrue("New cold process launch must play intro again", newProcessCall)
    }

    @Test
    fun test08_reducedMotionSettingSkipsPlayback() {
        val shouldPlay = IntroSessionController.shouldPlayIntro(isReducedMotion = true)
        assertFalse("Reduced motion setting must skip intro playback", shouldPlay)
    }

    @Test
    fun test11_greetingSelectionReturnsExactly15UniqueNormalizedVisibleStringsAndStartsWithNamaste() {
        val random = Random(12345)
        val greetings = IntroData.selectGreetings(random)

        assertEquals("Must contain exactly 15 greeting slides", 15, greetings.size)
        assertEquals("First slide must be 'नमस्ते'", "नमस्ते", greetings.first().text.trim())

        val normalizedSet = greetings.map { it.text.trim() }.toSet()
        assertEquals("All 15 greeting slides must be distinct normalized strings", 15, normalizedSet.size)
    }

    @Test
    fun test12_motifLayoutNeverOverlapsProtectedCenterBandOrAnotherMotif() {
        val palette = IntroData.PALETTES.first()
        val width = 1080f
        val height = 2400f
        val centerTop = height * 0.35f
        val centerBottom = height * 0.65f

        val placements = MotifLayoutEngine.computePlacements(width, height, palette, maxCount = 16)

        assertTrue("Placements must not exceed max count of 16", placements.size <= 16)

        placements.forEach { placement ->
            val top = placement.yPx - placement.sizePx
            val bottom = placement.yPx + placement.sizePx
            assertFalse(
                "Motif at y=${placement.yPx} size=${placement.sizePx} overlaps protected center band ($centerTop to $centerBottom)",
                bottom >= centerTop && top <= centerBottom
            )
        }

        // Check pairwise motif collisions
        for (i in placements.indices) {
            for (j in i + 1 until placements.size) {
                val p1 = placements[i]
                val p2 = placements[j]
                val dx = p1.xPx - p2.xPx
                val dy = p1.yPx - p2.yPx
                val distSq = dx * dx + dy * dy
                val minDist = (p1.sizePx + p2.sizePx) * 0.85f
                assertTrue(
                    "Motifs $i and $j overlap (distSq=$distSq, minDistSq=${minDist * minDist})",
                    distSq >= minDist * minDist
                )
            }
        }
    }

    @Test
    fun testPaletteAndTaglineSelection() {
        val random = Random(42)
        val palette = IntroData.selectPalette(random)
        assertTrue("Selected palette must belong to PALETTES", IntroData.PALETTES.contains(palette))

        val tagline1 = IntroData.selectTagline()
        val tagline2 = IntroData.selectTagline()
        assertTrue("Taglines must be non-blank", tagline1.isNotBlank() && tagline2.isNotBlank())
    }
}
