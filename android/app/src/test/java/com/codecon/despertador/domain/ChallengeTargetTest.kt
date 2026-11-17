package com.codecon.despertador.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ChallengeTargetTest {

    @Test
    fun `shakes returns exact seconds for round milliseconds`() {
        val result = ChallengeTarget.shakes(1_800_000_000_000L)
        assertEquals(1_800_000_000L, result)
    }

    @Test
    fun `shakes truncates milliseconds without mod`() {
        val resultWithSubseconds = ChallengeTarget.shakes(1_800_000_000_999L)
        assertEquals(1_800_000_000L, resultWithSubseconds)

        val resultBeginningOfSecond = ChallengeTarget.shakes(1_800_000_000_001L)
        assertEquals(1_800_000_000L, resultBeginningOfSecond)
    }

    @Test
    fun `shakes returns positive number for dates between 2026 and 2100 at both ends`() {
        val startOf2026 = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        val shakes2026 = ChallengeTarget.shakes(startOf2026)
        assertTrue("Shakes at 2026 start should be positive", shakes2026 > 0)
        assertEquals(startOf2026 / 1000L, shakes2026)

        val endOf2100 = Instant.parse("2100-12-31T23:59:59.999Z").toEpochMilli()
        val shakes2100 = ChallengeTarget.shakes(endOf2100)
        assertTrue("Shakes at 2100 end should be positive", shakes2100 > 0)
        assertEquals(endOf2100 / 1000L, shakes2100)
    }
}
