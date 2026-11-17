package com.codecon.despertador.storage

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PendingChallengeStoreTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var store: PendingChallengeStore

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        store = PendingChallengeStore(fakePrefs)
    }

    @Test
    fun `load returns null when store is empty`() {
        assertNull(store.load())
    }

    @Test
    fun `save followed by load returns the exact saved values`() {
        store.save(target = 100L, ringTimeMillis = 1_700_000_000_000L)

        val loaded = store.load()
        assertNotNull(loaded)
        assertEquals(100L, loaded?.target)
        assertEquals(1_700_000_000_000L, loaded?.ringTimeMillis)
    }

    @Test
    fun `after clear load returns null`() {
        store.save(target = 42L, ringTimeMillis = 1_234_567_890L)
        assertNotNull(store.load())

        store.clear()
        assertNull(store.load())
    }

    @Test
    fun `target around 1 point 8 billion survives round trip as Long`() {
        val target = 1_800_000_000L
        val ringTime = 1_800_000_000_123L

        store.save(target = target, ringTimeMillis = ringTime)
        val loaded = store.load()

        assertNotNull(loaded)
        assertEquals(target, loaded?.target)
        assertEquals(ringTime, loaded?.ringTimeMillis)
    }
}

class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(values)

    override fun getString(key: String?, defValue: String?): String? =
        (values[key] as? String) ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (values[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        (values[key] as? Number)?.toInt() ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        (values[key] as? Number)?.toLong() ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (values[key] as? Number)?.toFloat() ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (values[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(this)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private val toRemove = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            temp[key] = value
            toRemove.remove(key)
            return this
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor {
            temp[key] = values
            toRemove.remove(key)
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            temp[key] = value
            toRemove.remove(key)
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            temp[key] = value
            toRemove.remove(key)
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            temp[key] = value
            toRemove.remove(key)
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            temp[key] = value
            toRemove.remove(key)
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            toRemove.add(key)
            temp.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            temp.clear()
            toRemove.clear()
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) {
                prefs.values.clear()
            }
            for (key in toRemove) {
                prefs.values.remove(key)
            }
            prefs.values.putAll(temp)
        }
    }
}
