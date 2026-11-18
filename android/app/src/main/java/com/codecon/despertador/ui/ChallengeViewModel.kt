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

/**
 * Desafio de sacudir. Só LÊ o `PendingChallengeStore`: quem grava o desafio pendente é o
 * `AlarmReceiver`, quando o alarme toca. Criar este view model nunca escreve no store.
 */
class ChallengeViewModel(
    initialTarget: Long = 0L,
    private val store: PendingChallengeStore? = null,
    private val sensorSource: SensorSource? = null,
    private val threshold: Double = 2.5,
    private val minIntervalSeconds: Double = 0.3,
    /** Chamado uma vez quando o desafio de sacudir termina (ex.: parar o som do alarme). */
    private val onDone: (() -> Unit)? = null
) : ViewModel() {

    private lateinit var counter: ShakeCounter

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
        applyTarget(target)
    }

    private fun applyTarget(target: Long) {
        counter = ShakeCounter(
            target = target,
            threshold = threshold,
            minIntervalSeconds = minIntervalSeconds
        )
        remaining = counter.remaining
        isDone = counter.isDone
    }

    /** Alarme tocou depois que o app abriu: passa a usar o alvo que o receiver gravou. */
    fun reloadFromStore() {
        val pending = store?.load() ?: return
        if (pending.target <= 0L) return
        stopListening()
        applyTarget(pending.target)
    }

    /** "Testar desafio": novo desafio com alvo de agora, sem gravar pendente. */
    fun startTest() {
        stopListening()
        applyTarget(ChallengeTarget.shakes(System.currentTimeMillis()))
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
            onDone?.invoke()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
