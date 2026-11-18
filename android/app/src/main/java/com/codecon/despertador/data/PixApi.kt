package com.codecon.despertador.data

import java.time.Instant

data class PixCharge(
    val paymentId: String,
    val qrCode: String,
    val qrCodeBase64: String,
    val expiresAtMillis: Long,
    val amountCents: Int
)

enum class PixStatus { PENDING, APPROVED, EXPIRED, USED }

data class PixStatusResponse(val status: PixStatus, val expiresAtMillis: Long)

data class ConsumeResult(val released: Boolean, val reason: String? = null)

/** Falhas ao falar com o servidor. Nunca carrega corpo de resposta nem URL. */
class PixApiException(val kind: Kind) : Exception(kind.name) {
    enum class Kind { NETWORK, SERVER, NOT_FOUND, NOT_CONFIGURED }
}

/** Contrato do servidor Pix (issues #31 e #33). Os testes usam uma implementação falsa. */
interface PixApi {
    suspend fun createPayment(alarmId: String, deviceId: String): PixCharge
    suspend fun getStatus(paymentId: String): PixStatusResponse
    suspend fun consume(paymentId: String, alarmId: String, deviceId: String): ConsumeResult
}

fun parseIsoMillis(iso: String): Long = Instant.parse(iso).toEpochMilli()
