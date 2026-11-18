package com.codecon.despertador.alarm

import android.content.SharedPreferences

data class AlarmSetting(
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val scheduledId: Int?
)

class AlarmStore(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_ALARM_HOUR = "alarm_hour"
        const val KEY_ALARM_MINUTE = "alarm_minute"
        const val KEY_ALARM_ENABLED = "alarm_enabled"
        const val KEY_ALARM_SCHEDULED_ID = "alarm_scheduled_id"
    }

    fun save(s: AlarmSetting) {
        val editor = prefs.edit()
            .putInt(KEY_ALARM_HOUR, s.hour)
            .putInt(KEY_ALARM_MINUTE, s.minute)
            .putBoolean(KEY_ALARM_ENABLED, s.enabled)

        if (s.scheduledId != null) {
            editor.putInt(KEY_ALARM_SCHEDULED_ID, s.scheduledId)
        } else {
            editor.remove(KEY_ALARM_SCHEDULED_ID)
        }
        editor.apply()
    }

    fun load(): AlarmSetting? {
        if (!prefs.contains(KEY_ALARM_HOUR) || !prefs.contains(KEY_ALARM_MINUTE)) {
            return null
        }
        val hour = prefs.getInt(KEY_ALARM_HOUR, 0)
        val minute = prefs.getInt(KEY_ALARM_MINUTE, 0)
        val enabled = prefs.getBoolean(KEY_ALARM_ENABLED, false)
        val scheduledId = if (prefs.contains(KEY_ALARM_SCHEDULED_ID)) {
            prefs.getInt(KEY_ALARM_SCHEDULED_ID, 0)
        } else {
            null
        }
        return AlarmSetting(hour, minute, enabled, scheduledId)
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_ALARM_HOUR)
            .remove(KEY_ALARM_MINUTE)
            .remove(KEY_ALARM_ENABLED)
            .remove(KEY_ALARM_SCHEDULED_ID)
            .apply()
    }
}
