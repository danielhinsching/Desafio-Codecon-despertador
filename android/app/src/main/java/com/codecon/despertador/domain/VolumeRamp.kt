package com.codecon.despertador.domain

class VolumeRamp(
    val start: Float,
    val end: Float,
    val durationSeconds: Double
) {
    fun level(elapsedSeconds: Double): Float {
        if (elapsedSeconds <= 0.0) {
            return start
        }
        if (durationSeconds <= 0.0 || elapsedSeconds >= durationSeconds) {
            return end
        }
        val fraction = elapsedSeconds / durationSeconds
        return (start + (end - start) * fraction).toFloat()
    }
}
