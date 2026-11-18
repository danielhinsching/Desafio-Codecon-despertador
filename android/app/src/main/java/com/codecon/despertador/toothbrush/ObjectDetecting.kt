package com.codecon.despertador.toothbrush

import android.graphics.Bitmap

/** Detector de objetos: devolve rótulo e score de cada objeto encontrado na imagem. */
interface ObjectDetecting {
    fun detect(bitmap: Bitmap): List<Detection>
}
