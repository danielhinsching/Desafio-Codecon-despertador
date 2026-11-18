package com.codecon.despertador.toothbrush

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

sealed interface ToothbrushStatus {
    /** Câmera ativa, ainda sem escova. */
    data object Searching : ToothbrushStatus

    /** Escova vista em `frames` de `required` quadros seguidos. */
    data class Progress(val frames: Int, val required: Int) : ToothbrushStatus

    data object Confirmed : ToothbrushStatus

    /** Câmera ou IA indisponível: o desafio de sacudir continua sendo a saída. */
    data class Unavailable(val message: String) : ToothbrushStatus
}

/**
 * Estado do desafio da escova. Sem dependência de câmera: recebe quadros já como Bitmap, repassa ao
 * detector e ao verificador. `completion.onCompleted()` é chamado uma única vez, na confirmação.
 */
class ToothbrushViewModel(
    private val detector: ObjectDetecting?,
    private val completion: ChallengeCompletion,
    private val clockSeconds: () -> Double,
    private val verifier: ToothbrushVerifier = ToothbrushVerifier(),
    private val minFrameIntervalMillis: Long = 250L
) : ViewModel() {

    var status: ToothbrushStatus by mutableStateOf(ToothbrushStatus.Searching)
        private set

    /** Verdadeiro quando o app deve oferecer "Sacudir em vez disso". */
    var canUseShake: Boolean by mutableStateOf(false)
        private set

    private var lastAcceptedMillis: Long? = null
    private var completed = false

    init {
        if (detector == null) {
            markUnavailable("Não foi possível carregar o detector de escova. Use o desafio de sacudir.")
        }
    }

    fun onCameraPermissionDenied() =
        markUnavailable("Sem permissão da câmera. Use o desafio de sacudir.")

    fun onNoCamera() = markUnavailable("Este aparelho não tem câmera. Use o desafio de sacudir.")

    fun onCameraError() = markUnavailable("A câmera falhou. Use o desafio de sacudir.")

    /** Limita a taxa de análise (padrão ~4 quadros por segundo). */
    @Synchronized
    fun acceptFrame(nowMillis: Long): Boolean {
        val last = lastAcceptedMillis
        if (last != null && nowMillis - last < minFrameIntervalMillis) return false
        lastAcceptedMillis = nowMillis
        return true
    }

    /** Roda o detector no quadro e alimenta o verificador. Chamar fora da thread principal. */
    @Synchronized
    fun processFrame(frame: Bitmap) {
        val d = detector ?: return
        if (completed || status is ToothbrushStatus.Unavailable) return

        val detections = try {
            d.detect(frame)
        } catch (e: DetectionFailedException) {
            markUnavailable("A detecção falhou. Use o desafio de sacudir.")
            return
        }

        val now = clockSeconds()
        verifier.feed(detections, now)

        if (verifier.isConfirmed) {
            status = ToothbrushStatus.Confirmed
            canUseShake = false
            completed = true
            completion.onCompleted()
            return
        }

        status = if (verifier.consecutiveFrames > 0) {
            ToothbrushStatus.Progress(verifier.consecutiveFrames, verifier.requiredFrames)
        } else {
            ToothbrushStatus.Searching
        }
        updateFallback(now)
    }

    /** Chamado periodicamente pela UI para o tempo de fallback andar mesmo sem quadros novos. */
    @Synchronized
    fun tick() {
        if (!completed) updateFallback(clockSeconds())
    }

    private fun updateFallback(now: Double) {
        if (verifier.shouldOfferFallback(now)) canUseShake = true
    }

    private fun markUnavailable(message: String) {
        status = ToothbrushStatus.Unavailable(message)
        canUseShake = true
    }
}
