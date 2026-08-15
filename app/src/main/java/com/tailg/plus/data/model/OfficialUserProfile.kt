package com.tailg.plus.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Port of `lib/models/official_user_profile.dart`.
 *
 * Official user profile from `POST app/getUserProfile`
 * (decompiled `UserInfoBean` — nickName / avatar / signature etc.).
 *
 * Wire DTO → Moshi adapter. The wire key for [avatarPath] is `avatar_path`
 * (Dart `toJson()` writes `avatar_path` too); [fromJson] also accepts the
 * `avatarPath` / `birthDay` alternates like the Dart factory.
 */
@JsonClass(generateAdapter = true)
data class OfficialUserProfile(
    val id: String = "",
    val nickName: String = "",
    val name: String = "",
    val signature: String = "",
    val avatarName: String = "",
    @Json(name = "avatar_path") val avatarPath: String = "",
    val gender: String = "",
    val birthday: String = "",
    val obsAvatarId: String = "",
    val province: String = "",
    val city: String = "",
    val area: String = "",
    val address: String = "",
    @Json(ignore = true) val raw: Map<String, Any?> = emptyMap(),
) {
    /** Prefer social nick, then real name. */
    val displayName: String
        get() {
            val nick = nickName.trim()
            if (nick.isNotEmpty()) return nick
            val real = name.trim()
            if (real.isNotEmpty()) return real
            return ""
        }

    val avatarUrl: String?
        get() {
            val path = avatarPath.trim()
            if (path.isEmpty()) return null
            // Dart checks the http(s) prefix but returns `path` either way.
            return path
        }

    val hasDisplayName: Boolean get() = displayName.isNotEmpty()

    fun toJson(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "nickName" to nickName,
        "name" to name,
        "signature" to signature,
        "avatarName" to avatarName,
        "avatar_path" to avatarPath,
        "gender" to gender,
        "birthday" to birthday,
        "obsAvatarId" to obsAvatarId,
        "province" to province,
        "city" to city,
        "area" to area,
        "address" to address,
    )

    fun copyWith(
        id: String? = null,
        nickName: String? = null,
        name: String? = null,
        signature: String? = null,
        avatarName: String? = null,
        avatarPath: String? = null,
        gender: String? = null,
        birthday: String? = null,
        obsAvatarId: String? = null,
        province: String? = null,
        city: String? = null,
        area: String? = null,
        address: String? = null,
        raw: Map<String, Any?>? = null,
    ): OfficialUserProfile = OfficialUserProfile(
        id = id ?: this.id,
        nickName = nickName ?: this.nickName,
        name = name ?: this.name,
        signature = signature ?: this.signature,
        avatarName = avatarName ?: this.avatarName,
        avatarPath = avatarPath ?: this.avatarPath,
        gender = gender ?: this.gender,
        birthday = birthday ?: this.birthday,
        obsAvatarId = obsAvatarId ?: this.obsAvatarId,
        province = province ?: this.province,
        city = city ?: this.city,
        area = area ?: this.area,
        address = address ?: this.address,
        raw = raw ?: this.raw,
    )

    companion object {
        fun fromJson(json: Map<String, Any?>): OfficialUserProfile = OfficialUserProfile(
            id = parsePersistedString(json["id"]),
            nickName = parsePersistedString(json["nickName"]),
            name = parsePersistedString(json["name"]),
            signature = parsePersistedString(json["signature"]),
            avatarName = parsePersistedString(json["avatarName"]),
            avatarPath = parsePersistedString(json["avatar_path"] ?: json["avatarPath"]),
            gender = parsePersistedString(json["gender"]),
            birthday = parsePersistedString(json["birthday"] ?: json["birthDay"]),
            obsAvatarId = parsePersistedString(json["obsAvatarId"]),
            province = parsePersistedString(json["province"]),
            city = parsePersistedString(json["city"]),
            area = parsePersistedString(json["area"]),
            address = parsePersistedString(json["address"]),
            raw = json.toMap(),
        )
    }
}
