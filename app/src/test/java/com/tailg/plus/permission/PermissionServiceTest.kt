package com.tailg.plus.permission

import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class PermissionServiceTest {

  @Test
  fun successfulResultUnregistersLauncher() = runTest {
    val registry = RecordingRegistry()
    val permission = "test.permission.BLUETOOTH"
    val request = async(start = CoroutineStart.UNDISPATCHED) {
      requestPermissionsWithRegistry(
        key = "permission-success",
        registry = registry,
        permissions = arrayOf(permission),
        fallbackGranted = { false },
      )
    }

    assertEquals(setOf("permission-success"), registry.activeCallbackKeys())
    assertTrue(registry.dispatchResult(registry.lastRequestCode, mapOf(permission to true)))
    assertEquals(mapOf(permission to true), request.await())
    assertTrue(registry.activeCallbackKeys().isEmpty())
  }

  @Test
  fun cancellationUnregistersLauncher() = runTest {
    val registry = RecordingRegistry()
    val request = async(start = CoroutineStart.UNDISPATCHED) {
      requestPermissionsWithRegistry(
        key = "permission-cancelled",
        registry = registry,
        permissions = arrayOf("test.permission.LOCATION"),
        fallbackGranted = { false },
      )
    }

    assertEquals(setOf("permission-cancelled"), registry.activeCallbackKeys())
    request.cancelAndJoin()
    runCurrent()
    assertTrue(registry.activeCallbackKeys().isEmpty())
  }

  @Test
  fun launchFailureUnregistersAndUsesFallback() = runTest {
    val registry = RecordingRegistry(throwOnLaunch = true)
    val permission = "test.permission.NOTIFICATION"
    val result = requestPermissionsWithRegistry(
      key = "permission-launch-failure",
      registry = registry,
      permissions = arrayOf(permission),
      fallbackGranted = { it == permission },
    )

    assertEquals(mapOf(permission to true), result)
    assertTrue(registry.activeCallbackKeys().isEmpty())
  }

  private class RecordingRegistry(
    private val throwOnLaunch: Boolean = false,
  ) : ActivityResultRegistry() {
    var lastRequestCode: Int = -1

    override fun <I, O> onLaunch(
      requestCode: Int,
      contract: ActivityResultContract<I, O>,
      input: I,
      options: ActivityOptionsCompat?,
    ) {
      lastRequestCode = requestCode
      if (throwOnLaunch) error("launch failed")
    }

    fun activeCallbackKeys(): Set<String> {
      val field = ActivityResultRegistry::class.java.getDeclaredField("keyToCallback")
      field.isAccessible = true
      val registrations = field.get(this) as Map<*, *>
      return registrations.keys.filterIsInstance<String>().toSet()
    }
  }
}
