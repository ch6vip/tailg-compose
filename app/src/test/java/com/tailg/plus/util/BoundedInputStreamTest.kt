package com.tailg.plus.util

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedInputStreamTest {
    @Test
    fun unknownLengthStreamStopsAtTheLimitWithoutReadingTheWholeBody() {
        var bytesRead = 0
        val endless = object : InputStream() {
            override fun read(): Int { bytesRead++; return 0 }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                bytesRead += length
                return length
            }
        }
        assertThrows(IOException::class.java) { endless.readBytesLimited(1024) }
        assertEquals(1025, bytesRead)
    }

    @Test
    fun completeBodyAtTheLimitIsPreserved() {
        val bytes = ByteArray(1024) { it.toByte() }
        assertArrayEquals(bytes, ByteArrayInputStream(bytes).readBytesLimited(1024))
        assertArrayEquals(byteArrayOf(), ByteArrayInputStream(byteArrayOf()).readBytesLimited(0))
    }
}
