package com.tailg.plus.data.store

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Internal JSON codec for local persistence payloads.
 *
 * The Dart stores persist plain `jsonEncode`/`jsonDecode` of
 * `Map<String, dynamic>` / `List<dynamic>` trees (the models' `toJson()`
 * output). This codec round-trips the Kotlin equivalent
 * (`Map<String, Any?>` / `List<Any?>`) through `org.json` (Android built-in;
 * no new third-party dependency) so the stored bytes match the Dart JSON
 * shape (nulls included, e.g. `"latitude": null`).
 *
 * Values are expected to be JSON primitives (`String`/`Boolean`/`Number`),
 * `null`, `Map`, or `List`; any other object type is rejected by `org.json`
 * (mirrors Dart `jsonEncode` throwing for unsupported values).
 */
internal object StoreJson {

    /** Serialize a [Map]/[List] tree to a JSON text (never null). */
    fun encode(value: Any?): String {
        val wrapped = JSONObject.wrap(value)
        return wrapped?.toString() ?: "null"
    }

    /**
     * Parse a JSON text back into a `Map`/`List`/primitive tree.
     * `null` / `JSONObject.NULL` map to Kotlin `null`; throws [org.json.JSONException]
     * (or [IllegalArgumentException]) on malformed input, matching Dart
     * `jsonDecode` throwing on invalid JSON.
     */
    fun decode(text: String): Any? = fromJsonValue(JSONTokener(text).nextValue())

    private fun fromJsonValue(value: Any?): Any? = when {
        value === JSONObject.NULL -> null
        value is JSONObject -> {
            val map = linkedMapOf<String, Any?>()
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = fromJsonValue(value.get(key))
            }
            map
        }
        value is JSONArray -> {
            val list = mutableListOf<Any?>()
            for (i in 0 until value.length()) {
                list.add(fromJsonValue(value.get(i)))
            }
            list
        }
        else -> value
    }
}
