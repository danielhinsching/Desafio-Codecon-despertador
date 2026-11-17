package com.codecon.despertador.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeRampTest {

    private val start = 0.1f
    private val end = 1.0f
    private val duration = 60.0

    private val ramp = VolumeRamp(start = start, end = end, durationSeconds = duration)

    @Test
    fun elapsedZero_returnsStart() {
        assertEquals(start, ramp.level(0.0), 0.0001f)
    }

    @Test
    fun elapsedDuration_returnsEnd() {
        assertEquals(end, ramp.level(duration), 0.0001f)
    }

    @Test
    fun elapsedGreaterThanDuration_returnsEnd() {
        assertEquals(end, ramp.level(75.0), 0.0001f)
        assertEquals(end, ramp.level(120.0), 0.0001f)
    }

    @Test
    fun elapsedLessThanZero_returnsStart() {
        assertEquals(start, ramp.level(-1.0), 0.0001f)
        assertEquals(start, ramp.level(-50.0), 0.0001f)
    }

    @Test
    fun halfWay_returnsAverage() {
        val expectedAverage = (start + end) / 2f
        assertEquals(expectedAverage, ramp.level(30.0), 0.0001f)
    }

    @Test
    fun intermediateValues_interpolateLinearly() {
        val quarter = start + (end - start) * 0.25f
        val threeQuarters = start + (end - start) * 0.75f
        assertEquals(quarter, ramp.level(15.0), 0.0001f)
        assertEquals(threeQuarters, ramp.level(45.0), 0.0001f)
    }
}
