package com.tailg.plus.data.mqtt

import com.tailg.plus.data.cloud.OfficialCloudApiException
import com.tailg.plus.data.model.OfficialVehicle
import java.net.URI
import java.security.SecureRandom
import kotlin.random.Random

enum class MqttTransportSecurity(
    val diagnosticLabel: String,
    val userLabel: String,
) {
    PLAINTEXT("plaintext-tcp", "明文 TCP"),
    TLS("tls", "TLS 加密"),
}

/**
 * Port of `lib/services/official_mqtt_config.dart`.
 * Official MQTT endpoints and topic/payload helpers from decompiled
 * `TailgHost` + `TailgMqttUtil` + `ControlFragment.mqttPublish`.
 */
object OfficialMqttConfig {
    /** Production KKS/YJ broker; the official protocol currently uses plaintext TCP. */
    const val KKS_YJ_HOST_URI = "tcp://www.tailgdd.com:1883"

    /** Production C18/QGJ/GPS broker (`TailgHost.MQTT_HOST_URL_LINE_C18`). */
    const val C18_HOST_URI = "ssl://www.tailgdd.com:6668"

    /** Default hardcoded MQTT credentials used by KKS/YJ. */
    const val USERNAME = "client_app"
    const val PASSWORD = "123456"

    /** Official ControlFragment uses qos = 0. */
    const val QOS = 0

    val CONNECT_TIMEOUT: kotlin.time.Duration = kotlin.time.Duration.parse("10s")
    const val KEEP_ALIVE_SECONDS = 60

    /** Preconnect retries after the first failed attempt (total = 1 + this). */
    const val PRECONNECT_MAX_RETRIES = 2

    /** Base delay for exponential backoff between preconnect attempts. */
    val PRECONNECT_RETRY_BASE_DELAY: kotlin.time.Duration =
        kotlin.time.Duration.parse("600ms")

    /** Whether this model uses the plain TCP KKS/YJ broker (no TLS). */
    fun usesKksYjBroker(modelType: Int?): Boolean = modelType == 1 || modelType == 2

    /**
     * Resolve broker URI for [vehicle].
     * Official: KKS/YJ always use fixed tcp host; others prefer vehicle
     * `mqHost:mqPort` when present, else C18 ssl host.
     */
    fun brokerUriFor(vehicle: OfficialVehicle): String {
        if (usesKksYjBroker(vehicle.modelType)) return KKS_YJ_HOST_URI
        val host = vehicle.mqHost.trim()
        val port = vehicle.mqPort.trim()
        if (host.isNotEmpty() && port.isNotEmpty()) {
            // Official builds "ssl://{mqHost}:{mqPort}" for non-KKS/YJ.
            return "ssl://$host:$port"
        }
        return C18_HOST_URI
    }

    /**
     * Resolve MQTT username/password for [vehicle].
     * Official ControlFragment:
     * - KKS/YJ keep the hardcoded `client_app` / `123456`
     * - C18/QGJ/GPS overwrite from `mqUsername`/`mqPassword` and refuse to
     *   connect when either is empty.
     */
    fun credentialsFor(vehicle: OfficialVehicle): Pair<String, String> {
        if (usesKksYjBroker(vehicle.modelType)) {
            return USERNAME to PASSWORD
        }
        val user = vehicle.mqUsername.trim()
        val pass = vehicle.mqPassword.trim()
        if (user.isEmpty() || pass.isEmpty()) {
            throw OfficialCloudApiException(
                "官方车辆未返回 MQTT 账号/密码，无法连接远程通道",
            )
        }
        return user to pass
    }

    /**
     * Official clientId:
     * - KKS/YJ: `app_{imei}{random3}`
     * - others: `app_{imeiGpsOrImei}_{uid}_android_{random3}`
     */
    fun clientIdFor(
        vehicle: OfficialVehicle,
        userId: String,
        random: Random = Random(SecureRandom().nextLong()),
    ): String {
        val suffix = buildString { repeat(3) { append(random.nextInt(10)) } }
        if (usesKksYjBroker(vehicle.modelType)) {
            val imei = kksImei(vehicle)
            return "app_$imei$suffix"
        }
        val imei = if (vehicle.commandImei.isNotEmpty()) vehicle.commandImei else vehicle.imei
        val uid = if (userId.trim().isEmpty()) "0" else userId.trim()
        return "app_${imei}_${uid}_android_$suffix"
    }

    /** Publish topic matching ControlFragment.mqttPublish. */
    fun publishTopic(vehicle: OfficialVehicle, imei: String): String {
        if (usesKksYjBroker(vehicle.modelType)) {
            val name = if (vehicle.modelType == 2) "yunjia" else "kks"
            return "app-update-$name/$imei"
        }
        return "APP_S/CMD/$imei"
    }

