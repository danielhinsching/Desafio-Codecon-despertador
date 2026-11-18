package com.codecon.despertador.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.codecon.despertador.MainActivity
import kotlin.math.abs

class ExactAlarmPermissionDeniedException(message: String) : SecurityException(message)

open class AlarmScheduler(
    val context: Context,
    val alarmManager: AlarmManager? = null
) {

    private val resolvedAlarmManager: AlarmManager?
        get() = alarmManager ?: (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)

    open fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            resolvedAlarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    open fun canUseFullScreenIntent(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.canUseFullScreenIntent() ?: true
        } else {
            true
        }
    }

    open fun computeAlarmId(atMillis: Long): Int {
        return (atMillis % Int.MAX_VALUE).toInt().let { if (it == 0) 1 else abs(it) }
    }

    open fun validatePermissionsBeforeSchedule() {
        if (!canScheduleExactAlarms()) {
            redirectToExactAlarmSettings()
            throw ExactAlarmPermissionDeniedException("SCHEDULE_EXACT_ALARM permission not granted")
        }
        if (!canUseFullScreenIntent()) {
            redirectToFullScreenIntentSettings()
        }
    }

    open fun schedule(atMillis: Long): Int {
        validatePermissionsBeforeSchedule()

        val id = computeAlarmId(atMillis)
        val triggerIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_ALARM_ID, id)
            putExtra(EXTRA_RING_TIME, atMillis)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            triggerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            id,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(atMillis, showPendingIntent)
        resolvedAlarmManager?.setAlarmClock(alarmClockInfo, pendingIntent)

        return id
    }

    open fun cancel(id: Int) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            resolvedAlarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    open fun redirectToExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    open fun redirectToFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    companion object {
        const val ACTION_ALARM_TRIGGER = "com.codecon.despertador.ALARM_TRIGGER"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_RING_TIME = "ring_time_millis"
    }
}
