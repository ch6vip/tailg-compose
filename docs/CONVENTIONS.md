# Port Conventions (Dart → Kotlin)

> 状态：**已完成**。本文档是移植期间（历史）的强制契约，保留作移植规范参考；
> 后续新代码仍建议遵循其中的命名、数据建模与异步模型约定。

This document fixes the contracts for porting `tailg-ble-app` (Flutter/Dart) into
this Kotlin + Compose project. **All subagent ports MUST follow this file.**

相关文档：[UI_PORT_PLAN.md](UI_PORT_PLAN.md)（移植清单）、[PORT_WAVE2.md](PORT_WAVE2.md)（Wave 2 简报）。

## Source of truth

- Flutter source: `E:\ctf-aaa\tlddc\tailg-ble-app\lib\...` (read it, do not guess)
- Official decompiled Java reference: `E:\ctf-aaa\tlddc\3.5.9\sources\com\tailg\run\intelligence\...` (consult for protocol semantics)
- Kotlin output root: `app/src/main/java/com/tailg/plus/`

## Package layout

| Kotlin package | Ports |
|---|---|
| `com.tailg.plus.data.model` | `lib/models/*` — plain Kotlin data classes, **no Android imports**, no annotations unless JSON DTO |
| `com.tailg.plus.data.ble` | `lib/ble/*` — protocol, AES, frames, connection manager (Android `BluetoothGatt` wrapper) |
| `com.tailg.plus.data.cloud` | `lib/services/official_cloud*` — Retrofit client + Moshi DTOs + parsers + storage |
| `com.tailg.plus.data.mqtt` | `lib/services/official_mqtt*` — Paho client, topics, payloads |
| `com.tailg.plus.domain` | control routing/state machines, auto-connect, induction, OTA use cases |
| `com.tailg.plus.ui.screens` | `lib/pages/*` — one package per screen |
| `com.tailg.plus.ui.components` | `lib/widgets/*` — shared composables |
| `com.tailg.plus.service` | foreground services |

## Naming & style

- 2-space indent, `camelCase` members, `PascalCase` types, `UPPER_SNAKE` constants.
- Keep the Dart name where possible (`VehicleStatus`, `ControlCommandType`).
- Protocol constants keep their exact official values; `const_identifier_names` is intentionally NOT enforced.
- **No emoji anywhere.** Icons come from Material icons for now (Lucide swap is a later cosmetic pass).
- **Kotlin comments nest block comments**: NEVER write a literal `/*` inside a KDoc/comment
  (e.g. `` `lib/models/*` `` or `` `app/device/cmd/*` ``). Reword to avoid the `/*` sequence,
  otherwise the outer comment never closes and compilation fails with "Unclosed comment".

## Data modeling

- Dart `class X { final String a; ... }` → Kotlin `data class X(val a: String, ...)`.
- Dart `Map<String, dynamic>` handling → Moshi `@JsonClass(generateAdapter = true)` DTOs where JSON is on the wire; internal maps stay `Map<String, String>`.
- `enum` with int values → Kotlin `enum class` with explicit `value` + `companion object { fun fromValue(v: Int) }` matching the Dart semantics.
- `DateTime` → `java.time.Instant`/`LocalDateTime`; `Duration` → `java.time.Duration` or `kotlin.time.Duration` (pick one per module, document it).

## Async model

- Dart `Future<T>` → Kotlin `suspend fun` or `Flow<T>` (StateFlow for UI state).
- Dart `Stream<T>` → Kotlin `Flow<T>`.
- Callbacks (`void Function(...)`) → `suspend` lambdas or callbacks, prefer Flow/StateFlow.

## Dependencies (already in `gradle/libs.versions.toml`)

Retrofit + Moshi (codegen via KSP), OkHttp, Paho MQTT v3, DataStore Preferences,
EncryptedSharedPreferences (`androidx.security`), coroutines, Timber.
**Do not add new third-party deps without a comment in the PR.**
Built-in `javax.crypto` covers the AES work; no extra crypto library.

## Threading

- Network/BLE/MQTT work must not run on the main thread (use coroutines + `Dispatchers.IO`).
- Room is NOT used; persistence is DataStore (prefs) + JSON files under `filesDir` where the Dart code used file storage.

## Tests

Unit tests live in `app/src/test/java/com/tailg/plus/...`, mirroring the Dart
`test/` tree names. Pure logic (protocol parse, AES, control routing, MQTT
payload build, cloud parsers) MUST be covered without a device.

## Definition of done per module

1. Compiles cleanly (`./gradlew :app:compileDebugKotlin`).
2. Unit tests for pure logic pass.
3. Report back: files ported, semantic decisions, TODOs for device-only behavior.
