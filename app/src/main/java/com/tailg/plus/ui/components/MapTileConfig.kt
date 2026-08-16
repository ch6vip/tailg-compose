package com.tailg.plus.ui.components

/**
 * Port of `lib/config/map_tile_config.dart` — tile server configuration.
 *
 * Tianditu is used when a token is provided (Gradle property `tiandituToken`
 * -> BuildConfig.TIANDITU_TOKEN, mirroring the Dart `TIANDITU_TOKEN`
 * dart-define); otherwise the AutoNavi raster tiles, same as the Flutter line.
 * Pure Kotlin (no osmdroid imports) so template resolution stays unit-testable
 * on the JVM; the osmdroid tile-source adapter lives in `CyberMapView.kt`.
 */
object MapTileConfig {

  const val TIANDITU_TOKEN: String = com.tailg.plus.BuildConfig.TIANDITU_TOKEN

  val hasTiandituToken: Boolean
    get() = TIANDITU_TOKEN.trim().isNotEmpty()

  fun baseUrlTemplate(token: String = TIANDITU_TOKEN): String {
    if (token.trim().isNotEmpty()) {
      return "https://t{s}.tianditu.gov.cn/DataServer" +
        "?T=vec_w&x={x}&y={y}&l={z}&tk=${token.trim()}"
    }
    return "https://webrd0{s}.is.autonavi.com/appmaptile" +
      "?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}"
  }

  fun annotationUrlTemplate(token: String = TIANDITU_TOKEN): String? {
    if (token.trim().isEmpty()) return null
    return "https://t{s}.tianditu.gov.cn/DataServer" +
      "?T=cva_w&x={x}&y={y}&l={z}&tk=${token.trim()}"
  }

  fun subdomains(token: String = TIANDITU_TOKEN): List<String> {
    if (token.trim().isNotEmpty()) return listOf("0", "1", "2", "3", "4", "5", "6", "7")
    return listOf("1", "2", "3", "4")
  }

  val providerLabel: String
    get() = if (hasTiandituToken) "天地图" else "高德地图"

  /**
   * Resolve `{s}`/`{x}`/`{y}`/`{z}` in a tile template (matches the Dart
   * flutter_map subdomain rotation `t{s}`).
   */
  fun resolveTileUrl(
    template: String,
    x: Int,
    y: Int,
    zoom: Int,
    subdomainSeed: Int = x + y,
    subdomains: List<String> = subdomains(),
  ): String {
    val subdomain = if (subdomains.isEmpty()) "" else subdomains[Math.floorMod(subdomainSeed, subdomains.size)]
    return template
      .replace("{s}", subdomain)
      .replace("{x}", x.toString())
      .replace("{y}", y.toString())
      .replace("{z}", zoom.toString())
  }
}
