package com.tailg.plus.data.model

import com.squareup.moshi.JsonClass

/**
 * Port of `lib/models/official_smart_service_status.dart`.
 *
 * Wire DTO (`simQueryDetail` payload carries a `code` string) → Moshi adapter.
 * The Dart constants `expiredCode = '9'` / `cancelledCode = '7'` map to
 * [EXPIRED_CODE] / [CANCELLED_CODE].
 */
@JsonClass(generateAdapter = true)
data class OfficialSmartServiceStatus(
    val code: String = "",
) {
    val remoteControlBlockReason: String?
        get() = when (code) {
            EXPIRED_CODE -> "VIP智能服务已到期"
            CANCELLED_CODE -> "当前智能云盒已销号"
            else -> null
        }

    fun decisionForModelType(modelType: Int?): OfficialSmartServiceControlDecision {
        val message = remoteControlBlockReason
        if (message == null) {
            return OfficialSmartServiceControlDecision()
        }
        return when (modelType) {
            // ControlFragment's BB/default branch returns after the notice.
            3 -> OfficialSmartServiceControlDecision(
                message = message,
                blocksControl = true,
            )
            // These explicit switch branches show the notice, then still publish.
            8, 10, 14, 283, 401, 928, 2103, 2201 -> OfficialSmartServiceControlDecision(
                message = message,
                blocksControl = false,
            )
            // KKS/YJ do not consult simQueryDetail in their control branches.
            else -> OfficialSmartServiceControlDecision()
        }
    }

    companion object {
        const val EXPIRED_CODE = "9"
        const val CANCELLED_CODE = "7"

        fun fromPayload(payload: Any?): OfficialSmartServiceStatus {
            if (payload !is Map<*, *>) {
                return OfficialSmartServiceStatus()
            }
            return OfficialSmartServiceStatus(
                code = payload["code"]?.toString()?.trim() ?: "",
            )
        }
    }
}

data class OfficialSmartServiceControlDecision(
    val message: String? = null,
    val blocksControl: Boolean = false,
)
