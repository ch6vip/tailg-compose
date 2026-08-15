package com.tailg.plus.data.model

import java.time.Instant

/**
 * Port of `lib/models/official_cloud_message.dart`.
 *
 * NOT annotated as a Moshi DTO: the wire shape differs per factory
 * (`vehicle` reads `msgId`/`carProblemMessageRecordId`/`carProblemMessageInfoId`
 * and prefixes the id with `"vehicle:"`; `system` reads
 * `sysMessageRecordId`/`sysMessageInfoId`/`messageCode` and prefixes
 * `"system:"`), which a single generated adapter cannot express. Use the
 * companion factories, which replicate the Dart parsing exactly.
 *
 * `DateTime` → `Instant`; unparseable `sendTime` falls back to `Instant.EPOCH`,
 * matching Dart's `DateTime.fromMillisecondsSinceEpoch(0, isUtc: true)`.
 * `_fallbackId` uses [String.hashCode] (Kotlin/Java hash — a different
 * algorithm than Dart's `String.hashCode`, but the id is only a stable
 * per-content fallback key, which is the Dart intent).
 */
data class OfficialCloudMessage(
    val id: String,
    val title: String,
    val content: String,
    val time: Instant,
    val category: OfficialCloudMessageCategory,
    val messageCode: String = "",
    val carId: String = "",
    val url: String? = null,
) {
    companion object {
        fun vehicle(json: Map<String, Any?>): OfficialCloudMessage {
            val id = firstNonEmpty(
                listOf(json["msgId"], json["carProblemMessageRecordId"], json["carProblemMessageInfoId"]),
            )
            return OfficialCloudMessage(
                id = if (id.isEmpty()) fallbackId(json, "vehicle") else "vehicle:$id",
                title = clean(json["title"]) ?: "车辆消息",
                content = clean(json["content"]) ?: "",
                time = parseMessageTime(json["sendTime"]),
                category = OfficialCloudMessageCategory.VEHICLE,
                messageCode = clean(json["messageCode"]) ?: "",
                carId = clean(json["carId"]) ?: "",
            )
        }

        fun system(json: Map<String, Any?>): OfficialCloudMessage {
            val id = firstNonEmpty(
                listOf(json["sysMessageRecordId"], json["sysMessageInfoId"], json["messageCode"]),
            )
            return OfficialCloudMessage(
                id = if (id.isEmpty()) fallbackId(json, "system") else "system:$id",
                title = clean(json["title"]) ?: "系统消息",
                content = clean(json["content"] ?: json["description"]) ?: "",
                time = parseMessageTime(json["sendTime"]),
                category = OfficialCloudMessageCategory.SYSTEM,
                messageCode = clean(json["messageCode"]) ?: "",
                url = clean(json["url"]),
            )
        }

        private fun firstNonEmpty(values: List<Any?>): String {
            for (value in values) {
                val text = clean(value)
                if (text != null && text.isNotEmpty()) return text
            }
            return ""
        }

        private fun fallbackId(json: Map<String, Any?>, prefix: String): String {
            val title = clean(json["title"]) ?: ""
            val content = clean(json["content"]) ?: ""
            val sendTime = clean(json["sendTime"]) ?: ""
            return "$prefix:${title.hashCode()}_${content.hashCode()}_$sendTime"
        }

        private fun parseMessageTime(value: Any?): Instant {
            val text = clean(value)
            if (text == null) return Instant.EPOCH
            return parseDateTimeLenient(text.replaceFirst(" ", "T")) ?: Instant.EPOCH
        }

        private fun clean(value: Any?): String? {
            if (value == null) return null
            val text = value.toString().trim()
            if (text.isEmpty() || text == "--" || text.lowercase() == "null") return null
            return text
        }
    }
}

enum class OfficialCloudMessageCategory(val label: String) {
    VEHICLE("设备消息"),
    SYSTEM("系统消息"),
}
