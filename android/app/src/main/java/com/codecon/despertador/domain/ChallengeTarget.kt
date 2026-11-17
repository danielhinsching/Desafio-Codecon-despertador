package com.codecon.despertador.domain

object ChallengeTarget {
    fun shakes(ringTimeMillis: Long): Long {
        return ringTimeMillis / 1000L
    }
}
