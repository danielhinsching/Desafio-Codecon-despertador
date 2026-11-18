package com.codecon.despertador.ui

import com.codecon.despertador.data.ConsumeResult
import com.codecon.despertador.data.PixApi
import com.codecon.despertador.data.PixApiException
import com.codecon.despertador.data.PixCharge
import com.codecon.despertador.data.PixStatus
import com.codecon.despertador.data.PixStatusResponse
import com.codecon.despertador.storage.FakeSharedPreferences
import com.codecon.despertador.storage.PendingPixStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val T0 = 1_800_000_000_000L
private const val THIRTY_MIN = 30 * 60 * 1000L

private class FakePixApi : PixApi {
    var createCalls = 0
    var consumeCalls = 0
    var status = PixStatus.PENDING
    var failWith: PixApiException.Kind? = null
    var consumeResult = ConsumeResult(released = true)
    var lastCreate: Pair<String, String>? = null
    var lastConsume: Triple<String, String, String>? = null

    override suspend fun createPayment(alarmId: String, deviceId: String): PixCharge {
        failWith?.let { throw PixApiException(it) }
        createCalls++
        lastCreate = alarmId to deviceId
        return PixCharge("pay-$createCalls", "00020126-copia-e-cola", "iVBORw0KGgo=", T0 + THIRTY_MIN, 990)
    }

    override suspend fun getStatus(paymentId: String): PixStatusResponse {
        failWith?.let { throw PixApiException(it) }
        return PixStatusResponse(status, T0 + THIRTY_MIN)
    }

    override suspend fun consume(paymentId: String, alarmId: String, deviceId: String): ConsumeResult {
        failWith?.let { throw PixApiException(it) }
        consumeCalls++
        lastConsume = Triple(paymentId, alarmId, deviceId)
        return consumeResult
    }
}

class PixPaymentViewModelTest {

    private val prefs = FakeSharedPreferences()
    private val store = PendingPixStore(prefs)
    private val api = FakePixApi()
    private var now = T0
    private var released = 0

    private fun vm(alarmId: String? = "alarm-1") = PixPaymentViewModel(
        api = api,
        store = store,
        alarmIdProvider = { alarmId },
        onReleased = { released++ },
        clock = { now }
    )

    @Test
    fun startPayment_createsChargeAndWaits() = runBlocking {
        val vm = vm()
        vm.startPayment()

        val s = vm.state as PixState.Waiting
        assertEquals("pay-1", s.charge.paymentId)
        assertEquals("alarm-1", api.lastCreate?.first)
        assertEquals(store.deviceId(), api.lastCreate?.second)
        assertEquals("pay-1", store.load()?.paymentId)
        assertEquals(1800L, vm.remainingSeconds(s.charge))
    }

    @Test
    fun approved_consumesAndReleases_clearingPix() = runBlocking {
        val vm = vm()
        vm.startPayment()
        api.status = PixStatus.APPROVED
        vm.poll()

        assertEquals(PixState.Released, vm.state)
        assertEquals(1, api.consumeCalls)
        assertEquals("pay-1", api.lastConsume?.first)
        assertEquals("alarm-1", api.lastConsume?.second)
        assertEquals(1, released)
        assertNull(store.load())
    }

    @Test
    fun pending_keepsWaitingAndDoesNotConsume() = runBlocking {
        val vm = vm()
        vm.startPayment()
        vm.poll()

        assertTrue(vm.state is PixState.Waiting)
        assertEquals(0, api.consumeCalls)
        assertEquals(0, released)
    }

    @Test
    fun consumeNotReleased_doesNotReleaseAlarm() = runBlocking {
        val vm = vm()
        vm.startPayment()
        api.status = PixStatus.APPROVED
        api.consumeResult = ConsumeResult(released = false, reason = "mismatch")
        vm.poll()

        assertTrue(vm.state is PixState.Failed)
        assertEquals(0, released)
    }

