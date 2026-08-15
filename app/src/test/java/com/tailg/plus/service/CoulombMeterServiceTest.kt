package com.tailg.plus.service

import android.bluetooth.BluetoothGattCharacteristic
import com.tailg.plus.data.ble.CommandCode
import com.tailg.plus.data.ble.platform.ConnectionManager
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Port-validation tests for [CoulombMeterService], mirroring
 * `tailg-ble-app/test/coulomb_meter_service_test.dart` (pure helpers) plus
 * hermetic BLE-path coverage for the guard clauses and the timeout fallback
 * using a mocked [ConnectionManager].
 */
@RunWith(RobolectricTestRunner::class)
class CoulombMeterServiceTest {

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(
        block: suspend () -> Unit,
    ): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw AssertionError("Expected ${T::class.simpleName} but got $e", e)
        }
        throw AssertionError("Expected ${T::class.simpleName} but nothing was thrown")
    }

    @Test
    fun isSupportedHidesLithium208() {
        assertFalse(CoulombMeterService.isSupported(modelType = 8, bmsTlvType = "208"))
        assertTrue(CoulombMeterService.isSupported(modelType = 8, bmsTlvType = "176"))
        assertTrue(CoulombMeterService.isSupported(modelType = 8, bmsTlvType = ""))
    }

    @Test
    fun parseSocVisibleReadsBit0OfStatusByte() {
        // Official setSocVisible: length 24, prefix D0010A08, status at [10..12).
        // D0010A08 (8) + pad (2) + status (2) + tail ...
        assertEquals(true, CoulombMeterService.parseSocVisible("D0010A08FF01000000000000"))
        assertEquals(false, CoulombMeterService.parseSocVisible("D0010A08FF00000000000000"))
        // wrong prefix / short => null (need power-on path)
        assertNull(CoulombMeterService.parseSocVisible("AABBCC"))
        assertNull(CoulombMeterService.parseSocVisible("B0010A080100"))
    }

    @Test
    fun commandFramesMatchOfficialConstants() {
        assertEquals("D0018A00", CoulombMeterService.QUERY_FRAME)
        assertEquals("D0018A020500", CoulombMeterService.TURN_ON_FRAME)
        assertEquals("D0018A020600", CoulombMeterService.TURN_OFF_FRAME)
    }

    @Test
    fun queryStatusThrowsWhenNotLoggedIn() = runTest {
        val cm = mockk<ConnectionManager>()
        every { cm.isProtocolLoggedIn } returns false
        val service = CoulombMeterService(connectionManager = cm)

        val ex = assertSuspendThrows<IllegalStateException> { service.queryStatus() }
        assertEquals("请先连接车辆蓝牙", ex.message)
    }

    @Test
    fun queryStatusThrowsWhenFbb2Unavailable() = runTest {
        val cm = mockk<ConnectionManager>()
        every { cm.isProtocolLoggedIn } returns true
        every { cm.fbb2Char } returns null
        val service = CoulombMeterService(connectionManager = cm)

        val ex = assertSuspendThrows<IllegalStateException> { service.queryStatus() }
        assertEquals("当前连接不支持库仑计通道 (FBB2)", ex.message)
    }

    @Test
    fun queryStatusReturnsNullOnTimeout() = runTest {
        val cm = mockk<ConnectionManager>()
        every { cm.isProtocolLoggedIn } returns true
        every { cm.fbb2Char } returns mockk<BluetoothGattCharacteristic>()
        every { cm.fbb2Flow } returns MutableSharedFlow()
        coEvery { cm.sendCommand(CommandCode.powerOn) } returns true
        coEvery { cm.writeFbb2(CoulombMeterService.QUERY_FRAME) } just Runs
        val service = CoulombMeterService(connectionManager = cm)

        val result = service.queryStatus()

        assertNull(result)
        coVerify { cm.sendCommand(CommandCode.powerOn) }
        coVerify { cm.writeFbb2(CoulombMeterService.QUERY_FRAME) }
    }

    @Test
    fun setEnabledReturnsParsedFrameResult() = runTest {
        val cm = mockk<ConnectionManager>()
        every { cm.isProtocolLoggedIn } returns true
        every { cm.fbb2Char } returns mockk<BluetoothGattCharacteristic>()
        val fbb2 = MutableSharedFlow<String>()
        every { cm.fbb2Flow } returns fbb2
        coEvery { cm.sendCommand(CommandCode.powerOn) } returns true
        // Firmware replies with a parseable ON frame as the write side effect.
        coEvery { cm.writeFbb2(any()) } answers { fbb2.tryEmit("D0010A08FF01000000000000") }
        val service = CoulombMeterService(connectionManager = cm)

        val result = service.setEnabled(true)

        assertEquals(true, result)
        coVerify { cm.writeFbb2(CoulombMeterService.TURN_ON_FRAME) }
    }
}
