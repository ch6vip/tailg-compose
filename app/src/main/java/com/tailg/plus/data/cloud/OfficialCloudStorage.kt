package com.tailg.plus.data.cloud

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tailg.plus.data.model.OfficialUserProfile
import com.tailg.plus.data.model.OfficialVehicle
import com.tailg.plus.data.model.parsePersistedMap
import com.tailg.plus.log.LogLevel
import com.tailg.plus.log.LogService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.cloudDataStore by preferencesDataStore(name = "official_cloud")

/**
 * Restored session payload (Dart `_OfficialCloudStoredSession`).
 */
data class OfficialCloudStoredSession(
    val token: String,
    val phone: String,
    val userId: String,
    val selectedVehicleKey: String?,
    val cachedVehicles: List<OfficialVehicle>,
    val localVehicleLinks: Map<String, String>,
    val cachedUserProfile: OfficialUserProfile?,
)

/**
 * Port of `_OfficialCloudStorage` from `lib/services/official_cloud_storage.dart`.
 *
 * Storage split per the port conventions:
 * - credentials (token / phone / user id) → **EncryptedSharedPreferences**
 *   (androidx.security, AES256-GCM master key) — replaces FlutterSecureStorage
 * - everything else (selected vehicle, vehicle links, `carControlInfo` cache,
 *   cached user profile, legacy credential keys) → **DataStore Preferences**
 *   (replaces SharedPreferences), file `official_cloud`
 *
 * All secure-prefs access runs on [Dispatchers.IO]. JSON (de)serialization uses
 * [CloudJson] (Moshi). Key names are kept byte-for-byte from the Dart source,
 * including the bare `carControlInfo` key.
 */
