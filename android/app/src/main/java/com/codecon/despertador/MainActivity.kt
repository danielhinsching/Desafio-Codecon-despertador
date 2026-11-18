package com.codecon.despertador

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.codecon.despertador.sensor.SensorSource
import com.codecon.despertador.storage.PendingChallengeStore
import com.codecon.despertador.ui.ChallengeScreen
import com.codecon.despertador.ui.ChallengeViewModel

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

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChallengeScreen(viewModel = viewModel)
                }
            }
        }
    }
}
