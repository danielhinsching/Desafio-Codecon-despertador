package com.codecon.despertador.alarm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.time.Duration
import java.time.ZoneId
import java.util.Locale

interface Scheduling {
    fun schedule(atMillis: Long): Int
    fun cancel(id: Int)
    fun canScheduleExactAlarms(): Boolean = true
    fun redirectToExactAlarmSettings() {}
}

class AlarmSetupViewModel(
    private val scheduler: Scheduling,
    private val store: AlarmStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    var hour: Int by mutableIntStateOf(7)
        private set

    var minute: Int by mutableIntStateOf(0)
        private set

    var isEnabled: Boolean by mutableStateOf(false)
        private set

    var scheduledId: Int? by mutableStateOf(null)
        private set

    var nextRingMillis: Long? by mutableStateOf(null)
        private set

    var statusText: String by mutableStateOf("Alarme desligado")
        private set

    val canScheduleExact: Boolean
        get() = scheduler.canScheduleExactAlarms()

    init {
        loadSavedAlarm()
    }

    fun loadSavedAlarm() {
        val saved = store.load()
        if (saved != null) {
            hour = saved.hour
            minute = saved.minute
            isEnabled = saved.enabled
            scheduledId = saved.scheduledId

            if (saved.enabled) {
                val now = currentTimeProvider()
                val nextRing = nextRingMillis(saved.hour, saved.minute, now, zone)
                nextRingMillis = nextRing
                statusText = formatRingText(saved.hour, saved.minute, nextRing, now)
            } else {
                nextRingMillis = null
                statusText = "Alarme desligado"
            }
        } else {
            statusText = "Nenhum alarme definido"
        }
    }

    fun onTimeChosen(h: Int, m: Int) {
        hour = h
        minute = m

        // Cancela o alarme anterior se houver
        scheduledId?.let { id ->
            scheduler.cancel(id)
        }

        val now = currentTimeProvider()
        val nextRing = nextRingMillis(h, m, now, zone)
        val newId = scheduler.schedule(nextRing)

        scheduledId = newId
        isEnabled = true
        nextRingMillis = nextRing
        statusText = formatRingText(h, m, nextRing, now)

        store.save(AlarmSetting(hour = h, minute = m, enabled = true, scheduledId = newId))
    }

    fun onDisable() {
        scheduledId?.let { id ->
            scheduler.cancel(id)
        }
        scheduledId = null
        isEnabled = false
        nextRingMillis = null
        statusText = "Alarme desligado"

        store.save(AlarmSetting(hour = hour, minute = minute, enabled = false, scheduledId = null))
    }

    fun openExactAlarmSettings() {
        scheduler.redirectToExactAlarmSettings()
    }

    private fun formatRingText(h: Int, m: Int, ringMillis: Long, nowMillis: Long): String {
        val timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", h, m)
        val diffMillis = maxOf(0L, ringMillis - nowMillis)
        val diffDuration = Duration.ofMillis(diffMillis)
        val hoursRemaining = diffDuration.toHours()
        val minutesRemaining = diffDuration.toMinutes() % 60

        val remainingPart = when {
            hoursRemaining > 0 && minutesRemaining > 0 -> " (daqui a ${hoursRemaining}h${minutesRemaining}min)"
            hoursRemaining > 0 -> " (daqui a ${hoursRemaining}h)"
            minutesRemaining > 0 -> " (daqui a ${minutesRemaining}min)"
            else -> " (em menos de 1 min)"
        }

        return "Toca às $timeFormatted$remainingPart"
    }
}
