package com.codecon.despertador.sensor

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorSourceTest {

    private val delta = 1e-6

    @Test
    fun calculateMagnitudeInG_zeroAcceleration() {
        val magnitudeInG = SensorSource.calculateMagnitudeInG(0.0f, 0.0f, 0.0f)
        assertEquals(0.0, magnitudeInG, delta)
    }

    @Test
    fun calculateMagnitudeInG_singleAxisEarthGravity() {
        // 9.81 m/s² on Z-axis (device resting on table) should equal exactly 1.0 g
        val magnitudeInGZ = SensorSource.calculateMagnitudeInG(0.0f, 0.0f, 9.81f)
        assertEquals(1.0, magnitudeInGZ, delta)

        // 9.81 m/s² on X-axis
        val magnitudeInGX = SensorSource.calculateMagnitudeInG(9.81f, 0.0f, 0.0f)
        assertEquals(1.0, magnitudeInGX, delta)

        // Negative direction: magnitude is always non-negative
        val magnitudeInGY = SensorSource.calculateMagnitudeInG(0.0f, -9.81f, 0.0f)
        assertEquals(1.0, magnitudeInGY, delta)
    }

    @Test
    fun calculateMagnitudeInG_multipleAxesVector() {
        // Vector (3, 4, 0) * 9.81 has length 5 * 9.81, so in g it should be exactly 5.0 g
        val x = (3.0 * 9.81).toFloat()
        val y = (4.0 * 9.81).toFloat()
        val z = 0.0f

        val magnitudeInG = SensorSource.calculateMagnitudeInG(x, y, z)
        assertEquals(5.0, magnitudeInG, 1e-4)
    }

    @Test
    fun calculateMagnitudeInG_doubleOverload() {
        val magnitudeInG = SensorSource.calculateMagnitudeInG(0.0, 9.81, 0.0)
        assertEquals(1.0, magnitudeInG, delta)
    }

    @Test
    fun timestampToSeconds_convertsNanosAccurately() {
        assertEquals(0.0, SensorSource.timestampToSeconds(0L), delta)
        assertEquals(1.0, SensorSource.timestampToSeconds(1_000_000_000L), delta)
        assertEquals(1.5, SensorSource.timestampToSeconds(1_500_000_000L), delta)
        assertEquals(2.75, SensorSource.timestampToSeconds(2_750_000_000L), delta)
    }

    @Test
    fun startAndStopWithoutSensorManager_doesNotThrow() {
        val source = SensorSource(sensorManager = null)

        var samplesCount = 0
        source.start { _, _ ->
            samplesCount++
        }

        // Repeated call to start should also be safe and not throw
        source.start { _, _ ->
            samplesCount++
        }

        source.stop()

        assertEquals(0, samplesCount)
    }
}
