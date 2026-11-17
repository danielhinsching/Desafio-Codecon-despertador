package com.codecon.despertador.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeCounterTest {

    @Test
    fun sampleBelowThresholdDoesNotIncrement() {
        val counter = ShakeCounter(target = 5L, threshold = 2.5, minIntervalSeconds = 0.3)

        counter.feed(magnitude = 1.0, timeSeconds = 0.0)
        counter.feed(magnitude = 2.49, timeSeconds = 0.5)

        assertEquals(5L, counter.remaining)
        assertFalse(counter.isDone)
    }

    @Test
    fun twoSamplesAboveThresholdWithinMinIntervalCountAsOne() {
        val counter = ShakeCounter(target = 5L, threshold = 2.5, minIntervalSeconds = 0.3)

        counter.feed(magnitude = 3.0, timeSeconds = 1.0)
        counter.feed(magnitude = 4.0, timeSeconds = 1.1)
        counter.feed(magnitude = 3.5, timeSeconds = 1.25)

        assertEquals(4L, counter.remaining)
        assertFalse(counter.isDone)
    }

    @Test
    fun twoSamplesAboveThresholdSeparatedByMoreThanMinIntervalCountTwice() {
        val counter = ShakeCounter(target = 5L, threshold = 2.5, minIntervalSeconds = 0.3)

        counter.feed(magnitude = 3.0, timeSeconds = 1.0)
        counter.feed(magnitude = 3.2, timeSeconds = 1.35)

        assertEquals(3L, counter.remaining)
        assertFalse(counter.isDone)
    }

    @Test
    fun remainingNeverBelowZeroAndIsDoneWhenRemainingReachesZero() {
        val counter = ShakeCounter(target = 2L, threshold = 2.5, minIntervalSeconds = 0.3)

        assertFalse(counter.isDone)
        assertEquals(2L, counter.remaining)

        counter.feed(magnitude = 3.0, timeSeconds = 0.0)
        assertEquals(1L, counter.remaining)
        assertFalse(counter.isDone)

        counter.feed(magnitude = 3.0, timeSeconds = 0.4)
        assertEquals(0L, counter.remaining)
        assertTrue(counter.isDone)

        // Extra shake beyond target: remaining should stay at 0, never negative
        counter.feed(magnitude = 3.0, timeSeconds = 0.8)
        assertEquals(0L, counter.remaining)
        assertTrue(counter.isDone)
    }

    @Test
    fun worksWithLargeTargetAround1Point8Billion() {
        val target = 1_800_000_000L
        val counter = ShakeCounter(target = target, threshold = 2.5, minIntervalSeconds = 0.3)

        assertEquals(target, counter.remaining)
        assertFalse(counter.isDone)

        counter.feed(magnitude = 3.0, timeSeconds = 100.0)
        assertEquals(target - 1L, counter.remaining)
        assertFalse(counter.isDone)
    }

    @Test
    fun sampleExactlyAtThresholdCounts() {
        val counter = ShakeCounter(target = 1L, threshold = 2.5, minIntervalSeconds = 0.3)

        counter.feed(magnitude = 2.5, timeSeconds = 0.0)

        assertEquals(0L, counter.remaining)
        assertTrue(counter.isDone)
    }

    @Test
    fun zeroOrNegativeTargetStartsAsDone() {
        val zeroCounter = ShakeCounter(target = 0L, threshold = 2.5, minIntervalSeconds = 0.3)
        assertEquals(0L, zeroCounter.remaining)
        assertTrue(zeroCounter.isDone)

        val negativeCounter = ShakeCounter(target = -10L, threshold = 2.5, minIntervalSeconds = 0.3)
        assertEquals(0L, negativeCounter.remaining)
        assertTrue(negativeCounter.isDone)
    }
}
