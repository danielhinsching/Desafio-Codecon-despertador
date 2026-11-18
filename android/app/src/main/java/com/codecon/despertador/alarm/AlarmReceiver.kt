package com.codecon.despertador.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.codecon.despertador.MainActivity
import com.codecon.despertador.domain.ChallengeTarget
import com.codecon.despertador.storage.PendingChallengeStore

open class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val ringTimeMillis = intent?.getLongExtra(AlarmScheduler.EXTRA_RING_TIME, 0L)
            ?.takeIf { it > 0L } ?: System.currentTimeMillis()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val store = PendingChallengeStore(prefs)
        val target = processAlarmTrigger(ringTimeMillis, store)

        // Se o aparelho estiver interativo (tela acesa/em uso), inicia a Activity diretamente
        launchChallengeActivityIfInteractive(context, target, ringTimeMillis)

        // Inicia o foreground service para tocar o som com MediaPlayer e VolumeRamp
        startAlarmService(context, target, ringTimeMillis)
    }

    open fun processAlarmTrigger(ringTimeMillis: Long, store: PendingChallengeStore): Long {
        val target = ChallengeTarget.shakes(ringTimeMillis)
        store.save(target = target, ringTimeMillis = ringTimeMillis)
        return target
    }

    open fun launchChallengeActivityIfInteractive(context: Context, target: Long, ringTimeMillis: Long) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isInteractive = powerManager?.isInteractive ?: false
            if (isInteractive) {
                val activityIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(AlarmRingingService.EXTRA_TARGET, target)
                    putExtra(AlarmRingingService.EXTRA_RING_TIME, ringTimeMillis)
                }
                context.startActivity(activityIntent)
            }
        } catch (_: Exception) {
            // Se restringido pelo SO em background, a notificação com full-screen intent ainda atua
        }
    }

    open fun startAlarmService(context: Context, target: Long, ringTimeMillis: Long) {
        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            putExtra(AlarmRingingService.EXTRA_RING_TIME, ringTimeMillis)
            putExtra(AlarmRingingService.EXTRA_TARGET, target)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        const val PREFS_NAME = "despertador_prefs"
    }
}
