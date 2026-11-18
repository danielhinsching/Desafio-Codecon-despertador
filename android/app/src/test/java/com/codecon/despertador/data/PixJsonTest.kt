package com.codecon.despertador.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixJsonTest {

    @Test
    fun parseCharge_readsFiveFields() {
        val c = PixJson.parseCharge(
            """{"paymentId":"123","qrCode":"abc","qrCodeBase64":"b64","expiresAt":"2030-01-01T12:30:00.000Z","amountCents":990}"""
        )
        assertEquals("123", c.paymentId)
        assertEquals("abc", c.qrCode)
        assertEquals("b64", c.qrCodeBase64)
        assertEquals(990, c.amountCents)
        assertEquals(1_893_501_000_000L, c.expiresAtMillis)
    }

    @Test
    fun parseStatus_acceptsLowercaseStatuses() {
        val s = PixJson.parseStatus("""{"status":"approved","expiresAt":"2030-01-01T12:30:00.000Z"}""")
        assertEquals(PixStatus.APPROVED, s.status)
    }

    @Test
    fun parseConsume_releasedAndReason() {
        assertTrue(PixJson.parseConsume("""{"released":true}""").released)
        val no = PixJson.parseConsume("""{"released":false,"reason":"mismatch"}""")
        assertFalse(no.released)
        assertEquals("mismatch", no.reason)
        assertNull(PixJson.parseConsume("""{"released":true}""").reason)
    }

    @Test
    fun createBody_hasAlarmAndDeviceOnly() {
        val body = PixJson.createBody("a1", "d1")
        assertTrue(body.contains("\"alarmId\":\"a1\""))
        assertTrue(body.contains("\"deviceId\":\"d1\""))
        assertFalse(body.contains("amount"))
    }
}