class OfficialCloudStorage(
    private val context: Context,
    private val log: LogService = LogService(),
) {

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "official_cloud_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    suspend fun loadSession(): OfficialCloudStoredSession {
        val prefs = context.cloudDataStore.data.first()
        val credentials = withContext(Dispatchers.IO) { loadSecureCredentials(prefs) }
        val token = credentials.first
        return OfficialCloudStoredSession(
            token = token,
            phone = credentials.second,
            userId = credentials.third,
            selectedVehicleKey = prefs[KEY_SELECTED_VEHICLE],
            cachedVehicles = if (token.isEmpty()) {
                emptyList()
            } else {
                decodeCarControlInfo(prefs[KEY_CAR_CONTROL_INFO])
            },
            localVehicleLinks = decodeLinks(prefs[KEY_VEHICLE_LINKS]),
            cachedUserProfile = if (token.isEmpty()) {
                null
            } else {
                decodeUserProfile(prefs[KEY_USER_PROFILE])
            },
        )
    }

    suspend fun saveCredentials(token: String, phone: String, userId: String) {
        withContext(Dispatchers.IO) {
            securePrefs.edit().putString(KEY_SECURE_TOKEN, token).apply()
            securePrefs.edit().putString(KEY_SECURE_PHONE, phone).apply()
            if (userId.isEmpty()) {
                securePrefs.edit().remove(KEY_SECURE_USER_ID).apply()
            } else {
                securePrefs.edit().putString(KEY_SECURE_USER_ID, userId).apply()
            }
        }
        context.cloudDataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_PHONE)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_SELECTED_VEHICLE)
            prefs.remove(KEY_CAR_CONTROL_INFO)
            prefs.remove(KEY_USER_PROFILE)
        }
    }

    suspend fun clearCredentialsAndSelection() {
        withContext(Dispatchers.IO) {
            securePrefs.edit().remove(KEY_SECURE_TOKEN).apply()
            securePrefs.edit().remove(KEY_SECURE_PHONE).apply()
            securePrefs.edit().remove(KEY_SECURE_USER_ID).apply()
        }
        context.cloudDataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_PHONE)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_SELECTED_VEHICLE)
            prefs.remove(KEY_CAR_CONTROL_INFO)
            prefs.remove(KEY_USER_PROFILE)
        }
    }

    suspend fun saveSelectedVehicleKey(key: String?) {
        context.cloudDataStore.edit { prefs ->
            if (key == null) {
                prefs.remove(KEY_SELECTED_VEHICLE)
            } else {
                prefs[KEY_SELECTED_VEHICLE] = key
            }
        }
    }

    suspend fun saveCarControlInfo(vehicle: OfficialVehicle?) {
        context.cloudDataStore.edit { prefs ->
            if (vehicle == null) {
                prefs.remove(KEY_CAR_CONTROL_INFO)
            } else {
                prefs[KEY_CAR_CONTROL_INFO] = CloudJson.encode(vehicle.toJson())
            }
        }
    }

    suspend fun saveUserProfile(profile: OfficialUserProfile?) {
        context.cloudDataStore.edit { prefs ->
            if (profile == null) {
                prefs.remove(KEY_USER_PROFILE)
            } else {
                prefs[KEY_USER_PROFILE] = CloudJson.encode(profile.toJson())
            }
        }
    }

    suspend fun saveLinks(links: Map<String, String>) {
        context.cloudDataStore.edit { prefs ->
            prefs[KEY_VEHICLE_LINKS] = CloudJson.encode(links)
        }
    }

    // -- decode helpers -------------------------------------------------------

    private fun decodeUserProfile(raw: String?): OfficialUserProfile? {
        if (raw.isNullOrBlank()) return null
        try {
            val decoded = CloudJson.decode(raw)
            if (decoded !is Map<*, *>) return null
            val map = parsePersistedMap(decoded) ?: return null
            val profile = OfficialUserProfile.fromJson(map)
            return if (profile.hasDisplayName ||
                profile.avatarPath.trim().isNotEmpty() ||
                profile.id.trim().isNotEmpty()
            ) {
                profile
            } else {
                null
            }
        } catch (e: Exception) {
            log.operation(
                "官方用户资料缓存解析失败",
                detail = e.toString(),
                level = LogLevel.WARNING,
            )
            return null
        }
    }

    private suspend fun loadSecureCredentials(
        prefs: androidx.datastore.preferences.core.Preferences,
    ): Triple<String, String, String> {
        val secureToken = securePrefs.getString(KEY_SECURE_TOKEN, null)
        val securePhone = securePrefs.getString(KEY_SECURE_PHONE, null)
        val secureUserId = securePrefs.getString(KEY_SECURE_USER_ID, null)
        val legacyToken = prefs[KEY_TOKEN] ?: ""
        val legacyPhone = prefs[KEY_PHONE] ?: ""
        val legacyUserId = prefs[KEY_USER_ID] ?: ""
        val token = secureToken ?: legacyToken
        val phone = securePhone ?: legacyPhone
        val userId = secureUserId ?: legacyUserId
        if (legacyToken.isNotEmpty() || legacyPhone.isNotEmpty() || legacyUserId.isNotEmpty()) {
            if (token.isNotEmpty()) {
                securePrefs.edit().putString(KEY_SECURE_TOKEN, token).apply()
            }
            if (phone.isNotEmpty()) {
                securePrefs.edit().putString(KEY_SECURE_PHONE, phone).apply()
            }
            if (userId.isNotEmpty()) {
                securePrefs.edit().putString(KEY_SECURE_USER_ID, userId).apply()
            }
            context.cloudDataStore.edit { prefsEdit ->
                prefsEdit.remove(KEY_TOKEN)
                prefsEdit.remove(KEY_PHONE)
                prefsEdit.remove(KEY_USER_ID)
            }
            log.operation("官方云登录态已迁移到安全存储")
        }
        return Triple(token, phone, userId)
    }

    private fun decodeLinks(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        val decoded = decodeStoredJson(raw, "官云本地车辆关联数据损坏，已忽略")
            ?: return emptyMap()
        return decodeLinkPayload(decoded)
    }

    private fun decodeLinkPayload(decoded: Any?): Map<String, String> {
        if (decoded is Map<*, *>) {
            val stringMap = decoded.entries.associate { (key, value) ->
                key.toString() to value.toString()
            }
            return OfficialCloudVehicleLinks.normalize(stringMap)
        }
        log.operation(
            "官云本地车辆关联数据格式异常，已忽略",
            detail = "Expected JSON object, got ${decoded?.javaClass?.simpleName}",
            level = LogLevel.WARNING,
        )
        return emptyMap()
    }

    private fun decodeCarControlInfo(raw: String?): List<OfficialVehicle> {
        if (raw.isNullOrEmpty()) return emptyList()
        val decoded = decodeStoredJson(raw, "官云车辆控制缓存损坏，已忽略")
            ?: return emptyList()
        return decodeCachedVehicles(decoded)
    }

    private fun decodeStoredJson(raw: String, warningMessage: String): Any? {
        return try {
            CloudJson.decode(raw)
        } catch (e: Exception) {
            log.operation(
                warningMessage,
                detail = e.toString(),
                level = LogLevel.WARNING,
            )
            null
        }
    }

    private fun decodeCachedVehicles(decoded: Any?): List<OfficialVehicle> {
        val vehicles = OfficialCloudDataParser.vehicles(decoded)
        if (vehicles.isNotEmpty()) return vehicles
        log.operation(
            "官云车辆控制缓存无有效车辆，已忽略",
            detail = "type=${decoded?.javaClass?.simpleName}",
            level = LogLevel.WARNING,
        )
        return emptyList()
    }

    private companion object {
        // Secure (EncryptedSharedPreferences) keys.
        const val KEY_SECURE_TOKEN = "official_cloud_token"
        const val KEY_SECURE_PHONE = "official_cloud_phone"
        const val KEY_SECURE_USER_ID = "official_cloud_user_id"

        // DataStore keys (Dart SharedPreferences keys, kept byte-for-byte).
        val KEY_TOKEN = stringPreferencesKey("official_cloud_token")
        val KEY_PHONE = stringPreferencesKey("official_cloud_phone")
        val KEY_USER_ID = stringPreferencesKey("official_cloud_user_id")
        val KEY_SELECTED_VEHICLE = stringPreferencesKey("official_cloud_selected_vehicle")
        val KEY_VEHICLE_LINKS = stringPreferencesKey("official_cloud_vehicle_links")
        val KEY_CAR_CONTROL_INFO = stringPreferencesKey("carControlInfo")
        val KEY_USER_PROFILE = stringPreferencesKey("official_cloud_user_profile")
    }
}
