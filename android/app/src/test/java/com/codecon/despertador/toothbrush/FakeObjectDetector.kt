package com.codecon.despertador.toothbrush

import android.graphics.Bitmap
import java.io.Closeable

/** Detector falso para testes: devolve as detecções configuradas, em ordem, a cada chamada. */
class FakeObjectDetector(
    private val frames: List<List<Detection>> = emptyList(),
    private val failure: Exception? = null
) : ObjectDetecting, Closeable {

    var calls = 0
        private set
    var closed = false
        private set

    override fun detect(bitmap: Bitmap): List<Detection> {
        failure?.let { throw it }
        val result = frames.getOrElse(minOf(calls, frames.size - 1).coerceAtLeast(0)) { emptyList() }
        calls++
        return result
    }

    override fun close() {
        closed = true
    }
}
