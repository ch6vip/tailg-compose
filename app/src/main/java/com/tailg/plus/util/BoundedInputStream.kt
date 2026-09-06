package com.tailg.plus.util

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Enforce the cap while reading, including responses with no Content-Length. */
internal fun InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    require(maxBytes >= 0)
    val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val remaining = maxBytes.toLong() - output.size() + 1
        val count = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (count < 0) return output.toByteArray()
        if (count == 0) continue
        if (output.size().toLong() + count > maxBytes) {
            throw IOException("Response exceeds $maxBytes bytes")
        }
        output.write(buffer, 0, count)
    }
}
