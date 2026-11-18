package com.codecon.despertador.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Cliente HTTP real. `baseUrl` vem do BuildConfig; vazio significa "não configurado". */
class HttpPixApi(private val baseUrl: String) : PixApi {

    private class Response(val code: Int, val body: String)

    private suspend fun request(method: String, path: String, body: String?): Response {
        if (baseUrl.isBlank()) throw PixApiException(PixApiException.Kind.NOT_CONFIGURED)
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                Response(code, text)
            } catch (e: IOException) {
                throw PixApiException(PixApiException.Kind.NETWORK)
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun <T> parse(block: () -> T): T =
        try {
            block()
        } catch (e: Exception) {
            throw PixApiException(PixApiException.Kind.SERVER)
        }

    override suspend fun createPayment(alarmId: String, deviceId: String): PixCharge {
        val r = request("POST", "/api/payments", PixJson.createBody(alarmId, deviceId))
        if (r.code != 200 && r.code != 201) throw PixApiException(PixApiException.Kind.SERVER)
        return parse { PixJson.parseCharge(r.body) }
    }

    override suspend fun getStatus(paymentId: String): PixStatusResponse {
        val r = request("GET", "/api/payments/$paymentId", null)
        if (r.code == 404) throw PixApiException(PixApiException.Kind.NOT_FOUND)
        if (r.code != 200) throw PixApiException(PixApiException.Kind.SERVER)
        return parse { PixJson.parseStatus(r.body) }
    }

    override suspend fun consume(paymentId: String, alarmId: String, deviceId: String): ConsumeResult {
        val r = request("POST", "/api/payments/$paymentId/consume", PixJson.createBody(alarmId, deviceId))
        if (r.code == 404) throw PixApiException(PixApiException.Kind.NOT_FOUND)
        if (r.code != 200 && r.code != 409) throw PixApiException(PixApiException.Kind.SERVER)
        return parse { PixJson.parseConsume(r.body) }
    }
}
