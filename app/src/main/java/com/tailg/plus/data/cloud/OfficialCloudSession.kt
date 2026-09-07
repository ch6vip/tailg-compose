package com.tailg.plus.data.cloud

/** A token may be reused after logout; the generation identifies one local login. */
internal data class OfficialCloudSession(val token: String, val generation: Long) {
    override fun toString(): String = "OfficialCloudSession(generation=$generation)"

    fun matches(state: OfficialCloudState): Boolean =
        token.isNotEmpty() && token == state.token && generation == state.sessionGeneration

    fun resourceKey(resource: String): OfficialCloudResourceKey = OfficialCloudResourceKey(generation, resource)
}

internal data class OfficialCloudResourceKey(val sessionGeneration: Long, val resource: String)
