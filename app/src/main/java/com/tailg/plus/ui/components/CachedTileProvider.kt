package com.tailg.plus.ui.components

/**
 * Port of `lib/widgets/cached_tile_provider.dart` — disk-cached map tile
 * provider.
 *
 * The Dart class extends flutter_map's `TileProvider` and wraps
 * `CachedNetworkImageProvider` for on-disk tile caching. This project has no
 * map SDK yet (UI_PORT_PLAN: "map SDK choice TODO"), so this file only fixes
 * the contract the map layer must implement once a map composable is chosen
 * (e.g. MapLibre / osmdroid / google-maps-compose). No code depends on it yet;
 * it exists to keep the 25-widget port 1:1.
 *
 * Requirements for the future provider (mirroring the Dart):
 * - URL template `{z}/{x}/{y}` resolved from the tile server config
 *   (Dart `lib/config/map_tile_config.dart` — tianditu/web-tile server).
 * - Disk caching across page switches / restarts (Dart disk cache; Compose
 *   equivalent: OkHttp cache or the map SDK's own tile cache).
 */
interface CachedTileProvider {
  /** Resolve the tile URL for a Web-Mercator tile coordinate. */
  fun tileUrl(x: Int, y: Int, zoom: Int): String

  /**
   * Whether responses should be cached on disk. The Dart implementation
   * always caches; map SDKs usually expose this via their tile cache config.
   */
  val diskCacheEnabled: Boolean
    get() = true
}

/**
 * Tile URL resolver for the default tile server (Dart
 * `map_tile_config.dart`). `template` should contain `{x}`/`{y}`/`{z}`
 * placeholders; the implementation stays URL-template based until the map SDK
 * lands, matching the Dart's URL-building approach.
 */
class TemplateCachedTileProvider(
  private val template: String,
  private val headers: Map<String, String> = emptyMap(),
) : CachedTileProvider {
  override fun tileUrl(x: Int, y: Int, zoom: Int): String =
    template
      .replace("{x}", x.toString())
      .replace("{y}", y.toString())
      .replace("{z}", zoom.toString())

  /** Extra HTTP headers (Dart `TileProvider.headers`). */
  fun requestHeaders(): Map<String, String> = headers
}
