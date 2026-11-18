package com.codecon.despertador

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.codecon.despertador.alarm.AlarmRingingService
import com.codecon.despertador.alarm.AlarmScheduler
import com.codecon.despertador.alarm.AlarmSetupViewModel
import com.codecon.despertador.alarm.AlarmStore
import com.codecon.despertador.data.HttpPixApi
import com.codecon.despertador.sensor.SensorSource
import com.codecon.despertador.storage.PendingChallengeStore
import com.codecon.despertador.storage.PendingPixStore
import com.codecon.despertador.ui.AlarmSetupScreen
import com.codecon.despertador.ui.ChallengeScreen
import com.codecon.despertador.ui.ChallengeViewModel
import com.codecon.despertador.ui.PixPaymentViewModel

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Resultado da solicitação de permissão POST_NOTIFICATIONS
    }

    private var challengeViewModel: ChallengeViewModel? = null

    override fun onPause() {
        super.onPause()
        challengeViewModel?.stopListening()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockScreenFlags()
        requestNotificationPermissionIfNeeded()

        val prefs = getSharedPreferences("despertador_prefs", Context.MODE_PRIVATE)
        val store = PendingChallengeStore(prefs)
        val sensorSource = SensorSource(this)
        val viewModel = ChallengeViewModel(
            store = store,
            sensorSource = sensorSource,
            // Concluiu as sacudidas: o som do alarme para.
            onDone = { stopAlarmService() }
        )
        challengeViewModel = viewModel

        val pixStore = PendingPixStore(prefs)
        val pixViewModel = PixPaymentViewModel(
            api = HttpPixApi(BuildConfig.PIX_SERVER_URL),
            store = pixStore,
            // O alarme que está tocando é identificado pelo instante em que tocou (desafio pendente).
            alarmIdProvider = { store.load()?.ringTimeMillis?.toString() },
            onReleased = {
                // Servidor liberou o alarme: encerra o som e o desafio.
                stopAlarmService()
                viewModel.stopListening()
                store.clear()
                finish()
            }
        )

        val alarmStore = AlarmStore(prefs)
        val alarmScheduler = AlarmScheduler(this)
        val alarmSetupViewModel = AlarmSetupViewModel(
            scheduler = alarmScheduler,
            store = alarmStore
        )

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showChallengeTest by remember { mutableStateOf(false) }
                    val hasPendingChallenge = store.load() != null && !viewModel.isDone

                    if (hasPendingChallenge || showChallengeTest) {
                        ChallengeScreen(viewModel = viewModel, pixViewModel = pixViewModel)
                    } else {
                        AlarmSetupScreen(
                            viewModel = alarmSetupViewModel,
                            onTestChallenge = { showChallengeTest = true }
                        )
                    }
                }
            }
        }
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun stopAlarmService() {
        try {
            val stopIntent = Intent(this, AlarmRingingService::class.java).apply {
                action = AlarmRingingService.ACTION_STOP_ALARM
            }
            startService(stopIntent)
        } catch (_: Exception) {}
    }
}
