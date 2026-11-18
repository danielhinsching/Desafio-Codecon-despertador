package com.codecon.despertador.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.codecon.despertador.data.PixApi
import com.codecon.despertador.data.PixApiException
import com.codecon.despertador.data.PixCharge
import com.codecon.despertador.data.PixStatus
import com.codecon.despertador.storage.PendingPixStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

sealed interface PixState {
    data object Idle : PixState
    data object Creating : PixState
    /** `notice` é um aviso passageiro (ex.: sem internet); o alarme segue tocando e a consulta continua. */
    data class Waiting(val charge: PixCharge, val notice: String? = null) : PixState
    data object Expired : PixState
    data class Failed(val message: String) : PixState
    data object Released : PixState
}

/**
 * Fluxo do pagamento Pix. O app nunca decide sozinho que foi pago: só libera quando o servidor
 * responde `released: true` no consume.
 */
class PixPaymentViewModel(
    private val api: PixApi,
    private val store: PendingPixStore,
    private val alarmIdProvider: () -> String?,
    private val onReleased: () -> Unit,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val pollIntervalMillis: Long = 3_000L
) : ViewModel() {

    var state: PixState by mutableStateOf(PixState.Idle)
        private set

    var nowMillis: Long by mutableLongStateOf(clock())
        private set

    private val deviceId: String by lazy { store.deviceId() }

    /** Segundos até `expiresAt` (0 se já passou). */
    fun remainingSeconds(charge: PixCharge): Long =
        ((charge.expiresAtMillis - nowMillis) / 1000L).coerceAtLeast(0L)

    /** Chamado a cada segundo pela UI: atualiza a contagem e expira localmente. */
    fun tick() {
        nowMillis = clock()
        val s = state
        if (s is PixState.Waiting && nowMillis >= s.charge.expiresAtMillis) expire()
    }

    /** Ao reabrir o app: retoma a consulta do mesmo paymentId, se houver. */
    fun resumeIfPending() {
        if (state != PixState.Idle) return
        val charge = store.load() ?: return
        nowMillis = clock()
        if (nowMillis >= charge.expiresAtMillis) {
            store.clear()
            state = PixState.Expired
        } else {
            state = PixState.Waiting(charge)
        }
    }

    suspend fun startPayment() {
        if (state is PixState.Creating || state is PixState.Waiting) return
        val alarmId = alarmIdProvider()
        if (alarmId == null) {
            state = PixState.Failed("Não foi possível identificar o alarme.")
            return
        }
        state = PixState.Creating
        try {
            val charge = api.createPayment(alarmId, deviceId)
            store.save(charge)
            nowMillis = clock()
            state = PixState.Waiting(charge)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PixApiException) {
            state = PixState.Failed(messageFor(e, "gerar o Pix"))
        }
    }

    /** Uma consulta de status; ao `approved`, chama consume. Seguro de chamar em qualquer estado. */
    suspend fun poll() {
        val s = state as? PixState.Waiting ?: return
        val charge = s.charge
        try {
            val res = api.getStatus(charge.paymentId)
            when (res.status) {
                PixStatus.PENDING -> {
                    nowMillis = clock()
                    if (nowMillis >= res.expiresAtMillis) expire() else state = PixState.Waiting(charge)
                }
                PixStatus.EXPIRED -> expire()
                PixStatus.APPROVED -> consume(charge)
                PixStatus.USED -> {
                    store.clear()
                    state = PixState.Failed("Este pagamento já foi usado. O alarme continua tocando.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: PixApiException) {
            if (e.kind == PixApiException.Kind.NOT_FOUND) {
                expire()
            } else {
                state = PixState.Waiting(charge, messageFor(e, "consultar o pagamento"))
            }
        }
    }

    private suspend fun consume(charge: PixCharge) {
        val alarmId = alarmIdProvider()
        if (alarmId == null) {
            state = PixState.Waiting(charge, "Não foi possível identificar o alarme.")
            return
        }
        val result = try {
            api.consume(charge.paymentId, alarmId, deviceId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PixApiException) {
            state = PixState.Waiting(charge, messageFor(e, "liberar o alarme"))
            return
        }
        if (result.released) {
            store.clear()
            state = PixState.Released
            onReleased()
        } else if (result.reason == "not_approved") {
            // Ainda não aprovado de fato: continua consultando.
            state = PixState.Waiting(charge)
        } else {
            store.clear()
            state = PixState.Failed("O servidor não liberou este alarme. O alarme continua tocando.")
        }
    }

    /** Consulta a cada 3 s enquanto houver pagamento aguardando. Cancelada com a tela. */
    suspend fun runPolling() {
        while (true) {
            if (state is PixState.Waiting) poll()
            delay(pollIntervalMillis)
        }
    }

    fun dismiss() {
        if (state is PixState.Expired || state is PixState.Failed) state = PixState.Idle
    }

    private fun expire() {
        store.clear()
        state = PixState.Expired
    }

    private fun messageFor(e: PixApiException, action: String): String = when (e.kind) {
        PixApiException.Kind.NETWORK -> "Sem conexão ao $action. O alarme continua tocando."
        PixApiException.Kind.NOT_CONFIGURED -> "Pix indisponível neste aplicativo (servidor não configurado)."
        else -> "Erro do servidor ao $action. O alarme continua tocando."
    }
}
