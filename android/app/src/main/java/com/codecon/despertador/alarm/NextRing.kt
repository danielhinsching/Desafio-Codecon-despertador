package com.codecon.despertador.alarm

import java.time.Instant
import java.time.ZoneId

fun nextRingMillis(
    hour: Int,
    minute: Int,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): Long {
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val todayCandidate = now.withHour(hour)
        .withMinute(minute)
        .withSecond(0)
        .withNano(0)

    val candidateMillis = todayCandidate.toInstant().toEpochMilli()
    return if (candidateMillis > nowMillis) {
        candidateMillis
    } else {
        todayCandidate.plusDays(1).toInstant().toEpochMilli()
    }
}
