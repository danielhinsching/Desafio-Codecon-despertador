package com.codecon.despertador.alarm

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmSetupViewModelTest {

    private val zone = ZoneId.of("UTC")
    private val fakeNow = ZonedDateTime.of(2026, 11, 17, 6, 0, 0, 0, zone).toInstant().toEpochMilli()

    private class FakeScheduler : Scheduling {
        val scheduled = mutableMapOf<Int, Long>()
        val cancelled = mutableListOf<Int>()
        private var nextId = 100

        override fun schedule(atMillis: Long): Int {
            val id = nextId++
            scheduled[id] = atMillis
            return id
        }

        override fun cancel(id: Int) {
            cancelled.add(id)
            scheduled.remove(id)
        }
    }

    @Test
    fun settingTimeSchedulesAlarmAndShowsTimeToRing() {
        val prefs = InMemorySharedPreferences()
        val store = AlarmStore(prefs)
        val scheduler = FakeScheduler()

        val vm = AlarmSetupViewModel(
            scheduler = scheduler,
            store = store,
            zone = zone,
            currentTimeProvider = { fakeNow }
        )

        vm.onTimeChosen(7, 30)

        assertTrue(vm.isEnabled)
        assertEquals(7, vm.hour)
        assertEquals(30, vm.minute)
        assertTrue(vm.statusText.startsWith("Toca às 07:30"))
        assertEquals(1, scheduler.scheduled.size)

        // Verify stored in AlarmStore
        val saved = store.load()
        assertEquals(7, saved?.hour)
        assertEquals(30, saved?.minute)
        assertTrue(saved?.enabled == true)
    }

    @Test
    fun settingNewTimeCancelsPreviousAlarm() {
        val prefs = InMemorySharedPreferences()
        val store = AlarmStore(prefs)
        val scheduler = FakeScheduler()

        val vm = AlarmSetupViewModel(
            scheduler = scheduler,
            store = store,
            zone = zone,
            currentTimeProvider = { fakeNow }
        )

        vm.onTimeChosen(7, 30)
        val firstId = vm.scheduledId!!

        vm.onTimeChosen(8, 0)
        val secondId = vm.scheduledId!!

        assertTrue(scheduler.cancelled.contains(firstId))
        assertEquals(1, scheduler.scheduled.size)
        assertTrue(scheduler.scheduled.containsKey(secondId))
        assertTrue(vm.statusText.startsWith("Toca às 08:00"))
    }

    @Test
    fun onDisableCancelsAlarmAndUpdatesStatus() {
        val prefs = InMemorySharedPreferences()
        val store = AlarmStore(prefs)
        val scheduler = FakeScheduler()

        val vm = AlarmSetupViewModel(
            scheduler = scheduler,
            store = store,
            zone = zone,
            currentTimeProvider = { fakeNow }
        )

        vm.onTimeChosen(7, 30)
        val id = vm.scheduledId!!

        vm.onDisable()

        assertFalse(vm.isEnabled)
        assertNull(vm.scheduledId)
        assertEquals("Alarme desligado", vm.statusText)
        assertTrue(scheduler.cancelled.contains(id))
        assertTrue(scheduler.scheduled.isEmpty())

        val saved = store.load()
        assertFalse(saved?.enabled ?: true)
        assertNull(saved?.scheduledId)
    }

    @Test
    fun reopeningAppLoadsSavedAlarm() {
        val prefs = InMemorySharedPreferences()
        val store = AlarmStore(prefs)
        store.save(AlarmSetting(hour = 6, minute = 45, enabled = true, scheduledId = 42))

        val scheduler = FakeScheduler()
        val vm = AlarmSetupViewModel(
            scheduler = scheduler,
            store = store,
            zone = zone,
            currentTimeProvider = { fakeNow }
        )

        assertTrue(vm.isEnabled)
        assertEquals(6, vm.hour)
        assertEquals(45, vm.minute)
        assertEquals(42, vm.scheduledId)
        assertTrue(vm.statusText.startsWith("Toca às 06:45"))
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
