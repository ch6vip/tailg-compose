package com.tailg.plus.ui.components

/**
 * Port of `lib/widgets/cached_tile_provider.dart` — disk-cached map tile
 * provider.
 *
 * The Dart class extends flutter_map's `TileProvider` and wraps
 * `CachedNetworkImageProvider` for on-disk tile caching. The Compose line uses
 * osmdroid (see `CyberMapView.kt` / `MapTileConfig.kt`): template resolution
 * reuses this contract and on-disk caching comes from osmdroid's tile cache
 * (configured in `TailgApplication`).
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
