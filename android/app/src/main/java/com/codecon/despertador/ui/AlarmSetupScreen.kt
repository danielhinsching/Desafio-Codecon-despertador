package com.codecon.despertador.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codecon.despertador.alarm.AlarmSetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSetupScreen(
    viewModel: AlarmSetupViewModel,
    modifier: Modifier = Modifier,
    onTestChallenge: (() -> Unit)? = null
) {
    val hour = viewModel.hour
    val minute = viewModel.minute
    val isEnabled = viewModel.isEnabled
    val statusText = viewModel.statusText
    val canScheduleExact = viewModel.canScheduleExact

    val timePickerState = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = true
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Definir Despertador",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Card com status do alarme
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isEnabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isEnabled) "Alarme ativado" else "Alarme desligado",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                viewModel.onTimeChosen(timePickerState.hour, timePickerState.minute)
                            } else {
                                viewModel.onDisable()
                            }
                        }
                    )
                }
            }
        }

        // Aviso de permissão de alarme exato se não estiver concedida
        if (!canScheduleExact) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Permissão de alarme exato necessária para tocar no horário correto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.openExactAlarmSettings() }) {
                        Text("Abrir Configurações")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // TimePicker
        TimePicker(
            state = timePickerState
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Botão Definir Alarme
        Button(
            onClick = {
                viewModel.onTimeChosen(timePickerState.hour, timePickerState.minute)
            },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(52.dp)
        ) {
            Text(
                text = "Definir alarme",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (onTestChallenge != null) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onTestChallenge,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Text("Testar Tela de Desafio")
            }
        }
    }
}
