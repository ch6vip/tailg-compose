package com.tailg.plus.ui.components

import com.tailg.plus.util.readBytesLimited

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tailg.plus.R
import com.tailg.plus.data.cloud.ResolvedVehicleLocation
import com.tailg.plus.ui.theme.AppRadii
import com.tailg.plus.ui.theme.CyberHomeColors
import com.tailg.plus.util.BitmapMemoryCache
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Port of `lib/widgets/cyber_map_stats.dart` — map + ride-stats card row.
 *
 * **Pending references**: [ResolvedVehicleLocation] (Dart
 * `lib/services/vehicle_location_resolver.dart`, fields `hasCoordinate`,
 * `latitude`, `longitude`, `address`) → `com.tailg.plus.data.cloud`.
 *
 * **Map deferral**: the Dart embeds a `flutter_map` tile map. This project has
 * no map SDK yet (UI_PORT_PLAN "map SDK choice TODO"), so the mini map is a
 * placeholder canvas: mapPlaceholder fill + centered map glyph + a drawn pin
 * when [ResolvedVehicleLocation.hasCoordinate]. Swap the Canvas in [MiniMap]
 * for the chosen map composable in the map SDK pass.
 *
 * Token mapping: `CyberHomeColors.card/mapPlaceholder/ink/inkMuted/inkFaint/
 * primary/primarySoft/line/actionShadow` → the same-named [CyberHomeColors]
 * tokens; `AppRadii.sheet` → [AppRadii.sheet].
 *
 * Icons: `Lucide.map-pin` → `Icons.Filled.LocationOn`; `Lucide.map` →
 * `Icons.Filled.Map`; `Lucide.chart` → `Icons.Filled.BarChart`.
 */
@Composable
fun CyberMapStatsRow(
  location: ResolvedVehicleLocation?,
  address: String,
  todayKm: String,
  totalKm: String,
  modifier: Modifier = Modifier,
  onMapTap: () -> Unit,
  onRideStatsTap: () -> Unit,
) {
  // Side-by-side layout on every width: the mini map and the ride card split
  // the row evenly, with a 12dp gutter between them. Both cards are 1:1
  // squares (aspectRatio(1f)) sized by the available width.
  Row(
    modifier = modifier.padding(horizontal = 20.dp).fillMaxWidth(),
    verticalAlignment = Alignment.Top,
  ) {
    Box(modifier = Modifier.weight(1f)) {
      MiniMap(
        location = location,
        address = address,
        onMapTap = onMapTap,
      )
    }
    Spacer(Modifier.width(12.dp))
    Box(modifier = Modifier.weight(1f)) {
      AppPressable(
        onClick = onRideStatsTap,
        shape = RoundedCornerShape(AppRadii.sheet),
        semanticsLabel = stringResource(R.string.map_stats_view_ride),
        shadowElevation = 0.dp,
      ) {
        RideCard(
          todayKm = todayKm,
          totalKm = totalKm,
        )
      }
    }
  }
}

/** Raw "lat, lng" text (the location resolver's fallback) is not user-readable. */
private val RawCoordinates = Regex("^-?\\d+(\\.\\d+)?\\s*,\\s*-?\\d+(\\.\\d+)?$")

/** Address-strip text: blank or raw-coordinate fallbacks render as [fallback] (车辆定位). */
internal fun addressStripText(address: String, fallback: String): String {
  val text = address.trim()
  return if (text.isEmpty() || RawCoordinates.containsMatchIn(text)) fallback else text
}

/** Split "2.7 km" into ("2.7", "km") so the number renders big and the unit small. */
internal fun splitValueWithUnit(valueWithUnit: String): Pair<String, String> {
  val idx = valueWithUnit.lastIndexOf(' ')
  return if (idx > 0) valueWithUnit.substring(0, idx) to valueWithUnit.substring(idx + 1)
  else valueWithUnit to ""
}

/** Mini map on osmdroid tiles (Dart flutter_map embed); tap opens the map page. */
@Composable
internal fun MiniMap(
    location: ResolvedVehicleLocation?,
    address: String,
    onMapTap: () -> Unit,
    showFooter: Boolean = true,
) {
  val hasPin = location?.hasCoordinate == true
  val lat = location?.latitude
  val lng = location?.longitude
  val mapDescription = if (address.isBlank()) {
    stringResource(R.string.map_no_location)
  } else {
    "${stringResource(R.string.location_title)}：$address"
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(1f)
      .clip(RoundedCornerShape(AppRadii.sheet))
      .background(CyberHomeColors.mapPlaceholder),
  ) {
    if (hasPin && lat != null && lng != null) {
      MiniMapPreview(
        latitude = lat,
        longitude = lng,
        modifier = Modifier.matchParentSize(),
      )
    }
    Box(
      modifier = Modifier
        .matchParentSize()
        .semantics { contentDescription = mapDescription }
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          role = Role.Button,
        ) { onMapTap() },
    )
    // Address footer chip — official style: opaque gray strip, dark text,
    // no icon. Hidden when the host card draws its own inset address strip.
    if (showFooter) {
      Row(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .fillMaxWidth()
          .background(CyberHomeColors.control)
          .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = addressStripText(address, stringResource(R.string.service_location)),
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.W500,
            color = CyberHomeColors.ink.copy(alpha = 0.9f),
          ),
        )
      }
    }
  }
}

