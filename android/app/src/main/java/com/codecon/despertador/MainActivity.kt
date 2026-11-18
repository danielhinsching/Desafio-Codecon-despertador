package com.codecon.despertador

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    private var pendingStore: PendingChallengeStore? = null

    /** Desafio na tela: só quando o alarme tocou (pendente gravado pelo AlarmReceiver) ou no "Testar desafio". */
    private var showChallenge by mutableStateOf(false)

    override fun onPause() {
        super.onPause()
        challengeViewModel?.stopListening()
    }

    override fun onResume() {
        super.onResume()
        refreshPendingChallenge()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refreshPendingChallenge()
    }

    /** O alarme pode ter tocado com o app aberto: se há pendente, mostra o desafio com o alvo dele. */
    private fun refreshPendingChallenge() {
        if (pendingStore?.load() != null) {
            if (!showChallenge) challengeViewModel?.reloadFromStore()
            showChallenge = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockScreenFlags()
        requestNotificationPermissionIfNeeded()

        val prefs = getSharedPreferences("despertador_prefs", Context.MODE_PRIVATE)
        val store = PendingChallengeStore(prefs)
        pendingStore = store
        val sensorSource = SensorSource(this)
        val viewModel = ChallengeViewModel(
            store = store,
            sensorSource = sensorSource,
            // Concluiu as sacudidas: o som do alarme para e o app volta à tela de horário.
            onDone = {
                stopAlarmService()
                showChallenge = false
            }
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
                    // No "Testar desafio" (sem pendente), voltar leva à tela de horário e nada fica gravado.
                    BackHandler(enabled = showChallenge && store.load() == null) {
                        viewModel.stopListening()
                        showChallenge = false
                    }

                    if (showChallenge) {
                        ChallengeScreen(viewModel = viewModel, pixViewModel = pixViewModel)
                    } else {
                        AlarmSetupScreen(
                            viewModel = alarmSetupViewModel,
                            onTestChallenge = {
                                viewModel.startTest()
                                showChallenge = true
                            }
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
