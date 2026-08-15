package com.tailg.plus.log

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class LogServiceTest {

    private val t0 = LocalDateTime.of(2024, 5, 1, 12, 0, 0)

    private fun service(): LogService = LogService(clock = { t0 })

    @Test
    fun operation_addsRedactedEntry() {
        val log = service()
        log.operation("登录成功 token=abc123def456")
        val entry = log.all.single()
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals(LogCategory.OPERATION, entry.category)
        assertEquals(t0, entry.time)
        assertEquals("登录成功 token=abc***456", entry.message)
        assertEquals(null, entry.detail)
    }

    @Test
    fun ble_defaultsToDebug() {
        val log = service()
        log.ble("连接断开")
        val entry = log.all.single()
        assertEquals(LogLevel.DEBUG, entry.level)
        assertEquals(LogCategory.BLE, entry.category)
    }

    @Test
    fun loginDetailBecomesByteSummary() {
        val log = service()
        log.operation("发送登录帧", detail = "AA BB CC DD")
        assertEquals("<redacted login frame, 4 bytes>", log.all.single().detail)
    }

    @Test
    fun nonLoginDetailIsRedacted() {
        val log = service()
        log.operation("同步里程", detail = "token=abc123def456")
        assertEquals("token=abc***456", log.all.single().detail)
    }

    @Test
    fun ringBufferEvictsOldest() {
        val log = service()
        for (i in 1..2001) log.operation("msg $i")
        assertEquals(2000, log.all.size)
        assertEquals(1, log.evictedCount)
        assertEquals("msg 2", log.all.first().message)
    }

    @Test
    fun byCategoryFilters() {
        val log = service()
        log.operation("a")
        log.ble("b")
        assertEquals(listOf("b"), log.byCategory(LogCategory.BLE).map { it.message })
        assertEquals(listOf("a"), log.byCategory(LogCategory.OPERATION).map { it.message })
    }

    @Test
    fun clearResetsBuffer() {
        val log = service()
        log.operation("a")
        for (i in 1..10) log.ble("b$i")
        log.clear()
        assertEquals(0, log.all.size)
        assertEquals(0, log.evictedCount)
    }
}
