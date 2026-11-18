package com.codecon.despertador.toothbrush

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import java.io.Closeable

/** O modelo não pôde ser carregado (arquivo ausente, formato inválido, falha nativa). Erro tratável. */
class ModelLoadException(cause: Throwable) : Exception("Não foi possível carregar o detector de objetos.", cause)

/** A inferência falhou para esta imagem. Erro tratável. */
class DetectionFailedException(cause: Throwable) : Exception("Falha ao detectar objetos na imagem.", cause)

/**
 * Detector de objetos que roda no aparelho, sem rede: MediaPipe Tasks Object Detector com o modelo
 * EfficientDet-Lite0 (COCO, inclui a classe `toothbrush`) lido de `assets/`.
 *
 * O construtor lança [ModelLoadException] se o modelo não carregar; quem chama deve tratar
 * (ex.: oferecer o desafio de sacudir). Devolve todas as detecções acima de um limiar baixo; o filtro
 * final é do verificador.
 */
class OnDeviceObjectDetector(
    context: Context,
    scoreThreshold: Float = DEFAULT_SCORE_THRESHOLD
) : ObjectDetecting, Closeable {

    companion object {
        const val MODEL_ASSET = "efficientdet_lite0.tflite"
        const val DEFAULT_SCORE_THRESHOLD = 0.3f
    }

    private var detector: ObjectDetector? = try {
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(scoreThreshold)
            .setMaxResults(MAX_RESULTS)
            .build()
        ObjectDetector.createFromOptions(context.applicationContext, options)
    } catch (e: Exception) {
        throw ModelLoadException(e)
    } catch (e: UnsatisfiedLinkError) {
        throw ModelLoadException(e)
    }

    @Synchronized
    override fun detect(bitmap: Bitmap): List<Detection> {
        val d = detector ?: throw DetectionFailedException(IllegalStateException("detector fechado"))
        return try {
            val result = d.detect(BitmapImageBuilder(bitmap).build())
            result.detections().mapNotNull { detection ->
                // Um objeto pode ter várias categorias; a primeira é a de maior score.
                detection.categories().firstOrNull()?.let { Detection(it.categoryName(), it.score()) }
            }
        } catch (e: Exception) {
            throw DetectionFailedException(e)
        }
    }

    @Synchronized
    override fun close() {
        detector?.close()
        detector = null
    }
}

private const val MAX_RESULTS = 10
