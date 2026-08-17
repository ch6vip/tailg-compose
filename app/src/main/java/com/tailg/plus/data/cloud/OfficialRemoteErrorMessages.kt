package com.tailg.plus.data.cloud

/**
 * Compatibility alias — single source of truth lives in
 * [com.tailg.plus.domain.control.OfficialRemoteErrorMessages].
 *
 * MQTT and older call sites imported this type from the cloud package during
 * the parallel port; keep the name stable without duplicating logic.
 */
typealias OfficialRemoteErrorMessages =
  com.tailg.plus.domain.control.OfficialRemoteErrorMessages
