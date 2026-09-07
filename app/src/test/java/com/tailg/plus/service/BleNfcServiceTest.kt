package com.tailg.plus.service

import com.tailg.plus.data.ble.platform.ConnectionManager
import com.tailg.plus.data.ble.platform.ProtocolType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleNfcServiceTest {
  @Test
  fun tlinkNfcCommandsRequireATlinkLogin() = runTest {
    val connection = mockk<ConnectionManager>()
    val service = BleNfcService(connection)
    every { connection.isProtocolLoggedIn } returns true
    for (protocol in listOf(ProtocolType.KKS, ProtocolType.QGJ)) {
      every { connection.protocol } returns protocol
      assertFalse(service.canWriteOfficialNfc)
      assertFalse(service.addCard("01"))
    }
    every { connection.protocol } returns ProtocolType.TLINK
    every { connection.isProtocolLoggedIn } returns false
    assertFalse(service.delNfc("01"))
    coVerify(exactly = 0) { connection.writeStandardHex(any()) }

    every { connection.isProtocolLoggedIn } returns true
    coEvery { connection.writeStandardHex("85054A3202020156789ABCDE") } returns true
    assertTrue(service.canWriteOfficialNfc)
    assertTrue(service.addCard("01"))
    coVerify(exactly = 1) { connection.writeStandardHex(any()) }
  }
}
