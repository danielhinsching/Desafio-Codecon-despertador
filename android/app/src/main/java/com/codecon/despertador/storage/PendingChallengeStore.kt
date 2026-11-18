package com.codecon.despertador.storage

import android.content.SharedPreferences

data class Pending(
    val target: Long,
    val ringTimeMillis: Long
)

class PendingChallengeStore(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_PENDING_TARGET = "pendingTarget"
        const val KEY_PENDING_RING_TIME = "pendingRingTime"
    }

    fun save(target: Long, ringTimeMillis: Long) {
        prefs.edit()
            .putLong(KEY_PENDING_TARGET, target)
            .putLong(KEY_PENDING_RING_TIME, ringTimeMillis)
            .apply()
    }

    fun load(): Pending? {
        if (!prefs.contains(KEY_PENDING_TARGET) || !prefs.contains(KEY_PENDING_RING_TIME)) {
            return null
        }
        val target = prefs.getLong(KEY_PENDING_TARGET, 0L)
        val ringTime = prefs.getLong(KEY_PENDING_RING_TIME, 0L)
        return Pending(target = target, ringTimeMillis = ringTime)
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_PENDING_TARGET)
            .remove(KEY_PENDING_RING_TIME)
            .apply()
    }
}

typealias PersistentChallengeStore = PendingChallengeStore
