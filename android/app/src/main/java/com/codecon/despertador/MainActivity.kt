package com.codecon.despertador

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.codecon.despertador.data.HttpPixApi
import com.codecon.despertador.sensor.SensorSource
import com.codecon.despertador.storage.PendingChallengeStore
import com.codecon.despertador.storage.PendingPixStore
import com.codecon.despertador.ui.ChallengeScreen
import com.codecon.despertador.ui.ChallengeViewModel
import com.codecon.despertador.ui.PixPaymentViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("despertador_prefs", Context.MODE_PRIVATE)
        val store = PendingChallengeStore(prefs)
        val sensorSource = SensorSource(this)
        val viewModel = ChallengeViewModel(
            store = store,
            sensorSource = sensorSource
        )

        val pixStore = PendingPixStore(prefs)
        val pixViewModel = PixPaymentViewModel(
            api = HttpPixApi(BuildConfig.PIX_SERVER_URL),
            store = pixStore,
            // O alarme que está tocando é identificado pelo instante em que tocou (desafio pendente).
            alarmIdProvider = { store.load()?.ringTimeMillis?.toString() },
            onReleased = {
                // Servidor liberou o alarme: encerra o desafio. (A parada do som do serviço entra com a #17.)
                viewModel.stopListening()
                store.clear()
                finish()
            }
        )

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChallengeScreen(viewModel = viewModel, pixViewModel = pixViewModel)
                }
            }
        }
    }
}
