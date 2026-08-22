package com.tailg.plus.data.cloud

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

/**
 * Tiny Moshi wrapper for the dynamic JSON the cloud module deals with
 * (request bodies, response envelopes, persisted cache JSON).
 *
 * Plain Moshi (no kotlin-reflect / codegen) natively adapts `Any`, `Map`, and
 * `List`, so this covers every shape used here. Deviation from Dart
 * `jsonEncode`: Moshi skips null *values* in maps when serializing (Dart writes
 * `null`); every consumer here is lenient (`fromJson` / `parsePersisted*`
 * default missing keys), so round-trips are unaffected.
 */
internal object CloudJson {
    /** Single shared plain-Moshi instance for the cloud module. */
    internal val moshi: Moshi = Moshi.Builder().build()
    private val adapter: JsonAdapter<Any> = moshi.adapter(Any::class.java)

    fun encode(value: Any?): String = adapter.toJson(value)

    /** Throws on malformed JSON (mirrors Dart `jsonDecode`). */
    fun decode(text: String): Any = adapter.fromJson(text) ?: Unit
}
