package com.tailg.plus.data.cloud

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.log.LogService
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
  fun vehicleControlCacheLivesOnlyInSecurePreferences() = runTest {
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
