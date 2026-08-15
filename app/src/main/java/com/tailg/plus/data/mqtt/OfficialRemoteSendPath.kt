package com.tailg.plus.data.mqtt

/**
 * Which remote transport actually carried the last
 * [OfficialMqttService.sendCommandPreferMqtt] call.
 * Dart `enum OfficialRemoteSendPath { mqtt, http }`.
 */
enum class OfficialRemoteSendPath { MQTT, HTTP }