@Composable
private fun RideCard(
  todayKm: String,
  totalKm: String,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .aspectRatio(1f)
      .clip(RoundedCornerShape(AppRadii.sheet))
      .background(CyberHomeColors.card),
  ) {
    RideSegment(
      title = stringResource(R.string.map_stats_today_distance),
      valueWithUnit = todayKm,
      valueFontSize = 48.sp,
      background = CyberHomeColors.rideAccentSoft,
      showSwapIcon = true,
      modifier = Modifier.weight(1f),
    )
    HorizontalDivider(thickness = 1.dp, color = CyberHomeColors.line)
    RideSegment(
      title = stringResource(R.string.map_stats_total_distance),
      valueWithUnit = totalKm,
      valueFontSize = 32.sp,
      background = CyberHomeColors.card,
      showSwapIcon = false,
      modifier = Modifier.weight(1f),
    )
  }
}

/** Single segment on the RideCard (title + big number + small unit). */
@Composable
private fun RideSegment(
  title: String,
  valueWithUnit: String,
  valueFontSize: TextUnit,
  background: Color,
  showSwapIcon: Boolean,
  modifier: Modifier = Modifier,
) {
  val (number, unit) = remember(valueWithUnit) { splitValueWithUnit(valueWithUnit) }
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(background)
      .padding(horizontal = 16.dp, vertical = 14.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = title,
          style = TextStyle(fontSize = 13.sp, color = CyberHomeColors.inkSecondary),
        )
        Spacer(Modifier.weight(1f))
        if (showSwapIcon) {
          LucideIcon(icon = Lucide.swapVert, size = 16.dp, color = CyberHomeColors.inkFaint)
        }
      }
      // The card is square and each segment gets half of its height. Keep the
      // value row inside the remaining space and scale the complete number +
      // unit together when the available slot is smaller than the requested
      // 48sp/32sp typography. Without this bounded slot, the 48sp text can
      // extend into the next segment and its lower half is painted over.
      ScaleToFit(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.BottomStart,
      ) {
        Row(verticalAlignment = Alignment.Bottom) {
          AnimatedValueText(
            value = number,
            maxLines = 1,
            style = TextStyle(
              fontSize = valueFontSize,
              fontWeight = FontWeight.W700,
              color = CyberHomeColors.ink,
            ),
          )
          if (unit.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(
              text = unit,
              maxLines = 1,
              style = TextStyle(fontSize = 16.sp, color = CyberHomeColors.inkMuted),
              modifier = Modifier.padding(bottom = 6.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MiniMapPreview(
  latitude: Double,
  longitude: Double,
  modifier: Modifier = Modifier,
) {
  val zoom = 15
  val tile = remember(latitude, longitude) {
    MapTileConfig.webMercatorLocation(latitude, longitude, zoom)
  }
  val url = remember(tile) {
    MapTileConfig.resolveTileUrl(
      template = MapTileConfig.baseUrlTemplate(),
      x = tile.tileX,
      y = tile.tileY,
      zoom = tile.zoom,
    )
  }
  val bitmap by produceState<ImageBitmap?>(initialValue = null, url) {
    value = withContext(Dispatchers.IO) { loadMiniMapTile(url) }
  }
  val image = bitmap
  if (image != null) {
    Image(
      bitmap = image,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      alignment = BiasAlignment(
        horizontalBias = (tile.fractionalX * 2.0 - 1.0).toFloat(),
        verticalBias = (tile.fractionalY * 2.0 - 1.0).toFloat(),
      ),
      modifier = modifier,
    )
  }
}

/**
 * Bounded in-memory tile cache for the home mini-map. Now routed through the
 * shared [BitmapMemoryCache] (heap-aware LRU + onTrimMemory) instead of a
 * private 16-entry map, so tiles and vehicle photos draw from one global
 * budget and the OS trims both under pressure.
 */
private const val MINI_MAP_TILE_CACHE_PREFIX = "mini-map-tile:"

private suspend fun loadMiniMapTile(url: String): ImageBitmap? {
  val cacheKey = MINI_MAP_TILE_CACHE_PREFIX + url
  BitmapMemoryCache.get(cacheKey)?.let { return it.asImageBitmap() }
  return withContext(Dispatchers.IO) {
    runCatching {
      val connection = URL(url).openConnection() as HttpURLConnection
      connection.connectTimeout = 4_000
      connection.readTimeout = 4_000
      connection.instanceFollowRedirects = true
      connection.setRequestProperty("User-Agent", "tailg-plus")
      try {
        if (connection.contentLengthLong > MAX_MINI_MAP_TILE_BYTES) return@runCatching null
        val bytes = connection.inputStream.use { it.readBytesLimited(MAX_MINI_MAP_TILE_BYTES) }
        if (bytes.isEmpty()) return@runCatching null
        val decoded = decodeMiniMapTile(bytes)
        if (decoded != null) BitmapMemoryCache.put(cacheKey, decoded)
        decoded?.asImageBitmap()
      } finally {
        connection.disconnect()
      }
    }.getOrNull()
  }
}

/**
 * Decode a mini-map tile bounded to the home preview slot (~256px). Tiles are
 * 256x256 already, but a scaled/retina tile source can exceed that; sampling
 * keeps the decode allocation small and identical to what the preview shows.
 * [android.graphics.Bitmap.Config.RGB_565] matches the previous decode and
 * halves memory vs ARGB_8888 (map tiles carry no alpha).
 */
internal fun decodeMiniMapTile(bytes: ByteArray): Bitmap? {
  if (bytes.isEmpty()) return null
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
  var sampleSize = 1
  while (bounds.outWidth / (sampleSize * 2) >= MINI_MAP_TILE_TARGET_PX ||
    bounds.outHeight / (sampleSize * 2) >= MINI_MAP_TILE_TARGET_PX
  ) {
    sampleSize *= 2
  }
  val options = BitmapFactory.Options().apply {
    inSampleSize = sampleSize
    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
  }
  return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private const val MINI_MAP_TILE_TARGET_PX = 256
private const val MAX_MINI_MAP_TILE_BYTES = 2 * 1024 * 1024
