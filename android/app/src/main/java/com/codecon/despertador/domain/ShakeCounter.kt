package com.codecon.despertador.domain

class ShakeCounter(
    val target: Long,
    val threshold: Double = 2.5,
    val minIntervalSeconds: Double = 0.3
) {
    private var count: Long = 0L
    private var lastShakeTime: Double? = null

    val remaining: Long
        get() = maxOf(0L, target - count)

    val isDone: Boolean
        get() = remaining == 0L

    fun feed(magnitude: Double, timeSeconds: Double) {
        if (magnitude < threshold) {
            return
        }

        val lastTime = lastShakeTime
        if (lastTime == null || (timeSeconds - lastTime) >= minIntervalSeconds) {
            count++
            lastShakeTime = timeSeconds
        }
    }
}
