package com.codecon.despertador.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codecon.despertador.data.PixCharge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Seção "Pagar por Pix" da tela do desafio. Sacudir continua funcionando com o QR aberto. */
@Composable
fun PixPaymentSection(viewModel: PixPaymentViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.resumeIfPending()
        viewModel.runPolling()
    }
    LaunchedEffect(viewModel) {
        while (true) {
            delay(1_000)
            viewModel.tick()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (val state = viewModel.state) {
            PixState.Idle, PixState.Released -> PayButton { scope.launch { viewModel.startPayment() } }

            PixState.Creating -> Text("Gerando Pix...", style = MaterialTheme.typography.bodyMedium)

            is PixState.Waiting -> WaitingContent(viewModel, state)

            PixState.Expired -> {
                Text(
                    "O QR Code expirou. O alarme continua tocando.",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                PayButton(label = "Gerar outro QR Code") {
                    viewModel.dismiss()
                    scope.launch { viewModel.startPayment() }
                }
            }

            is PixState.Failed -> {
                Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                PayButton(label = "Tentar de novo") {
                    viewModel.dismiss()
                    scope.launch { viewModel.startPayment() }
                }
            }
        }
    }
}

@Composable
private fun PayButton(label: String = "Pagar R$ 9,90 por Pix", onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(56.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun WaitingContent(viewModel: PixPaymentViewModel, state: PixState.Waiting) {
    val clipboard = LocalClipboardManager.current
    val charge = state.charge
    val bitmap = remember(charge.qrCodeBase64) { decodeQr(charge) }

    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = "QR Code Pix", modifier = Modifier.size(220.dp))
    }

    val seconds = viewModel.remainingSeconds(charge)
    Text(
        text = "Expira em %02d:%02d".format(seconds / 60, seconds % 60),
        style = MaterialTheme.typography.bodyMedium
    )

    Text(
        text = charge.qrCode,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 3,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
    Button(onClick = { clipboard.setText(AnnotatedString(charge.qrCode)) }) {
        Text("Copiar código Pix")
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = state.notice ?: "Aguardando o pagamento...",
        style = MaterialTheme.typography.bodyMedium,
        color = if (state.notice != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center
    )
}

private fun decodeQr(charge: PixCharge) =
    runCatching {
        val bytes = Base64.decode(charge.qrCodeBase64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