    /** Status subscribe topics (subset used after official connect). */
    fun subscribeTopics(vehicle: OfficialVehicle, imei: String): List<String> {
        if (usesKksYjBroker(vehicle.modelType)) {
            val name = if (vehicle.modelType == 2) "yunjia" else "kks"
            return listOf("$name-get-$imei")
        }
        return listOf(
            "S_APP/STATUS/$imei",
            "S_APP/OTA/$imei",
            "S_APP/CHARGER/$imei",
            "S_APP/CHECK/$imei",
        )
    }

    /** Official `MqttCmdBean` JSON: `{"imei":"...","command":"lock"}`. */
    fun commandPayload(imei: String, command: String): String =
        """{"imei":"$imei","command":"$command"}"""

    /** IMEI used in topic/payload for this vehicle (official `this.imei`). */
    fun commandImei(vehicle: OfficialVehicle): String {
        if (usesKksYjBroker(vehicle.modelType)) return kksImei(vehicle)
        // QGJ/C18/GPS paths typically bind MQTT on imeiGps when present.
        if (vehicle.imeiGps.isNotEmpty()) return vehicle.imeiGps
        return if (vehicle.commandImei.isNotEmpty()) vehicle.commandImei else vehicle.imei
    }

    private fun kksImei(vehicle: OfficialVehicle): String =
        if (vehicle.imei.isNotEmpty()) vehicle.imei else vehicle.commandImei

    data class BrokerUri(
        val security: MqttTransportSecurity,
        val host: String,
        val port: Int,
    ) {
        val secure: Boolean
            get() = security == MqttTransportSecurity.TLS

        val diagnosticLabel: String
            get() = security.diagnosticLabel
    }

    /**
     * Parse a Paho broker URI and reject malformed or unsupported endpoints.
     * Missing ports retain Paho's standard defaults; an explicitly malformed
     * port is never silently replaced with a default.
     */
    fun parseBrokerUri(uri: String): BrokerUri {
        val raw = uri.trim()
        require(raw.isNotEmpty()) { "broker URI 不能为空" }
        val parsed = try {
            URI(raw)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("broker URI 格式无效", e)
        }
        val scheme = parsed.scheme?.lowercase()
            ?: throw IllegalArgumentException("broker URI 缺少 scheme")
        val security = when (scheme) {
            "tcp", "ws" -> MqttTransportSecurity.PLAINTEXT
            "ssl", "wss" -> MqttTransportSecurity.TLS
            else -> throw IllegalArgumentException("不支持的 MQTT scheme: $scheme")
        }
        require(parsed.userInfo == null) { "broker URI 不允许 user-info" }
        require(parsed.rawPath.isNullOrEmpty()) { "broker URI 不允许 path" }
        require(parsed.rawQuery == null && parsed.rawFragment == null) {
            "broker URI 不允许 query 或 fragment"
        }

        val authority = parsed.rawAuthority?.trim().orEmpty()
        require(authority.isNotEmpty()) { "broker URI 缺少 host" }
        val host: String
        val explicitPort: String?
        if (authority.startsWith("[")) {
            val closingBracket = authority.indexOf(']')
            require(closingBracket > 1) { "IPv6 broker host 格式无效" }
            host = authority.substring(1, closingBracket)
            val suffix = authority.substring(closingBracket + 1)
            require(suffix.isEmpty() || suffix.startsWith(":")) {
                "broker URI host/port 格式无效"
            }
            explicitPort = suffix.drop(1).takeIf { suffix.isNotEmpty() }
        } else {
            val firstColon = authority.indexOf(':')
            val lastColon = authority.lastIndexOf(':')
            require(firstColon == lastColon) { "IPv6 broker host 必须使用方括号" }
            if (firstColon >= 0) {
                host = authority.substring(0, firstColon)
                explicitPort = authority.substring(firstColon + 1)
            } else {
                host = authority
                explicitPort = null
            }
        }
        require(host.isNotBlank()) { "broker URI 缺少 host" }

        val defaultPort = if (security == MqttTransportSecurity.TLS) 8883 else 1883
        val port = if (explicitPort == null) {
            defaultPort
        } else {
            require(explicitPort.isNotEmpty()) { "broker URI 缺少 port" }
            explicitPort.toIntOrNull()?.also {
                require(it in 1..65535) { "broker URI port 超出范围" }
            } ?: throw IllegalArgumentException("broker URI port 必须是数字")
        }
        return BrokerUri(security = security, host = host, port = port)
    }
}
