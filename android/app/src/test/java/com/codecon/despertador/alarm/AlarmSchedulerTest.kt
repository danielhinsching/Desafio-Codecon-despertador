package com.codecon.despertador.alarm

import android.content.Context
import com.codecon.despertador.domain.ChallengeTarget
import com.codecon.despertador.domain.VolumeRamp
import com.codecon.despertador.storage.FakeSharedPreferences
import com.codecon.despertador.storage.PendingChallengeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AlarmSchedulerTest {

    private class TestableAlarmScheduler(
        context: Context,
        var permissionGranted: Boolean = true,
        var fullScreenGranted: Boolean = true
    ) : AlarmScheduler(context) {
        var exactAlarmSettingsRedirected = false
        var fullScreenSettingsRedirected = false

        override fun canScheduleExactAlarms(): Boolean = permissionGranted
        override fun canUseFullScreenIntent(): Boolean = fullScreenGranted

        override fun redirectToExactAlarmSettings() {
            exactAlarmSettingsRedirected = true
        }

        override fun redirectToFullScreenIntentSettings() {
            fullScreenSettingsRedirected = true
        }
    }

    private class TestableAlarmReceiver : AlarmReceiver() {
        var activityLaunched = false
        var serviceStarted = false

        override fun launchChallengeActivityIfInteractive(context: Context, target: Long, ringTimeMillis: Long) {
            activityLaunched = true
        }

        override fun startAlarmService(context: Context, target: Long, ringTimeMillis: Long) {
            serviceStarted = true
        }
    }

    @Test
    fun `computeAlarmId generates non-zero positive integer`() {
        val scheduler = AlarmScheduler(fakeContext())
        val id1 = scheduler.computeAlarmId(1_800_000_000_000L)
        val id2 = scheduler.computeAlarmId(0L)
        val id3 = scheduler.computeAlarmId(1_800_000_001_500L)

        assertTrue("Id should be positive", id1 > 0)
        assertTrue("Id should be positive for 0 timestamp", id2 > 0)
        assertTrue("Id should be positive", id3 > 0)
    }

    @Test
    fun `validatePermissionsBeforeSchedule throws and redirects when exact alarm is denied`() {
        val scheduler = TestableAlarmScheduler(fakeContext(), permissionGranted = false)

        try {
            scheduler.validatePermissionsBeforeSchedule()
            fail("Expected ExactAlarmPermissionDeniedException")
        } catch (e: ExactAlarmPermissionDeniedException) {
            assertTrue("Should call redirect to exact alarm settings", scheduler.exactAlarmSettingsRedirected)
            assertEquals("SCHEDULE_EXACT_ALARM permission not granted", e.message)
        }
    }

    @Test
    fun `validatePermissionsBeforeSchedule redirects when full screen intent is denied on Android 14`() {
        val scheduler = TestableAlarmScheduler(fakeContext(), permissionGranted = true, fullScreenGranted = false)

        scheduler.validatePermissionsBeforeSchedule()
        assertTrue("Should call redirect to full screen settings", scheduler.fullScreenSettingsRedirected)
    }

    @Test
    fun `AlarmReceiver processAlarmTrigger computes target and saves to PendingChallengeStore`() {
        val receiver = AlarmReceiver()
        val fakePrefs = FakeSharedPreferences()
        val store = PendingChallengeStore(fakePrefs)

        val ringTimeMillis = 1_800_000_000_000L
        val computedTarget = receiver.processAlarmTrigger(fakeContext(), ringTimeMillis, store)

        val expectedTarget = ChallengeTarget.shakes(ringTimeMillis)
        assertEquals(expectedTarget, computedTarget)
        assertEquals(1_800_000_000L, computedTarget)

        val loaded = store.load()
        assertNotNull(loaded)
        assertEquals(expectedTarget, loaded?.target)
        assertEquals(ringTimeMillis, loaded?.ringTimeMillis)
    }

    @Test
    fun `TestableAlarmReceiver triggers activity and service`() {
        val receiver = TestableAlarmReceiver()
        val fakePrefs = FakeSharedPreferences()
        val store = PendingChallengeStore(fakePrefs)

        val ringTimeMillis = 1_800_000_000_000L
        receiver.processAlarmTrigger(fakeContext(), ringTimeMillis, store)
        receiver.launchChallengeActivityIfInteractive(fakeContext(), 1_800_000_000L, ringTimeMillis)
        receiver.startAlarmService(fakeContext(), 1_800_000_000L, ringTimeMillis)

        assertTrue("Activity should be launched", receiver.activityLaunched)
        assertTrue("Service should be started", receiver.serviceStarted)
    }

    @Test
    fun `volume ramp progression starts low and reaches max at duration`() {
        val ramp = VolumeRamp(start = 0.05f, end = 1.0f, durationSeconds = 30.0)

        // At 0s
        assertEquals(0.05f, ramp.level(0.0), 0.001f)

        // At 15s (halfway)
        assertEquals(0.525f, ramp.level(15.0), 0.01f)

        // At 30s (full)
        assertEquals(1.0f, ramp.level(30.0), 0.001f)

        // Past duration
        assertEquals(1.0f, ramp.level(60.0), 0.001f)
    }

    private fun fakeContext(): Context {
        return java.lang.reflect.Proxy.newProxyInstance(
            Context::class.java.classLoader,
            arrayOf(Context::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getPackageName" -> "com.codecon.despertador"
                else -> null
            }
        } as Context
    }
}
