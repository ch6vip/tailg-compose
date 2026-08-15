package com.tailg.plus.data.cloud

import java.time.Duration
import java.time.LocalDateTime

/**
 * Port of `OfficialCloudApiResponse` / `OfficialCloudRequestSummary` from
 * `lib/services/official_cloud_api_client.dart`.
 *
 * [body] keeps the whole decoded envelope map (`code` / `msg` / `data` + any
 * extra top-level fields) exactly like the Dart `Map<String, dynamic>` so the
 * lenient parser semantics are preserved.
 */
data class OfficialCloudApiResponse(
    val statusCode: Int,
    /** Header names lower-cased, first value per name (Dart `headers`). */
    val headers: Map<String, String>,
    val body: Map<String, Any?>,
)

/** Last-request diagnostic summary surfaced by the service. */
data class OfficialCloudRequestSummary(
    val path: String,
    val method: String,
    val statusCode: Int?,
    val code: String?,
    val message: String?,
    val elapsed: Duration,
    val success: Boolean,
    val at: LocalDateTime,
)
