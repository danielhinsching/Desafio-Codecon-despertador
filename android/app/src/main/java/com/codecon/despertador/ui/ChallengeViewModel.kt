package com.codecon.despertador.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.codecon.despertador.domain.ChallengeTarget
import com.codecon.despertador.domain.ShakeCounter
import com.codecon.despertador.sensor.SensorSource
import com.codecon.despertador.storage.PendingChallengeStore

class ChallengeViewModel(
    initialTarget: Long = 0L,
    private val store: PendingChallengeStore? = null,
    private val sensorSource: SensorSource? = null,
    threshold: Double = 2.5,
    minIntervalSeconds: Double = 0.3
) : ViewModel() {

    private val counter: ShakeCounter

    var remaining: Long by mutableLongStateOf(0L)
        private set

    var isDone: Boolean by mutableStateOf(false)
        private set

    var isRunning: Boolean by mutableStateOf(false)
        private set

    init {
        val pending = store?.load()
        val target = when {
            pending != null && pending.target > 0L -> pending.target
            initialTarget > 0L -> initialTarget
            else -> ChallengeTarget.shakes(System.currentTimeMillis())
        }

        counter = ShakeCounter(
            target = target,
            threshold = threshold,
            minIntervalSeconds = minIntervalSeconds
        )
        remaining = counter.remaining
        isDone = counter.isDone

        if (!isDone && pending == null && store != null && target > 0L) {
            store.save(target, System.currentTimeMillis())
        }
    }

    fun startListening() {
        if (isRunning || isDone) return
        isRunning = true
        sensorSource?.start { magnitude, timeSeconds ->
            onSample(magnitude, timeSeconds)
        }
    }

    fun stopListening() {
        sensorSource?.stop()
        isRunning = false
    }

    fun onSample(magnitude: Double, timeSeconds: Double) {
        if (isDone) return

        counter.feed(magnitude, timeSeconds)
        remaining = counter.remaining
        isDone = counter.isDone

        if (isDone) {
            stopListening()
            store?.clear()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
