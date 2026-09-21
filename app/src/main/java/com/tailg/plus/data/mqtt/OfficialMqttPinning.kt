package com.tailg.plus.data.mqtt

import android.annotation.SuppressLint
import java.io.ByteArrayInputStream
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Certificate pinning for the official MQTT TLS broker.
 *
 * The official C18 broker serves a **private-CA self-signed** certificate
 * (captured from `www.tailgdd.com:6668`: `CN=c18_ex_base_pro.tailgdd.com`,
 * RSA-2048, valid 2023-09-11 → 2053-09-03, chain not verifiable), so platform
 * validation always fails. The official app works around this with a plain
 * trust-all `X509TrustManager`
 * (`com.tailg.run.intelligence.model.home.mqtt.miTM`), which in a Release build
 * is a man-in-the-middle hole.
 *
 * This client instead **pins** the official certificate's public key and accepts
 * a server certificate only when its SubjectPublicKeyInfo matches the pinned
 * key. There is deliberately **no** platform-trust fallback: Paho's `ssl://`
 * transport performs no hostname/endpoint verification, so accepting "any
 * platform-trusted chain" would let an attacker present a valid public-CA
 * certificate for any domain and bypass the pin entirely. Pinning the SPKI
 * (rather than the whole certificate) keeps the pin valid if the operator
 * re-issues the certificate with the same key pair.
 *
 * If another official broker serves a different key, add its DER to
 * [PINNED_CERT_DER_BASE64]; a host presenting an unpinned certificate is
 * rejected, and the caller degrades to the HTTP command path.
 */
internal object OfficialMqttPinning {

    /**
     * Base64 DER of the official broker certificate. SHA-256 of the DER:
     * `7ed944d07afebf76c0f72a8779da2800737f51777681b2c0589b7f4b2ee16438`.
     */
    private const val PINNED_CERT_DER_BASE64 =
        "MIIDIzCCAgsCFC9TVqrD0O7n9VC5aAZdRBEQUeu/MA0GCSqGSIb3DQEBCwUAMCYx" +
            "JDAiBgNVBAMMG2MxOF9leF9iYXNlX3Byby50YWlsZ2RkLmNvbTAgFw0yMzA5MTEw" +
            "ODU2MTRaGA8yMDUzMDkwMzA4NTYxNFowdDELMAkGA1UEBhMCQ04xEDAOBgNVBAgM" +
            "B0ppYW5nc3UxDTALBgNVBAcMBFd1eGkxDjAMBgNVBAoMBXRhaWxnMQ4wDAYDVQQL" +
            "DAV0YWlsZzEkMCIGA1UEAwwbYzE4X2V4X2Jhc2VfcHJvLnRhaWxnZGQuY29tMIIB" +
            "IjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAo1a3DVk6x1Qzo4sNW31xLTiC" +
            "/sv9RAOlzHpNJN7m1GrCM8duNGnWVOWgrg5+sF0RRXCiWTcKl8oGuenpJQyj+7qi" +
            "auq9PbgHuDMzBwbY9WAcrO3xxMZX8365hcEWR2KDx6dtW2OCvOy9+J9tHmCnCcp/" +
            "OciWDEV2Id655ZvgBR75CbraKI0v5fTmiWHtYYRNStQwia5G6bIeMCPyerzgom/A" +
            "tDS8pLh1dglvPvmbNyZfoWs7w2r30xoKfDobQGcWDht/nvZz1KepPFI8tzYgn8pP" +
            "C7BvHI5/gszeQtQ/un5KKMbpzPlyJb1KBWRPMmvXB/nI+6ymWtmGPznMwAgI+QID" +
            "AQABMA0GCSqGSIb3DQEBCwUAA4IBAQAol2vTf52BnJt5XahgwZkt6FfhtwTVnNfq" +
            "fzNaDhtRCMdIyJJLiIm1XdhGTP6znYV/oG9npPKPZ7DVIalxSeJQm9NiMfwLjrZ/" +
            "ISuewPoyCbXihb77cF4wpVmC+9Wp8Xgx829TDqZ7pmNZHZ8MKgwnXXrrdBclDUUC" +
            "1CKcyBdjQi3Q0UCrOKq6g+28nVmW3obSewK8Ig0V805syf8KYl+HJlozz2/CZeXQ" +
            "/w6Azcm/ohDTIWzA9FYoiorKO+7GG1/cKRnBmc+eqKbFZE6BWEJ9cQYFVhES1jw2" +
            "1O0htHqHGxp+rR0wfD/HZsHiUym+okWNMOSsh6dxCcgTYPYjPTNl"

    /** The pinned official certificate, parsed once. */
    val pinnedCertificate: X509Certificate by lazy {
        val der = Base64.getDecoder().decode(PINNED_CERT_DER_BASE64)
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
    }

    /** True when [cert] carries the same public key as the pinned certificate. */
    fun isPinned(cert: X509Certificate): Boolean =
        cert.publicKey.encoded.contentEquals(pinnedCertificate.publicKey.encoded)

    /**
     * [SSLSocketFactory] that accepts only the pinned official key. There is no
     * trust-all and no platform-trust fallback (see the class KDoc).
     */
    fun sslSocketFactory(): SSLSocketFactory {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(PinnedTrustManager()), SecureRandom())
        return context.socketFactory
    }
}

/**
 * [X509TrustManager] that accepts a server certificate only when its public key
 * matches the pinned official key. Everything else is rejected.
 *
 * The lint `CustomX509TrustManager` warning is suppressed deliberately: this
 * manager does not disable validation — it is stricter than the platform
 * default, and the platform default cannot be used because Paho does not verify
 * the endpoint hostname.
 */
@SuppressLint("CustomX509TrustManager")
internal class PinnedTrustManager : X509TrustManager {

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        // This client never presents a client certificate; fail closed.
        throw CertificateException("client certificate authentication is not supported")
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val leaf = chain.firstOrNull()
            ?: throw CertificateException("empty server certificate chain")
        try {
            leaf.checkValidity()
        } catch (e: java.security.cert.CertificateExpiredException) {
            throw CertificateException("pinned official certificate is expired", e)
        } catch (e: java.security.cert.CertificateNotYetValidException) {
            throw CertificateException("pinned official certificate is not yet valid", e)
        }
        if (!OfficialMqttPinning.isPinned(leaf)) {
            throw CertificateException(
                "server certificate does not match the pinned official key " +
                    "(subject=${leaf.subjectX500Principal.name})",
            )
        }
    }
    override fun getAcceptedIssuers(): Array<X509Certificate> =
        arrayOf(OfficialMqttPinning.pinnedCertificate)
}