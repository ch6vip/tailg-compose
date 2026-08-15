# Wave 2 Delegation Briefs (cloud / mqtt / control routing / stores)

Launch AFTER the model-layer subagent (`data/model`) reports. Each brief is
self-contained; give the subagent the CONVENTIONS.md path and the exact source
files. Do not run gradle locally; GH Actions verifies.

## W2-A · Cloud API (com.tailg.plus.data.cloud)

Sources (lib/services/):
- official_cloud_api_client.dart (Retrofit interface; endpoints/methods 1:1)
- official_cloud_auth_parser.dart (login/sms responses)
- official_cloud_data_parser.dart (vehicle/status JSON parsers)
- official_cloud_vehicle_mapper.dart (cloud DTO → data.model)
- official_cloud_vehicle_links.dart (binding/unbinding)
- official_cloud_vehicle_sync.dart (sync jobs)
- official_cloud_state.dart (auth/session StateFlow)
- official_cloud_storage.dart (persist session tokens — EncryptedSharedPreferences)
- official_cloud_service.dart (2591 LOC — facade; split into several files if needed)

Rules: Moshi @JsonClass DTOs for wire formats; Retrofit suspend funs;
error mapping mirrors OfficialCloudRedactor/remote error messages
(official_remote_error_messages.dart). Session storage MUST use
EncryptedSharedPreferences (androidx.security).

## W2-B · MQTT (com.tailg.plus.data.mqtt)

Sources (lib/services/):
- official_mqtt_config.dart (broker, clientId, topics)
- official_mqtt_payload.dart (payload build/parse per control command)
- official_mqtt_service.dart (Paho client lifecycle, connect/reconnect,
  publish, subscribe, ack semantics — cross-check MqttUtil.java /
  TailgMqttUtil.java under E:\ctf-aaa\tlddc\3.5.9\sources\...\model\home\mqtt)

Rules: Paho v3 (`org.eclipse.paho.client.mqttv3`); no Android service
dependency; suspend wrappers around blocking client on Dispatchers.IO.

## W2-C · Control routing (com.tailg.plus.domain.control)

Sources (lib/services/):
- control_channel_resolver.dart (near/far field selection, modelType branches)
- control_channel_status.dart
- control_command_policy.dart / control_command_route.dart / control_command_result.dart
- control_command_confirmation.dart (ack/state confirmation)
- control_command_executor.dart
- official_control_route.dart / official_car_operator_policy.dart
- official_remote_error_messages.dart
- models/control_command.dart etc. (already in data.model)

Cross-check ControlFragment.java + ControlTypeUtil.java in 3.5.9.
Rules: pure Kotlin state machine, unit-testable without device.

## W2-D · Stores (com.tailg.plus.data.store)

Sources (lib/services/):
- vehicle_store.dart (vehicle list persistence + selection)
- message_read_store.dart
- replica_feature_store.dart (+ models/replica_feature.dart, models/persistence_value.dart)
- official_cloud_vehicle_links.dart (if not in W2-A)
Rules: DataStore or JSON files under filesDir, matching Dart persistence keys.
