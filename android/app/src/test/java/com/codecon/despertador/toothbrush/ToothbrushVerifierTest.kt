package com.codecon.despertador.toothbrush

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToothbrushVerifierTest {

    @Test
    fun fewerConsecutiveFramesThanRequiredDoesNotConfirm() {
        val verifier = ToothbrushVerifier(requiredFrames = 3, minScore = 0.5f)

        verifier.feed(listOf(Detection("toothbrush", 0.9f)), timeSeconds = 0.0)
        assertEquals(1, verifier.consecutiveFrames)
        assertFalse(verifier.isConfirmed)

        verifier.feed(listOf(Detection("toothbrush", 0.85f)), timeSeconds = 0.1)
        assertEquals(2, verifier.consecutiveFrames)
        assertFalse(verifier.isConfirmed)
    }

    @Test
    fun requiredConsecutiveFramesWithScoreAboveMinConfirms() {
        val verifier = ToothbrushVerifier(requiredFrames = 3, minScore = 0.5f)

        verifier.feed(listOf(Detection("toothbrush", 0.6f)), timeSeconds = 0.0)
        verifier.feed(listOf(Detection("toothbrush", 0.7f)), timeSeconds = 0.1)
        verifier.feed(listOf(Detection("toothbrush", 0.8f)), timeSeconds = 0.2)

        assertEquals(3, verifier.consecutiveFrames)
        assertTrue(verifier.isConfirmed)
    }

    @Test
    fun frameWithoutToothbrushResetsSequence() {
        val verifier = ToothbrushVerifier(requiredFrames = 3, minScore = 0.5f)

        verifier.feed(listOf(Detection("toothbrush", 0.9f)), timeSeconds = 0.0)
        verifier.feed(listOf(Detection("toothbrush", 0.9f)), timeSeconds = 0.1)
        assertEquals(2, verifier.consecutiveFrames)

        // Frame without toothbrush resets sequence to 0
        verifier.feed(emptyList(), timeSeconds = 0.2)
        assertEquals(0, verifier.consecutiveFrames)
        assertFalse(verifier.isConfirmed)

        // Needs 3 fresh consecutive frames to confirm
        verifier.feed(listOf(Detection("toothbrush", 0.9f)), timeSeconds = 0.3)
        assertEquals(1, verifier.consecutiveFrames)
        assertFalse(verifier.isConfirmed)
    }

    @Test
    fun detectionWithScoreBelowMinDoesNotCountAndResets() {
        val verifier = ToothbrushVerifier(requiredFrames = 3, minScore = 0.5f)

        verifier.feed(listOf(Detection("toothbrush", 0.9f)), timeSeconds = 0.0)
        assertEquals(1, verifier.consecutiveFrames)

        // Score 0.49f is below 0.5f minScore -> frame does not count
        verifier.feed(listOf(Detection("toothbrush", 0.49f)), timeSeconds = 0.1)
        assertEquals(0, verifier.consecutiveFrames)
        assertFalse(verifier.isConfirmed)
    }

    @Test
    fun detectionWithDifferentLabelDoesNotCount() {
        val verifier = ToothbrushVerifier(requiredFrames = 3, minScore = 0.5f)

        verifier.feed(listOf(Detection("toothbrush", 0.9f)), timeSeconds = 0.0)
        assertEquals(1, verifier.consecutiveFrames)

        // Different label e.g. "hairbrush" or "bottle" with high score does not count
        verifier.feed(listOf(Detection("hairbrush", 0.99f)), timeSeconds = 0.1)
        assertEquals(0, verifier.consecutiveFrames)
        assertFalse(verifier.isConfirmed)
    }

    @Test
    fun caseInsensitiveLabelMatching() {
        val verifier = ToothbrushVerifier(requiredFrames = 3, minScore = 0.5f)

        verifier.feed(listOf(Detection("ToothBrush", 0.9f)), timeSeconds = 0.0)
        verifier.feed(listOf(Detection("TOOTHBRUSH", 0.9f)), timeSeconds = 0.1)
        verifier.feed(listOf(Detection("toothbrush", 0.9f)), timeSeconds = 0.2)

        assertEquals(3, verifier.consecutiveFrames)
        assertTrue(verifier.isConfirmed)
    }

    @Test
    fun shouldOfferFallbackOnlyAfterFallbackAfterSecondsWithoutConfirming() {
        val verifier = ToothbrushVerifier(requiredFrames = 3, minScore = 0.5f, fallbackAfterSeconds = 60.0)

        // Before any feed
        assertFalse(verifier.shouldOfferFallback(timeSeconds = 0.0))

        // First feed at t = 10.0
        verifier.feed(emptyList(), timeSeconds = 10.0)

        // At t = 69.9 (59.9s passed, < 60s)
        assertFalse(verifier.shouldOfferFallback(timeSeconds = 69.9))

        // At t = 70.0 (60.0s passed, >= 60s)
        assertTrue(verifier.shouldOfferFallback(timeSeconds = 70.0))

        // At t = 80.0
        assertTrue(verifier.shouldOfferFallback(timeSeconds = 80.0))
    }

    @Test
    fun shouldOfferFallbackIsFalseWhenAlreadyConfirmed() {
        val verifier = ToothbrushVerifier(requiredFrames = 2, minScore = 0.5f, fallbackAfterSeconds = 10.0)

        verifier.feed(listOf(Detection("toothbrush", 0.8f)), timeSeconds = 0.0)
        verifier.feed(listOf(Detection("toothbrush", 0.8f)), timeSeconds = 1.0)
        assertTrue(verifier.isConfirmed)

        // Even after fallbackAfterSeconds, fallback is not offered if already confirmed
        assertFalse(verifier.shouldOfferFallback(timeSeconds = 100.0))
    }

    @Test
    fun onceConfirmedRemainsConfirmed() {
        val verifier = ToothbrushVerifier(requiredFrames = 2, minScore = 0.5f)

        verifier.feed(listOf(Detection("toothbrush", 0.9f)), timeSeconds = 0.0)
        verifier.feed(listOf(Detection("toothbrush", 0.9f)), timeSeconds = 0.1)
        assertTrue(verifier.isConfirmed)

        // Feeding empty frame or wrong label after confirmation does not unconfirm
        verifier.feed(emptyList(), timeSeconds = 0.2)
        assertTrue(verifier.isConfirmed)

        verifier.feed(listOf(Detection("other", 0.1f)), timeSeconds = 0.3)
        assertTrue(verifier.isConfirmed)
    }

    @Test
    fun frameWithMultipleDetectionsCountsIfAnyMatches() {
        val verifier = ToothbrushVerifier(requiredFrames = 1, minScore = 0.5f)

        val detections = listOf(
            Detection("person", 0.95f),
            Detection("toothbrush", 0.75f),
            Detection("bottle", 0.6f)
        )

        verifier.feed(detections, timeSeconds = 0.0)
        assertTrue(verifier.isConfirmed)
    }
}
