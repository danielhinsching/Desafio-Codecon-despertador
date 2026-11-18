package com.codecon.despertador.toothbrush

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeObjectDetectorTest {

    // Bitmap é só passado adiante; o fake não o lê. No JVM o Bitmap do android.jar é um stub,
    // então criamos uma instância sem rodar construtor.
    private val bitmap: Bitmap = run {
        val f = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        f.isAccessible = true
        (f.get(null) as sun.misc.Unsafe).allocateInstance(Bitmap::class.java) as Bitmap
    }

    @Test
    fun returnsConfiguredFramesInOrder_thenRepeatsLast() {
        val tooth = Detection("toothbrush", 0.8f)
        val cup = Detection("cup", 0.4f)
        val fake = FakeObjectDetector(listOf(listOf(cup), listOf(tooth, cup)))

        assertEquals(listOf(cup), fake.detect(bitmap))
        assertEquals(listOf(tooth, cup), fake.detect(bitmap))
        assertEquals(listOf(tooth, cup), fake.detect(bitmap))
        assertEquals(3, fake.calls)
    }

    @Test
    fun emptyConfig_returnsNoDetections() {
        assertTrue(FakeObjectDetector().detect(bitmap).isEmpty())
    }

    @Test(expected = DetectionFailedException::class)
    fun failure_isSurfacedAsDetectionFailed() {
        FakeObjectDetector(failure = DetectionFailedException(RuntimeException("x"))).detect(bitmap)
    }

    @Test
    fun close_marksClosed() {
        val fake = FakeObjectDetector()
        fake.close()
        assertTrue(fake.closed)
    }
}
