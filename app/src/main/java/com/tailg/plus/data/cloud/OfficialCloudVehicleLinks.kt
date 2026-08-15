package com.tailg.plus.data.cloud

/**
 * Port of `OfficialCloudVehicleLinks` from `lib/services/official_cloud_vehicle_links.dart`.
 *
 * Pure link-map policy (P1-5): one official key → one local device id; linking a
 * local id that is already bound to another official key replaces that mapping
 * (switching cars wins); an empty local id unlinks the official key.
 */
object OfficialCloudVehicleLinks {

    fun normalize(links: Map<String, String>): Map<String, String> {
        val next = linkedMapOf<String, String>()
        for ((key, value) in links) {
            val officialKey = key.trim()
            val localId = value.trim()
            if (officialKey.isEmpty() || localId.isEmpty()) continue
            next[officialKey] = localId
        }
        return next
    }

    fun link(
        links: Map<String, String>,
        officialVehicleKey: String,
        localVehicleId: String,
    ): Map<String, String> {
        val key = officialVehicleKey.trim()
        val localId = localVehicleId.trim()
        val next = normalize(links).toMutableMap()
        if (key.isEmpty()) return next
        if (localId.isEmpty()) {
            next.remove(key)
            return next
        }
        // Drop any other official keys pointing at the same local id.
        next.entries.removeAll { (officialKey, linkedLocalId) ->
            officialKey != key && linkedLocalId == localId
        }
        next[key] = localId
        return next
    }

    fun unlink(links: Map<String, String>, officialVehicleKey: String): Map<String, String> {
        val next = normalize(links).toMutableMap()
        next.remove(officialVehicleKey.trim())
        return next
    }

    fun prune(
        links: Map<String, String>,
        validLocalVehicleIds: Set<String>,
    ): Map<String, String> {
        val validIds = validLocalVehicleIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val next = linkedMapOf<String, String>()
        for ((officialKey, localId) in normalize(links)) {
            if (localId !in validIds) continue
            next[officialKey] = localId
        }
        return next
    }

    fun isLinkedTo(
        links: Map<String, String>,
        officialVehicleKey: String,
        localVehicleId: String,
    ): Boolean {
        val key = officialVehicleKey.trim()
        val localId = localVehicleId.trim()
        if (key.isEmpty() || localId.isEmpty()) return false
        return normalize(links)[key] == localId
    }
}
