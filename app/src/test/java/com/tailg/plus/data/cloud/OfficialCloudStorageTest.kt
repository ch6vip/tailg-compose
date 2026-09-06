package com.tailg.plus.data.cloud

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.log.LogService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfficialCloudStorageTest {

  private class MemoryDataStore : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
      transform(data.value).also { data.value = it }
  }

  private fun failingSecurePrefs(): SharedPreferences {
    val editor = mockk<SharedPreferences.Editor>()
    every { editor.putString(any(), any()) } returns editor
    every { editor.remove(any()) } returns editor
    every { editor.commit() } returns false
    return mockk {
      every { edit() } returns editor
      every { getString(any(), any()) } answers { securePrefs.getString(firstArg(), secondArg()) }
    }
  }

  private lateinit var context: Context
  private lateinit var securePrefs: SharedPreferences
  private lateinit var storage: OfficialCloudStorage

  @Before
  fun setUp() = runTest {
    context = ApplicationProvider.getApplicationContext()
    securePrefs = context.getSharedPreferences(TEST_SECURE_PREFS, Context.MODE_PRIVATE)
    securePrefs.edit().clear().commit()
    storage = OfficialCloudStorage(
      context = context,
      log = LogService(),
      securePrefsFactory = { securePrefs },
    )
    storage.clearCredentialsAndSelection()
  }

  @After
  fun tearDown() = runTest {
    storage.clearCredentialsAndSelection()
    securePrefs.edit().clear().commit()
  }

  @Test
  fun failedCredentialWriteKeepsLegacyDataForRetry() = runTest {
    val dataStore = MemoryDataStore()
    dataStore.edit {
      it[stringPreferencesKey("official_cloud_token")] = "legacy-token"
      it[stringPreferencesKey("official_cloud_selected_vehicle")] = "legacy-vehicle"
      it[stringPreferencesKey("carControlInfo")] = "legacy-cache"
    }
    val previous = dataStore.data.value
    val failedStorage = OfficialCloudStorage(context, securePrefsFactory = { failingSecurePrefs() }, dataStore = dataStore)

    val failure = runCatching { failedStorage.saveCredentials("new-token", "phone", "user") }.exceptionOrNull()

    assertTrue(failure is IOException)
    assertEquals(previous, dataStore.data.value)
  }

  @Test
  fun failedCredentialMigrationKeepsTheSourceCredentials() = runTest {
    val dataStore = MemoryDataStore()
    dataStore.edit {
      it[stringPreferencesKey("official_cloud_token")] = "legacy-token"
      it[stringPreferencesKey("official_cloud_phone")] = "legacy-phone"
      it[stringPreferencesKey("official_cloud_user_id")] = "legacy-user"
    }
    val previous = dataStore.data.value
    val failedStorage = OfficialCloudStorage(context, securePrefsFactory = { failingSecurePrefs() }, dataStore = dataStore)

    val failure = runCatching { failedStorage.loadSession() }.exceptionOrNull()

    assertTrue(failure is IOException)
    assertEquals(previous, dataStore.data.value)
  }

  @Test
  fun failedVehicleMigrationKeepsTheSourceCache() = runTest {
    storage.saveCredentials("token", "phone", "user")
    val dataStore = MemoryDataStore()
    val cacheKey = stringPreferencesKey("carControlInfo")
    val payload = CloudJson.encode(OfficialVehicle(carId = "car", imei = "860000000000001").toJson())
    dataStore.edit { it[cacheKey] = payload }
    val failedStorage = OfficialCloudStorage(context, securePrefsFactory = { failingSecurePrefs() }, dataStore = dataStore)

    val failure = runCatching { failedStorage.loadSession() }.exceptionOrNull()

    assertTrue(failure is IOException)
    assertEquals(payload, dataStore.data.value[cacheKey])
  }

  @Test
  fun vehicleAndLogoutWritesReportCommitFailure() = runTest {
    val failedStorage = OfficialCloudStorage(context, securePrefsFactory = { failingSecurePrefs() }, dataStore = MemoryDataStore())

    assertTrue(runCatching { failedStorage.saveCarControlInfo(OfficialVehicle(carId = "car")) }.exceptionOrNull() is IOException)
    assertTrue(runCatching { failedStorage.clearCredentialsAndSelection() }.exceptionOrNull() is IOException)
  }

  @Test
  fun tokenLoginRetainsVerifiedVehicleAndProfileOnDisk() = kotlinx.coroutines.runBlocking {
    val vehicle = OfficialVehicle(carId = "car", imei = "860000000000001", frame = "frame")
    val api = mockk<OfficialCloudApiClientInterface>(relaxed = true)
    every { api.config } returns OfficialCloudApiConfig()
    coEvery { api.request(any(), any(), any(), any(), any()) } answers {
      val data: Any = if (firstArg<String>() == "app/centralControl/carStatus") {
        listOf(vehicle.toJson())
      } else {
        mapOf("id" to "verified-user", "nickName" to "test user")
      }
      OfficialCloudApiResponse(200, emptyMap(), mapOf("code" to 200, "data" to data))
    }
    val service = OfficialCloudService(
      storage, api, mockk(relaxed = true),
      scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher()),
    )
    try {
      service.loginWithToken("verified-token")
      val restored = storage.loadSession()

      assertEquals("verified-token", restored.token)
      assertEquals(vehicle.key, restored.selectedVehicleKey)
      assertEquals(vehicle.key, restored.cachedVehicles.single().key)
      assertEquals("verified-user", restored.cachedUserProfile?.id)
    } finally {
      service.dispose()
    }
  }

  @Test
  fun vehicleControlCacheLivesOnlyInSecurePreferences() = kotlinx.coroutines.runBlocking {
    // runBlocking, not runTest: loadSession() reads a real DataStore file and
    // its read-timeout guard must run on the real clock (runTest's virtual
    // time would fire the 5s timeout before the real IO resumes).
    val vehicle = OfficialVehicle.fromJson(
      mapOf(
        "imei" to "860000000000001",
        "carName" to "测试车辆",
        "mqUsername" to "vehicle-user",
        "mqPassword" to "vehicle-pass",
        "passwordInfo" to mapOf("main" to 123456),
      ),
    )
    storage.saveCredentials(token = "token", phone = "13800000000", userId = "42")
    storage.saveCarControlInfo(vehicle)

    val encryptedPayload = securePrefs.getString(SECURE_CAR_CONTROL_INFO, null)
    assertTrue(encryptedPayload!!.contains("vehicle-pass"))
    assertTrue(encryptedPayload.contains("passwordInfo"))

    val restored = storage.loadSession().cachedVehicles.single()
    assertEquals("vehicle-user", restored.mqUsername)
    assertEquals("vehicle-pass", restored.mqPassword)
    assertEquals(123456, restored.mainBlePassword)

    securePrefs.edit().remove(SECURE_CAR_CONTROL_INFO).commit()
    val withoutSecureCache = storage.loadSession()
    assertTrue(withoutSecureCache.cachedVehicles.isEmpty())
    assertNull(securePrefs.getString(SECURE_CAR_CONTROL_INFO, null))
  }

  private companion object {
    const val TEST_SECURE_PREFS = "official_cloud_storage_test_secure"
    const val SECURE_CAR_CONTROL_INFO = "official_cloud_car_control_info"
  }
}
