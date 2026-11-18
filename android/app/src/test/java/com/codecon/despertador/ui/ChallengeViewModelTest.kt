package com.codecon.despertador.ui

import android.content.SharedPreferences
import com.codecon.despertador.storage.PendingChallengeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeViewModelTest {

    @Test
    fun threeValidPeaksSeparatedByHalfSecond_reachesDoneAndZeroRemaining() {
        val vm = ChallengeViewModel(initialTarget = 3L, threshold = 2.5, minIntervalSeconds = 0.3)

        assertEquals(3L, vm.remaining)
        assertFalse(vm.isDone)

        // Peak 1 at t = 0.0 s
        vm.onSample(magnitude = 3.0, timeSeconds = 0.0)
        assertEquals(2L, vm.remaining)
        assertFalse(vm.isDone)

        // Peak 2 at t = 0.5 s (0.5s separation)
        vm.onSample(magnitude = 3.5, timeSeconds = 0.5)
        assertEquals(1L, vm.remaining)
        assertFalse(vm.isDone)

        // Peak 3 at t = 1.0 s (0.5s separation)
        vm.onSample(magnitude = 3.2, timeSeconds = 1.0)
        assertEquals(0L, vm.remaining)
        assertTrue(vm.isDone)
    }

    @Test
    fun sampleBelowThresholdDoesNotCount() {
        val vm = ChallengeViewModel(initialTarget = 2L, threshold = 2.5, minIntervalSeconds = 0.3)

        vm.onSample(magnitude = 1.5, timeSeconds = 0.0)
        assertEquals(2L, vm.remaining)
        assertFalse(vm.isDone)
    }

    @Test
    fun resumesTargetFromPendingChallengeStore() {
        val prefs = InMemorySharedPreferences()
        val store = PendingChallengeStore(prefs)
        store.save(target = 12345L, ringTimeMillis = 1000L)

        val vm = ChallengeViewModel(store = store)

        assertEquals(12345L, vm.remaining)
        assertFalse(vm.isDone)
    }

    @Test
    fun creatingWithEmptyStoreDoesNotWriteAnything() {
        val prefs = InMemorySharedPreferences()
        val store = PendingChallengeStore(prefs)

        ChallengeViewModel(store = store)

        assertNull(store.load())
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun startTestDoesNotWritePendingChallenge() {
        val store = PendingChallengeStore(InMemorySharedPreferences())
        val vm = ChallengeViewModel(store = store)

        vm.startTest()

        assertNull(store.load())
        assertFalse(vm.isDone)
    }

    @Test
    fun reloadFromStoreUsesTargetWrittenByReceiverAfterCreation() {
        val store = PendingChallengeStore(InMemorySharedPreferences())
        val vm = ChallengeViewModel(initialTarget = 5L, store = store)
        assertEquals(5L, vm.remaining)

        store.save(target = 777L, ringTimeMillis = 1000L) // o AlarmReceiver grava ao tocar
        vm.reloadFromStore()

        assertEquals(777L, vm.remaining)
    }

    @Test
    fun onDoneCalledExactlyOnceWhenChallengeCompletes() {
        var calls = 0
        val vm = ChallengeViewModel(initialTarget = 2L, onDone = { calls++ })

        vm.onSample(magnitude = 3.0, timeSeconds = 0.0)
        assertEquals(0, calls)
        vm.onSample(magnitude = 3.0, timeSeconds = 1.0)
        assertEquals(1, calls)
        vm.onSample(magnitude = 3.0, timeSeconds = 2.0)
        assertEquals(1, calls)
    }

    @Test
    fun clearsPendingChallengeStoreWhenDone() {
        val prefs = InMemorySharedPreferences()
        val store = PendingChallengeStore(prefs)
        store.save(target = 1L, ringTimeMillis = 1000L)

        val vm = ChallengeViewModel(store = store)
        assertEquals(1L, vm.remaining)

        vm.onSample(magnitude = 3.0, timeSeconds = 0.0)

        assertEquals(0L, vm.remaining)
        assertTrue(vm.isDone)
        assertNull(store.load())
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private inner class Editor : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private val removes = mutableSetOf<String>()

            override fun putString(key: String?, value: String?): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { if (key != null) temp[key] = value; return this }
            override fun remove(key: String?): SharedPreferences.Editor { if (key != null) removes.add(key); return this }
            override fun clear(): SharedPreferences.Editor { data.clear(); return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                removes.forEach { data.remove(it) }
                temp.forEach { (k, v) -> if (v != null) data[k] = v else data.remove(k) }
            }
        }
    }
}