    @Test
    fun consumeNotApproved_keepsWaiting() = runBlocking {
        val vm = vm()
        vm.startPayment()
        api.status = PixStatus.APPROVED
        api.consumeResult = ConsumeResult(released = false, reason = "not_approved")
        vm.poll()

        assertTrue(vm.state is PixState.Waiting)
        assertEquals(0, released)
    }

    @Test
    fun serverExpired_showsExpiredAndClearsStore_thenAllowsNewCharge() = runBlocking {
        val vm = vm()
        vm.startPayment()
        api.status = PixStatus.EXPIRED
        vm.poll()

        assertEquals(PixState.Expired, vm.state)
        assertNull(store.load())

        vm.dismiss()
        vm.startPayment()
        assertEquals("pay-2", (vm.state as PixState.Waiting).charge.paymentId)
    }

    @Test
    fun localClockPastExpiry_expiresOnTick() = runBlocking {
        val vm = vm()
        vm.startPayment()
        now = T0 + THIRTY_MIN + 1
        vm.tick()

        assertEquals(PixState.Expired, vm.state)
        assertEquals(0, released)
    }

    @Test
    fun noInternetOnPoll_keepsWaitingWithNotice_alarmNotReleased() = runBlocking {
        val vm = vm()
        vm.startPayment()
        api.failWith = PixApiException.Kind.NETWORK
        vm.poll()

        val s = vm.state as PixState.Waiting
        assertNotNull(s.notice)
        assertEquals(0, released)

        // Voltou a internet: segue normalmente.
        api.failWith = null
        api.status = PixStatus.APPROVED
        vm.poll()
        assertEquals(PixState.Released, vm.state)
    }

    @Test
    fun noInternetOnCreate_failsWithMessage() = runBlocking {
        val vm = vm()
        api.failWith = PixApiException.Kind.NETWORK
        vm.startPayment()

        assertTrue(vm.state is PixState.Failed)
        assertNull(store.load())
        assertEquals(0, released)
    }

    @Test
    fun serverError_onCreate_failsWithMessage() = runBlocking {
        val vm = vm()
        api.failWith = PixApiException.Kind.SERVER
        vm.startPayment()

        assertTrue(vm.state is PixState.Failed)
    }

    @Test
    fun reopenApp_resumesSamePaymentId() = runBlocking {
        vm().startPayment()

        val reopened = vm()
        reopened.resumeIfPending()
        val s = reopened.state as PixState.Waiting
        assertEquals("pay-1", s.charge.paymentId)

        api.status = PixStatus.APPROVED
        reopened.poll()
        assertEquals(PixState.Released, reopened.state)
        assertEquals("pay-1", api.lastConsume?.first)
        assertEquals(1, api.createCalls)
    }

    @Test
    fun reopenAfterExpiry_showsExpired() = runBlocking {
        vm().startPayment()
        now = T0 + THIRTY_MIN + 5_000

        val reopened = vm()
        reopened.resumeIfPending()
        assertEquals(PixState.Expired, reopened.state)
        assertNull(store.load())
    }

    @Test
    fun resumeWithNothingPending_staysIdle() {
        val vm = vm()
        vm.resumeIfPending()
        assertEquals(PixState.Idle, vm.state)
    }

    @Test
    fun noAlarmId_doesNotCreateCharge() = runBlocking {
        val vm = vm(alarmId = null)
        vm.startPayment()

        assertTrue(vm.state is PixState.Failed)
        assertEquals(0, api.createCalls)
    }

    @Test
    fun startPaymentTwice_doesNotCreateSecondCharge() = runBlocking {
        val vm = vm()
        vm.startPayment()
        vm.startPayment()
        assertEquals(1, api.createCalls)
        assertFalse(vm.state is PixState.Failed)
    }

    @Test
    fun deviceId_isStable() {
        assertEquals(store.deviceId(), PendingPixStore(prefs).deviceId())
    }
}
