package com.tailg.plus.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * Port of `lib/services/permission_service.dart` (AppPermissionService).
 * permission_handler + geolocator → AndroidX Activity Result API.
 */
data class PermissionCheckResult(
    val granted: Boolean,
    val message: String? = null,
    /** True when the user must open system settings (permanent deny). */
    val openSettingsRecommended: Boolean = false,
) {
    companion object {
        fun granted() = PermissionCheckResult(granted = true)
        fun denied(message: String, openSettingsRecommended: Boolean = false) =
            PermissionCheckResult(
                granted = false,
                message = message,
                openSettingsRecommended = openSettingsRecommended,
            )
    }
}

/**
 * Android permission facade. Keep the instance app-scoped (Hilt provides it);
 * request methods need a [ComponentActivity] and must be called on the main
 * thread while the activity lifecycle is CREATED+ (launcher registration rule).
 */
class AppPermissionService(private val context: Context) {

    private val blePermissions: Array<String>
        get() = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }.toTypedArray()

    private val notificationPermission: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    /** Check-only (never prompts). */
    fun checkBleScanPermissions(): PermissionCheckResult {
        val denied = blePermissions.any { !isGranted(it) }
        if (denied) return PermissionCheckResult.denied("请授予蓝牙和定位权限后再扫描")
        return PermissionCheckResult.granted()
    }

    /**
     * BLE scan/connect permissions (Android 12+ Scan/Connect + location for
     * older stacks / OEM scan requirements). [request] = false only checks.
     */
    suspend fun requestBleScanPermissions(
        activity: ComponentActivity,
        request: Boolean = true,
    ): PermissionCheckResult {
        if (!request) return checkBleScanPermissions()
        val result = requestPermissions(activity, blePermissions)
        val permanentlyBlocked = result.isPermanentlyBlocked(activity, blePermissions)
        val blocked = permanentlyBlocked || result.values.any { !it }
        if (blocked) {
            return PermissionCheckResult.denied(
                if (permanentlyBlocked) "蓝牙/定位权限被永久拒绝，请到系统设置开启后重试" else "请授予蓝牙和定位权限后再扫描",
                openSettingsRecommended = permanentlyBlocked,
            )
        }
        return PermissionCheckResult.granted()
    }

    /**
     * Port of Dart `ensureLocationPermission({required bool request})`.
     *
     * Checks the device location service, then the app location permission;
     * when [request] is true and the permission is denied, prompts via the
     * activity result launcher. [activity] is only touched on the request and
     * permanent-denial paths, so check-only calls may pass null.
     *
     * Deviation from Dart: the geolocator plugin hides the activity plumbing;
     * here the caller must supply the [activity] when [request] is true.
     */
    suspend fun ensureLocationPermission(
        activity: ComponentActivity?,
        request: Boolean,
    ): PermissionCheckResult {
        if (!isLocationServiceEnabled()) {
            return PermissionCheckResult.denied("定位服务未开启", openSettingsRecommended = true)
        }
        var granted = LOCATION_PERMISSIONS.any { isGranted(it) }
        if (!granted && request) {
            val target =
                requireNotNull(activity) { "activity is required to request location permission" }
            val result = requestPermissions(target, LOCATION_PERMISSIONS)
            granted = result.values.any { it }
        }
        if (!granted) {
            val permanently = request &&
                activity?.shouldShowRequestPermissionRationale(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ) == false
            return PermissionCheckResult.denied(
                if (permanently) "定位权限已被永久拒绝，请到系统设置开启" else "未授予定位权限",
                openSettingsRecommended = permanently,
            )
        }
        return PermissionCheckResult.granted()
    }

    private fun isLocationServiceEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    /** Notification permission (background induction needs it). */
    suspend fun requestNotificationPermission(
        activity: ComponentActivity,
        request: Boolean = true,
    ): PermissionCheckResult {
        val permission = notificationPermission ?: return PermissionCheckResult.granted()
        if (!request) {
            return if (isGranted(permission)) PermissionCheckResult.granted()
            else PermissionCheckResult.denied("后台感应需要通知权限")
        }
        val result = requestPermissions(activity, arrayOf(permission))
        if (result[permission] == true) return PermissionCheckResult.granted()
        val permanently = result.isPermanentlyBlocked(activity, arrayOf(permission))
        return PermissionCheckResult.denied(
            if (permanently) "通知权限被永久拒绝，请到系统设置开启后重试" else "后台感应需要通知权限",
            openSettingsRecommended = permanently,
        )
    }

    /** Opens system app settings so the user can re-grant BLE/location. */
    fun openSystemSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun Map<String, Boolean>.isPermanentlyBlocked(
        activity: ComponentActivity,
        permissions: Array<String>,
    ): Boolean = permissions.any {
        !isGranted(it) && !activity.shouldShowRequestPermissionRationale(it)
    }

    private suspend fun requestPermissions(
        activity: ComponentActivity,
        permissions: Array<String>,
    ): Map<String, Boolean> = requestPermissionsWithRegistry(
        key = "tailg_permission_${requestCounter.incrementAndGet()}",
        registry = activity.activityResultRegistry,
        permissions = permissions,
        fallbackGranted = ::isGranted,
    )

    companion object {
        private val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        private val requestCounter = java.util.concurrent.atomic.AtomicInteger()
    }
}

internal suspend fun requestPermissionsWithRegistry(
    key: String,
    registry: ActivityResultRegistry,
    permissions: Array<String>,
    fallbackGranted: (String) -> Boolean,
): Map<String, Boolean> = suspendCancellableCoroutine { cont ->
    var launcher: ActivityResultLauncher<Array<String>>? = null
    val unregister: () -> Unit = {
        try {
            launcher?.unregister()
        } catch (e: Exception) {
            Timber.w(e, "permission launcher unregister failed")
        }
        launcher = null
    }
    val registeredLauncher = registry.register(
        key,
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        unregister()
        if (cont.isActive) cont.resume(result)
    }
    launcher = registeredLauncher
    cont.invokeOnCancellation { unregister() }
    try {
        registeredLauncher.launch(permissions)
    } catch (e: Exception) {
        Timber.w(e, "permission launcher launch failed")
        unregister()
        if (cont.isActive) {
            cont.resume(permissions.associateWith(fallbackGranted))
        }
    }
}
