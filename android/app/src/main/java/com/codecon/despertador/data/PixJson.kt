package com.codecon.despertador.data

import org.json.JSONObject

/** Conversão JSON <-> modelos do contrato. Separada do HTTP para poder ser testada. */
object PixJson {
    fun createBody(alarmId: String, deviceId: String): String =
        JSONObject().put("alarmId", alarmId).put("deviceId", deviceId).toString()

    fun parseCharge(json: String): PixCharge {
        val o = JSONObject(json)
        return PixCharge(
            paymentId = o.getString("paymentId"),
            qrCode = o.getString("qrCode"),
            qrCodeBase64 = o.getString("qrCodeBase64"),
            expiresAtMillis = parseIsoMillis(o.getString("expiresAt")),
            amountCents = o.getInt("amountCents")
        )
    }

    fun parseStatus(json: String): PixStatusResponse {
        val o = JSONObject(json)
        val status = PixStatus.valueOf(o.getString("status").uppercase())
        return PixStatusResponse(status, parseIsoMillis(o.getString("expiresAt")))
    }

    fun parseConsume(json: String): ConsumeResult {
        val o = JSONObject(json)
        return ConsumeResult(
            released = o.optBoolean("released", false),
            reason = if (o.has("reason") && !o.isNull("reason")) o.getString("reason") else null
        )
    }
}
