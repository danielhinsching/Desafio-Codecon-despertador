package com.codecon.despertador.toothbrush

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

private class CountingCompletion : ChallengeCompletion {
    var calls = 0
    override fun onCompleted() {
        calls++
    }
}

class ToothbrushViewModelTest {

    private val bitmap: Bitmap = mock(Bitmap::class.java)
    private val tooth = Detection("toothbrush", 0.9f)
    private val cup = Detection("cup", 0.9f)
    private var now = 0.0
    private val completion = CountingCompletion()

    private fun vm(detector: ObjectDetecting?, verifier: ToothbrushVerifier = ToothbrushVerifier()) =
        ToothbrushViewModel(detector, completion, clockSeconds = { now }, verifier = verifier)

    private fun ToothbrushViewModel.frame() {
        processFrame(bitmap)
        now += 0.3
    }

    @Test
    fun startsSearching_withoutShakeOption() {
        val vm = vm(FakeObjectDetector())
        assertEquals(ToothbrushStatus.Searching, vm.status)
        assertFalse(vm.canUseShake)
    }

    @Test
    fun threeToothbrushFrames_confirmAndCompleteOnce() {
        val vm = vm(FakeObjectDetector(listOf(listOf(tooth))))
        vm.frame()
        assertEquals(ToothbrushStatus.Progress(1, 3), vm.status)
        vm.frame()
        assertEquals(ToothbrushStatus.Progress(2, 3), vm.status)
        assertEquals(0, completion.calls)
        vm.frame()
        assertEquals(ToothbrushStatus.Confirmed, vm.status)
        assertEquals(1, completion.calls)

        vm.frame() // quadros extras não completam de novo
        assertEquals(1, completion.calls)
    }

    @Test
    fun frameWithoutToothbrush_resetsProgress() {
        val vm = vm(FakeObjectDetector(listOf(listOf(tooth), listOf(tooth), listOf(cup), listOf(tooth))))
        repeat(2) { vm.frame() }
        assertEquals(ToothbrushStatus.Progress(2, 3), vm.status)
        vm.frame()
        assertEquals(ToothbrushStatus.Searching, vm.status)
        vm.frame()
        assertEquals(ToothbrushStatus.Progress(1, 3), vm.status)
        assertEquals(0, completion.calls)
    }

    @Test
    fun fallbackOffered_afterTimeoutWithoutConfirming() {
        val vm = vm(
            FakeObjectDetector(listOf(listOf(cup))),
            ToothbrushVerifier(fallbackAfterSeconds = 60.0)
        )
        vm.frame()
        assertFalse(vm.canUseShake)

        now = 61.0
        vm.tick()
        assertTrue(vm.canUseShake)
        assertEquals(0, completion.calls)
    }

    @Test
    fun noFallback_afterConfirming() {
        val vm = vm(FakeObjectDetector(listOf(listOf(tooth))))
        repeat(3) { vm.frame() }
        now = 1_000.0
        vm.tick()
        assertFalse(vm.canUseShake)
    }

    @Test
    fun permissionDenied_warnsAndOffersShake() {
        val vm = vm(FakeObjectDetector())
        vm.onCameraPermissionDenied()
        assertTrue(vm.status is ToothbrushStatus.Unavailable)
        assertTrue(vm.canUseShake)
    }

    @Test
    fun noCamera_warnsAndOffersShake() {
        val vm = vm(FakeObjectDetector())
        vm.onNoCamera()
        assertTrue(vm.status is ToothbrushStatus.Unavailable)
        assertTrue(vm.canUseShake)
    }

    @Test
    fun detectorNull_modelFailedToLoad_offersShake() {
        val vm = vm(null)
        assertTrue(vm.status is ToothbrushStatus.Unavailable)
        assertTrue(vm.canUseShake)
        vm.processFrame(bitmap) // não derruba
        assertEquals(0, completion.calls)
    }

    @Test
    fun detectionFailure_offersShake_withoutCrashing() {
        val vm = vm(FakeObjectDetector(failure = DetectionFailedException(RuntimeException("x"))))
        vm.frame()
        assertTrue(vm.status is ToothbrushStatus.Unavailable)
        assertTrue(vm.canUseShake)
        assertEquals(0, completion.calls)
    }

    @Test
    fun unavailable_ignoresFurtherFrames() {
        val fake = FakeObjectDetector(listOf(listOf(tooth)))
        val vm = vm(fake)
        vm.onCameraError()
        repeat(5) { vm.frame() }
        assertEquals(0, fake.calls)
        assertEquals(0, completion.calls)
    }

    @Test
    fun acceptFrame_limitsRate() {
        val vm = vm(FakeObjectDetector())
        assertTrue(vm.acceptFrame(1_000))
        assertFalse(vm.acceptFrame(1_100))
        assertTrue(vm.acceptFrame(1_250))
        assertFalse(vm.acceptFrame(1_300))
    }
}
