package com.tailg.plus.data.model

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class OfficialCloudMessageTest {
  @Test
  fun messagesPreserveLocalWallClockTimeAndExplicitOffsets() {
    val previous = TimeZone.getDefault()
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
      val expected = Instant.parse("2026-09-07T00:30:00Z")
      for (sendTime in listOf("2026-09-07 08:30:00", "2026-09-07T08:30:00+08:00", "2026-09-07T00:30:00Z")) {
        assertEquals(expected, OfficialCloudMessage.vehicle(mapOf("sendTime" to sendTime)).time)
        assertEquals(expected, OfficialCloudMessage.system(mapOf("sendTime" to sendTime)).time)
      }
    } finally {
      TimeZone.setDefault(previous)
    }
  }

  @Test
  fun persistedNaiveDatesKeepTheirUtcSemanticsAndAcceptSpaceSeparators() {
    val expected = Instant.parse("2026-09-07T08:30:00Z")
    assertEquals(expected, parsePersistedDate("2026-09-07 08:30:00"))
    assertEquals(expected, parsePersistedDate("2026-09-07T08:30:00"))
    assertEquals(Instant.EPOCH, OfficialCloudMessage.vehicle(mapOf("sendTime" to "invalid")).time)
  }
}
