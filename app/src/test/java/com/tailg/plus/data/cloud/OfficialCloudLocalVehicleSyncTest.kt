package com.tailg.plus.data.cloud

import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.model.VehicleProfile
import com.tailg.plus.data.model.VehicleProtocol
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OfficialCloudLocalVehicleSyncTest {
    @Test
    fun lateLocalSyncCannotRevertANewerVehicleSelection() = runTest {
        val first = OfficialVehicle(carId = "a", btmac = "AA:BB:CC:DD:EE:01")
        val second = OfficialVehicle(carId = "b", btmac = "AA:BB:CC:DD:EE:02")
        val releaseFirst = CompletableDeferred<Unit>()
        val local = object : OfficialCloudVehicleStore {
            override val vehicles = mutableListOf<VehicleProfile>()
            var selected: String? = null
            override suspend fun init() = Unit
            override suspend fun setDefault(id: String) { selected = id }
            override suspend fun upsert(id: String, name: String, protocol: VehicleProtocol, makeDefault: Boolean): VehicleProfile {
                if (id == first.normalizedDeviceMac) releaseFirst.await()
                val profile = VehicleProfile(id, name, protocol, Instant.EPOCH, Instant.EPOCH)
                vehicles.add(profile)
                if (makeDefault) selected = id
                return profile
            }
        }
        val api = mockk<OfficialCloudApiClientInterface>(relaxed = true)
        coEvery { api.request(any(), any(), any(), any(), any()) } coAnswers { awaitCancellation() }
        val service = OfficialCloudService(mockk(relaxed = true), api, local, scope = backgroundScope)
        service.setStateForTest(OfficialCloudState.initial().copyWith(
            initialized = true, token = "session", vehicles = listOf(first, second), selectedVehicleKey = first.key,
        ))
        val oldSync = async { service.applySelectedVehicleToLocalProfile() }
        testScheduler.runCurrent()
        val selection = async { service.selectVehicle(second) }
        testScheduler.runCurrent()
        releaseFirst.complete(Unit)
        oldSync.await()
        selection.await()

        assertEquals(second.key, service.currentState.selectedVehicle?.key)
        assertEquals(second.normalizedDeviceMac, local.selected)
        assertEquals(mapOf(first.key to first.normalizedDeviceMac, second.key to second.normalizedDeviceMac),
            service.currentState.localVehicleLinks)
    }

    @Test
    fun concurrentLinkWritesPreserveBothAssociationsOnDiskAndInMemory() = runTest {
        val storage = mockk<OfficialCloudStorage>(relaxed = true)
        val releaseFirst = CompletableDeferred<Unit>()
        var persisted = emptyMap<String, String>()
        coEvery { storage.saveLinks(any()) } coAnswers {
            val links = arg<Map<String, String>>(0)
            if (links.keys == setOf("a")) releaseFirst.await()
            persisted = links.toMap()
        }
        val service = OfficialCloudService(storage, mockk(relaxed = true), mockk(relaxed = true), scope = backgroundScope)
        val first = async { service.linkLocalVehicle("a", "local-a") }
        testScheduler.runCurrent()
        val second = async { service.linkLocalVehicle("b", "local-b") }
        testScheduler.runCurrent()
        releaseFirst.complete(Unit)
        first.await()
        second.await()

        val expected = mapOf("a" to "local-a", "b" to "local-b")
        assertEquals(expected, persisted)
        assertEquals(expected, service.currentState.localVehicleLinks)
    }

    @Test
    fun unlinkQueuedBehindAnotherLinkUsesTheLatestSnapshot() = runTest {
        val storage = mockk<OfficialCloudStorage>(relaxed = true)
        val releaseFirst = CompletableDeferred<Unit>()
        coEvery { storage.saveLinks(any()) } coAnswers {
            if (arg<Map<String, String>>(0).containsKey("a")) releaseFirst.await()
        }
        val service = OfficialCloudService(storage, mockk(relaxed = true), mockk(relaxed = true), scope = backgroundScope)
        val first = async { service.linkLocalVehicle("a", "local-a") }
        testScheduler.runCurrent()
        val remove = async { service.unlinkLocalVehicle("a") }
        testScheduler.runCurrent()
        releaseFirst.complete(Unit)
        first.await()
        remove.await()

        assertEquals(emptyMap<String, String>(), service.currentState.localVehicleLinks)
    }
}
