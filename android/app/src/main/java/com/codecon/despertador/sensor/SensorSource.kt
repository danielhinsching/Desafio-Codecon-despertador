package com.codecon.despertador.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class SensorSource(
    private val sensorManager: SensorManager?
) {
    constructor(context: Context) : this(
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    )

    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var listener: SensorEventListener? = null
    private var isStarted = false

    fun start(onSample: (Double, Double) -> Unit) {
        if (isStarted) {
            return
        }

        if (sensorManager == null || accelerometer == null) {
            return
        }

        val eventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitudeInG = calculateMagnitudeInG(x, y, z)
                val timeSeconds = timestampToSeconds(event.timestamp)
                onSample(magnitudeInG, timeSeconds)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Not needed for acceleration samples
            }
        }

        val registered = sensorManager.registerListener(
            eventListener,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME
        )

        if (registered) {
            listener = eventListener
            isStarted = true
        }
    }

    fun stop() {
        if (!isStarted) {
            return
        }

        listener?.let {
            sensorManager?.unregisterListener(it)
        }
        listener = null
        isStarted = false
    }

    companion object {
        const val STANDARD_GRAVITY = 9.81

        fun calculateMagnitudeInG(x: Float, y: Float, z: Float, gravity: Double = STANDARD_GRAVITY): Double {
            val magnitude = sqrt((x * x + y * y + z * z).toDouble())
            return magnitude / gravity
        }

        fun calculateMagnitudeInG(x: Double, y: Double, z: Double, gravity: Double = STANDARD_GRAVITY): Double {
            val magnitude = sqrt(x * x + y * y + z * z)
            return magnitude / gravity
        }

        fun timestampToSeconds(timestampNanos: Long): Double {
            return timestampNanos.toDouble() / 1_000_000_000.0
        }
    }
}
