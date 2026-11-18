package com.codecon.despertador.toothbrush

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * Desafio da escova: prévia ao vivo da câmera (sem galeria) + detecção no aparelho.
 * `completion` é injetado (a ligação real é da #48); `onUseShake` leva de volta ao desafio de sacudir.
 */
@Composable
fun ToothbrushScreen(
    completion: ChallengeCompletion,
    onUseShake: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    val detector = remember {
        try {
            OnDeviceObjectDetector(context)
        } catch (e: ModelLoadException) {
            null
        }
    }
    val viewModel = remember {
        ToothbrushViewModel(
            detector = detector,
            // O detector roda em thread de fundo: o fim do desafio volta para a thread principal.
            completion = object : ChallengeCompletion {
                override fun onCompleted() = mainExecutor.execute { completion.onCompleted() }
            },
            clockSeconds = { SystemClock.elapsedRealtime() / 1000.0 }
        )
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val hasCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) viewModel.onCameraPermissionDenied()
    }

    LaunchedEffect(Unit) {
        if (!hasCamera) {
            viewModel.onNoCamera()
        } else if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            viewModel.tick()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            detector?.close()
        }
    }

    val status = viewModel.status

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCamera && hasPermission && detector != null && status !is ToothbrushStatus.Unavailable) {
            CameraPreview(
                viewModel = viewModel,
                analysisExecutor = analysisExecutor,
                lifecycleOwner = lifecycleOwner
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = when (status) {
                    ToothbrushStatus.Searching -> "Mostre sua escova de dente para a câmera"
                    is ToothbrushStatus.Progress ->
                        "Escova detectada! Segure firme (${status.frames}/${status.required})"
                    ToothbrushStatus.Confirmed -> "Escova confirmada!"
                    is ToothbrushStatus.Unavailable -> status.message
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = if (status is ToothbrushStatus.Unavailable) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )

            if (viewModel.canUseShake) {
                Button(onClick = onUseShake, modifier = Modifier.fillMaxWidth(0.8f).height(56.dp)) {
                    Text("Sacudir em vez disso")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CameraPreview(
    viewModel: ToothbrushViewModel,
    analysisExecutor: java.util.concurrent.Executor,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    var provider: ProcessCameraProvider? = remember { null }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { proxy ->
                    try {
                        if (viewModel.acceptFrame(SystemClock.elapsedRealtime())) {
                            viewModel.processFrame(proxy.toUprightBitmap())
                        }
                    } finally {
                        proxy.close()
                    }
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                viewModel.onCameraError()
            }
        }, mainExecutor)

        // Libera a câmera ao sair da tela.
        onDispose { provider?.unbindAll() }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private fun ImageProxy.toUprightBitmap(): Bitmap {
    val bitmap = toBitmap()
    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
