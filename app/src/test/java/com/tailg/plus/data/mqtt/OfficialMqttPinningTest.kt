package com.tailg.plus.data.mqtt

import io.mockk.every
import io.mockk.mockk
import java.security.MessageDigest
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the official MQTT certificate pinning: the pinned certificate is the
 * expected official broker cert, and the trust manager accepts ONLY that key —
 * never an arbitrary (even publicly-trusted) certificate.
 */
class OfficialMqttPinningTest {

    @Test
    fun pinnedCertificateIsTheOfficialBrokerCertificate() {
        val cert = OfficialMqttPinning.pinnedCertificate
        assertTrue(
            "unexpected subject: ${cert.subjectX500Principal.name}",
            cert.subjectX500Principal.name.contains("c18_ex_base_pro.tailgdd.com"),
        )
        // Fingerprint guard: a corrupted base64 that still parses to a same-CN
        // certificate would otherwise pass unnoticed.
        val sha256 = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        assertEquals(
            "7ed944d07afebf76c0f72a8779da2800737f51777681b2c0589b7f4b2ee16438",
            sha256,
        )
    }

    @Test
    fun pinnedCertificateMatchesItself() {
        assertTrue(OfficialMqttPinning.isPinned(OfficialMqttPinning.pinnedCertificate))
    }

    @Test
    fun aDifferentPublicKeyIsNotPinned() {
        assertFalse(OfficialMqttPinning.isPinned(certificateWithKey(byteArrayOf(1, 2, 3, 4))))
    }

    @Test
    fun trustManagerAcceptsThePinnedCertificate() {
        PinnedTrustManager().checkServerTrusted(arrayOf(OfficialMqttPinning.pinnedCertificate), "EC")
    }

    @Test
    fun trustManagerRejectsAnyUnpinnedCertificate() {
        // Includes a certificate a platform trust store would accept: Paho does no
        // hostname verification, so only the pin may ever be accepted.
        assertThrows(CertificateException::class.java) {
            PinnedTrustManager().checkServerTrusted(arrayOf(certificateWithKey(byteArrayOf(7, 7, 7))), "EC")
        }
    }

    @Test
    fun trustManagerRejectsAnEmptyChain() {
        assertThrows(CertificateException::class.java) {
            PinnedTrustManager().checkServerTrusted(emptyArray(), "EC")
        }
    }

    @Test
    fun trustManagerFailsClosedForClientCertificates() {
        assertThrows(CertificateException::class.java) {
            PinnedTrustManager().checkClientTrusted(arrayOf(OfficialMqttPinning.pinnedCertificate), "EC")
        }
    }

    @Test
    fun trustManagerRejectsExpiredPinnedCertificate() {
        val expired = mockk<X509Certificate>()
        every { expired.publicKey } returns OfficialMqttPinning.pinnedCertificate.publicKey
        every { expired.subjectX500Principal } returns OfficialMqttPinning.pinnedCertificate.subjectX500Principal
        every { expired.checkValidity() } throws java.security.cert.CertificateExpiredException("expired")
        assertThrows(CertificateException::class.java) {
            PinnedTrustManager().checkServerTrusted(arrayOf(expired), "EC")
        }
    }

    private fun certificateWithKey(encoded: ByteArray): X509Certificate {
        val key = mockk<PublicKey>()
        every { key.encoded } returns encoded
        val principal = mockk<X500Principal>()
        every { principal.name } returns "CN=other"
        val cert = mockk<X509Certificate>()
        every { cert.publicKey } returns key
        every { cert.subjectX500Principal } returns principal
        every { cert.checkValidity() } returns Unit
        return cert
    }
}