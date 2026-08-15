package com.tailg.plus.data.mqtt

import com.tailg.plus.data.cloud.OfficialCloudApiException
import com.tailg.plus.data.model.OfficialVehicle
import java.security.SecureRandom
import kotlin.random.Random

/**
 * Port of `lib/services/official_mqtt_config.dart`.
 * Official MQTT endpoints and topic/payload helpers from decompiled
 * `TailgHost` + `TailgMqttUtil` + `ControlFragment.mqttPublish`.
 */
object OfficialMqttConfig {
    /** Production KKS/YJ broker (`TailgHost.MQTT_HOST_URL_LINE_KKS_YJ`). */
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

    /** Whether this model uses the plain TCP KKS/YJ broker (no SSL). */
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

    data class BrokerUri(val secure: Boolean, val host: String, val port: Int)

    /** Parse `tcp://host:port` / `ssl://host:port` into parts. */
    fun parseBrokerUri(uri: String): BrokerUri {
        val raw = uri.trim()
        val secure = raw.startsWith("ssl://") || raw.startsWith("wss://")
        val withoutScheme = raw
            .replace(Regex("^(tcp|ssl|ws|wss)://"), "")
            .trim()
        val parts = withoutScheme.split(":")
        val host = parts.first()
        val port = if (parts.size > 1) {
            parts[1].toIntOrNull() ?: if (secure) 8883 else 1883
        } else {
            if (secure) 8883 else 1883
        }
        return BrokerUri(secure = secure, host = host, port = port)
    }
}
