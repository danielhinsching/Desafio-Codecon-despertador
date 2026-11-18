package com.codecon.despertador.storage

import android.content.SharedPreferences
import com.codecon.despertador.data.PixCharge
import java.util.UUID

/** Guarda a cobrança Pix em andamento para retomar a consulta ao reabrir o app. */
class PendingPixStore(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_PAYMENT_ID = "pixPaymentId"
        const val KEY_QR_CODE = "pixQrCode"
        const val KEY_QR_BASE64 = "pixQrBase64"
        const val KEY_EXPIRES_AT = "pixExpiresAt"
        const val KEY_AMOUNT = "pixAmountCents"
        const val KEY_DEVICE_ID = "deviceId"
    }

    fun save(charge: PixCharge) {
        prefs.edit()
            .putString(KEY_PAYMENT_ID, charge.paymentId)
            .putString(KEY_QR_CODE, charge.qrCode)
            .putString(KEY_QR_BASE64, charge.qrCodeBase64)
            .putLong(KEY_EXPIRES_AT, charge.expiresAtMillis)
            .putInt(KEY_AMOUNT, charge.amountCents)
            .apply()
    }

    fun load(): PixCharge? {
        val id = prefs.getString(KEY_PAYMENT_ID, null) ?: return null
        val qr = prefs.getString(KEY_QR_CODE, null) ?: return null
        val b64 = prefs.getString(KEY_QR_BASE64, null) ?: return null
        if (!prefs.contains(KEY_EXPIRES_AT)) return null
        return PixCharge(
            paymentId = id,
            qrCode = qr,
            qrCodeBase64 = b64,
            expiresAtMillis = prefs.getLong(KEY_EXPIRES_AT, 0L),
            amountCents = prefs.getInt(KEY_AMOUNT, 990)
        )
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_PAYMENT_ID)
            .remove(KEY_QR_CODE)
            .remove(KEY_QR_BASE64)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_AMOUNT)
            .apply()
    }

    /** Id estável do app no aparelho, gerado uma vez. */
    fun deviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }
}
