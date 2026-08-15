package com.tailg.plus.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.data.model.VehicleLocation
import com.tailg.plus.data.model.VehicleProfile
import com.tailg.plus.data.model.VehicleProtocol
import com.tailg.plus.data.store.VehicleStore
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import com.tailg.plus.permission.AppPermissionService
import com.tailg.plus.permission.PermissionCheckResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Port-validation tests for [LocationService], mirroring
 * `tailg-ble-app/test/location_service_test.dart`. The geolocator plugin and
 * the permission service are replaced by mocks (the Dart test mocks the
 * platform channels the same way); the vehicle store is mocked so the
 * DataStore layer is not needed here.
 */
@RunWith(RobolectricTestRunner::class)
class LocationServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var store: VehicleStore
    private lateinit var permissions: AppPermissionService

    @Before
    fun setUp() {
        store = mockk()
        permissions = mockk()
        coEvery { store.init() } just Runs
        coEvery { store.updateLastLocation(any(), any()) } just Runs
        every { store.vehicles } returns emptyList()
        every { store.defaultVehicle } returns null
    }

    private fun service(
        clock: () -> Instant = { NOW },
        position: GeoPosition? = null,
        log: LogService = LogService(),
    ): LocationService = LocationService(
        context = context,
        vehicleStore = store,
        permissionService = permissions,
        logService = log,
        clock = clock,
        locationProvider = LocationProvider {
            position ?: error("no position stubbed")
        },
    )

    private suspend inline fun <reified T : Throwable> assertSuspendThrows(
        block: suspend () -> Unit,
    ): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw AssertionError("Expected ${T::class.simpleName} but got $e", e)
        }
        throw AssertionError("Expected ${T::class.simpleName} but nothing was thrown")
    }

    private fun vehicleWithLocation(recordedAt: Instant): VehicleProfile = VehicleProfile(
        id = "AA:BB:CC:DD:EE:FF",
        name = "默认车",
        protocol = VehicleProtocol.QGJ,
        createdAt = recordedAt,
        updatedAt = recordedAt,
        lastLocation = VehicleLocation(
            latitude = 31.2304,
            longitude = 121.4737,
            accuracy = 8.5,
            recordedAt = recordedAt,
        ),
    )

    @Test
    fun captureCurrentLocationConvertsPermissionDenialToDomainError() = runTest {
        coEvery { permissions.ensureLocationPermission(any(), any()) } returns
            PermissionCheckResult.denied("未授予定位权限")

        val ex = assertSuspendThrows<LocationCaptureException> {
            service().captureCurrentLocation()
        }
        assertEquals("未授予定位权限", ex.message)
    }

    @Test
    fun captureCurrentLocationMapsPlatformPositionWithInjectedClock() = runTest {
        val now = Instant.parse("2026-06-20T02:15:00Z")
        coEvery { permissions.ensureLocationPermission(any(), any()) } returns
            PermissionCheckResult.granted()

        val location = service(
            clock = { now },
            position = GeoPosition(22.5431, 114.0579, 6.25),
        ).captureCurrentLocation()

        assertEquals(22.5431, location.latitude, 1e-9)
        assertEquals(114.0579, location.longitude, 1e-9)
        assertEquals(6.25, location.accuracy, 1e-9)
        assertEquals(now, location.recordedAt)
    }

    @Test
    fun recordDefaultVehicleLocationInitializesVehicleStoreBeforeLookup() = runTest {
        val now = Instant.parse("2026-06-20T01:30:00Z")
        val vehicle = vehicleWithLocation(now)
        every { store.defaultVehicle } returns vehicle
        every { store.vehicles } returns listOf(vehicle)

        val location = service(clock = { now }).recordDefaultVehicleLocation()

        assertNotNull(location)
        assertEquals("31.230400, 121.473700", location!!.coordinateText)
        coVerify(atLeast = 1) { store.init() }
    }

    @Test
    fun recordVehicleLocationNormalizesIdsBeforeCachedThrottleLookup() = runTest {
        val now = Instant.parse("2026-06-20T01:30:00Z")
        val vehicle = vehicleWithLocation(now)
        every { store.vehicles } returns listOf(vehicle)

        val location = service(clock = { now }).recordVehicleLocation("  AA:BB:CC:DD:EE:FF  ")

        assertNotNull(location)
        assertEquals("31.230400, 121.473700", location!!.coordinateText)
    }

    @Test
    fun recordVehicleLocationIgnoresBlankIds() = runTest {
        val location = service().recordVehicleLocation("   ")

        assertNull(location)
        coVerify(exactly = 0) { store.init() }
    }

    @Test
    fun recordVehicleLocationHidesPermissionErrorsDuringSilentCapture() = runTest {
        val now = Instant.parse("2026-06-20T02:30:00Z")
        val stale = now.minusSeconds(120)
        val vehicle = vehicleWithLocation(stale)
        every { store.vehicles } returns listOf(vehicle)
        coEvery { permissions.ensureLocationPermission(any(), any()) } returns
            PermissionCheckResult.denied("未授予定位权限")

        val log = LogService()
        val location = service(clock = { now }, log = log)
            .recordVehicleLocation("AA:BB:CC:DD:EE:FF")

        assertNull(location)
        val entry = log.all.single { it.message == "记录车辆位置失败" }
        assertEquals("未授予定位权限", entry.detail)
        assertEquals(LogLevel.DEBUG, entry.level)
    }

    @Test
    fun recordVehicleLocationRethrowsPermissionErrorsForUserRequests() = runTest {
        val now = Instant.parse("2026-06-20T02:45:00Z")
        val vehicle = vehicleWithLocation(now)
        every { store.vehicles } returns listOf(vehicle)
        coEvery { permissions.ensureLocationPermission(any(), any()) } returns
            PermissionCheckResult.denied("未授予定位权限")

        val ex = assertSuspendThrows<LocationCaptureException> {
            service(clock = { now }).recordVehicleLocation(
                "AA:BB:CC:DD:EE:FF",
                requestPermission = true,
            )
        }
        assertEquals("未授予定位权限", ex.message)
    }

    @Test
    fun recordVehicleLocationPersistsANewlyCapturedPosition() = runTest {
        val now = Instant.parse("2026-06-20T03:00:00Z")
        val stale = now.minusSeconds(120)
        val vehicle = vehicleWithLocation(stale)
        every { store.vehicles } returns listOf(vehicle)
        coEvery { permissions.ensureLocationPermission(any(), any()) } returns
            PermissionCheckResult.granted()

        val location = service(
            clock = { now },
            position = GeoPosition(23.1291, 113.2644, 5.0),
        ).recordVehicleLocation("AA:BB:CC:DD:EE:FF", requestPermission = true)

        assertNotNull(location)
        assertEquals("23.129100, 113.264400", location!!.coordinateText)
        assertEquals(now, location.recordedAt)

        val captured = slot<VehicleLocation>()
        coVerify { store.updateLastLocation("AA:BB:CC:DD:EE:FF", capture(captured)) }
        assertEquals("23.129100, 113.264400", captured.captured.coordinateText)
    }

    companion object {
        private val NOW: Instant = Instant.parse("2026-06-20T00:00:00Z")
    }
}
