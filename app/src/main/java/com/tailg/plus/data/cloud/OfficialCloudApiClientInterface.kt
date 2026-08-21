package com.tailg.plus.data.cloud

/**
 * Interface for the official cloud API client, enabling test doubles without
 * override fields in [OfficialCloudService].
 */
interface OfficialCloudApiClientInterface {
    val config: OfficialCloudApiConfig
    val lastRequest: OfficialCloudRequestSummary?

    suspend fun request(
        path: String,
        method: String,
        token: String? = null,
        body: Map<String, Any?>? = null,
        retryPolicy: OfficialCloudRetryPolicy = OfficialCloudRetryPolicy.TRANSPORT_ONLY,
    ): OfficialCloudApiResponse

    fun dispose()
}