package com.codecon.despertador.alarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class NextRingTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun nextRingMillis_whenTargetIsLaterToday_returnsTodayWithZeroSeconds() {
        // Now: 06:15:30.500 UTC on 2026-11-17
        val now = ZonedDateTime.of(2026, 11, 17, 6, 15, 30, 500_000_000, zone)
        val nowMillis = now.toInstant().toEpochMilli()

        val ringMillis = nextRingMillis(hour = 7, minute = 30, nowMillis = nowMillis, zone = zone)
        val ringZdt = Instant.ofEpochMilli(ringMillis).atZone(zone)

        val expected = ZonedDateTime.of(2026, 11, 17, 7, 30, 0, 0, zone)
        assertEquals(expected.toInstant().toEpochMilli(), ringMillis)
        assertEquals(0, ringZdt.second)
        assertEquals(0, ringZdt.nano)
    }

    @Test
    fun nextRingMillis_whenTargetAlreadyPassedToday_returnsTomorrowWithZeroSeconds() {
        // Now: 08:00:00 UTC on 2026-11-17
        val now = ZonedDateTime.of(2026, 11, 17, 8, 0, 0, 0, zone)
        val nowMillis = now.toInstant().toEpochMilli()

        val ringMillis = nextRingMillis(hour = 7, minute = 30, nowMillis = nowMillis, zone = zone)
        val ringZdt = Instant.ofEpochMilli(ringMillis).atZone(zone)

        val expected = ZonedDateTime.of(2026, 11, 18, 7, 30, 0, 0, zone)
        assertEquals(expected.toInstant().toEpochMilli(), ringMillis)
        assertEquals(0, ringZdt.second)
        assertEquals(0, ringZdt.nano)
    }

    @Test
    fun nextRingMillis_whenTargetIsExactlyCurrentTime_returnsTomorrow() {
        // Now: exactly 07:30:00.000 UTC on 2026-11-17
        val now = ZonedDateTime.of(2026, 11, 17, 7, 30, 0, 0, zone)
        val nowMillis = now.toInstant().toEpochMilli()

        val ringMillis = nextRingMillis(hour = 7, minute = 30, nowMillis = nowMillis, zone = zone)
        val ringZdt = Instant.ofEpochMilli(ringMillis).atZone(zone)

        val expected = ZonedDateTime.of(2026, 11, 18, 7, 30, 0, 0, zone)
        assertEquals(expected.toInstant().toEpochMilli(), ringMillis)
        assertEquals(0, ringZdt.second)
        assertEquals(0, ringZdt.nano)
    }
}
