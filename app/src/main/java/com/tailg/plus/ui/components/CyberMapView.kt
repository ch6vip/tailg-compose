package com.tailg.plus.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tailg.plus.ui.theme.CyberHomeColors
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.res.stringResource
import com.tailg.plus.R

/**
 * osmdroid adapter for [MapTileConfig] templates (Dart
 * `map_tile_config.dart` + flutter_map `TileLayer` equivalent). AMap/Tianditu
 * templates are query-string based, so the URL is built by template resolution
 * rather than osmdroid's default path builder.
 */
class TemplateTileSource(
  private val template: String,
  private val subdomains: List<String>,
  name: String,
) : OnlineTileSourceBase(name, 3, 18, 256, ".png", arrayOf("https://")) {
  override fun getTileURLString(pTile: Long): String =
    MapTileConfig.resolveTileUrl(
      template = template,
      x = MapTileIndex.getX(pTile),
      y = MapTileIndex.getY(pTile),
      zoom = MapTileIndex.getZoom(pTile),
      subdomains = subdomains,
    )
}

/** Circle outline in geo coordinates for a fence radius (Dart CircleLayer radiusInMeter). */
fun circleGeoPoints(center: GeoPoint, radiusMeters: Double, segments: Int = 64): List<GeoPoint> {
  val latRad = Math.toRadians(center.latitude)
  val dLat = radiusMeters / 111_320.0
  val dLng = radiusMeters / (111_320.0 * cos(latRad).coerceAtLeast(1e-6))
  return List(segments) { i ->
    val angle = 2.0 * PI * i / segments
    GeoPoint(
      center.latitude + dLat * sin(angle),
      center.longitude + dLng * cos(angle),
    )
  }
}

/** Remembers the last camera target so recompositions never fight user gestures. */
private class CameraTarget {
  var centerLat: Double? = null
  var centerLng: Double? = null
  var trackKey: String? = null
  var initialized = false
}

/**
 * Data-driven overlay instances reused across recompositions.
 *
 * The `update` block of [androidx.compose.ui.viewinterop.AndroidView] runs on
 * every recomposition (live location tracking, fence toggles, loading flags).
 * Rebuilding `Polyline`/`Polygon`/`Marker` per call allocated objects and made
 * osmdroid re-register overlays each frame. Instead the instances are created
 * once and mutated in place; they are added to / removed from the map's
 * overlay list only when their visibility actually flips.
 */
private class MapOverlayState(
  val trackCasing: Polyline,
  val trackLine: Polyline,
  val fence: Polygon,
  val pin: Marker,
  val overlays: MutableList<Overlay> = mutableListOf(),
) {
  var trackVisible = false
  var fenceVisible = false
  var pinVisible = false

  /** Re-apply visibility after a mutation so the map overlay list stays canonical. */
  fun sync(mapView: MapView) {
    val desired = buildList {
      if (trackVisible) {
        add(trackCasing)
        add(trackLine)
      }
      if (fenceVisible) add(fence)
      if (pinVisible) add(pin)
    }
    // Cheap diffs: remove what is no longer wanted, add what is missing.
    overlays.forEach { if (it !in desired) mapView.overlays.remove(it) }
    desired.forEach { if (it !in overlays) mapView.overlays.add(it) }
    overlays.clear()
    overlays.addAll(desired)
  }
}

/**
 * Shared map composable — port of the Dart `_MapPanel` (lib/pages/location_page.dart):
 * tile layer (+ Tianditu annotation overlay when configured), vehicle pin,
 * fence circle (green enabled / amber warning, 0.12 fill + 0.55 border alpha),
 * track polyline (green with white casing) and track bounds auto-fit.
 *
 * The camera only moves when the target (vehicle pin or track) actually
 * changes; plain recompositions (loading flags, fence toggle) leave the user's
 * pan/zoom alone.
 */
@Composable
fun CyberMapView(
  latitude: Double?,
  longitude: Double?,
  modifier: Modifier = Modifier,
  fenceRadiusMeters: Double? = null,
  fenceEnabled: Boolean = true,
  trackPoints: List<GeoPoint> = emptyList(),
  initialZoom: Double = 16.0,
  showVehiclePin: Boolean = true,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val baseTemplate = remember { MapTileConfig.baseUrlTemplate() }
  val subdomains = remember { MapTileConfig.subdomains() }
  val labelTemplate = remember { MapTileConfig.annotationUrlTemplate() }
  val strVehicleLocation = stringResource(R.string.map_view_vehicle_location)

  // Tiles providers own thread pools: create once, detach once. Rebuilding
  // them per recomposition would leak threads/bitmats.
  val labelProvider = remember(labelTemplate) {
    labelTemplate?.let { MapTileProviderBasic(context, TemplateTileSource(it, subdomains, "tailg-label")) }
  }

  val camera = remember { CameraTarget() }

  val mapView = remember {
    MapView(context).apply {
      layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
      setTileSource(TemplateTileSource(baseTemplate, subdomains, "tailg-base"))
      setMultiTouchControls(true)
      controller.setZoom(initialZoom)
      minZoomLevel = 3.0
      maxZoomLevel = 18.0
      isTilesScaledToDpi = true
    }
  }

  // Reusable data overlays — created once per map view instance.
  val overlayState = remember {
    MapOverlayState(
      trackCasing = Polyline(mapView).apply {
        outlinePaint.color = android.graphics.Color.WHITE
        outlinePaint.strokeWidth = 9f
        outlinePaint.alpha = (255 * 0.55f).toInt()
      },
      trackLine = Polyline(mapView).apply {
        outlinePaint.color = android.graphics.Color.rgb(0x22, 0xC5, 0x5E)
        outlinePaint.strokeWidth = 5f
      },
      fence = Polygon(mapView).apply {
        outlinePaint.strokeWidth = 2f
      },
      pin = Marker(mapView).apply {
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        title = strVehicleLocation
      },
    )
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> mapView.onResume()
        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      mapView.onDetach()
      labelProvider?.detach()
    }
  }

  Box(modifier = modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
      modifier = Modifier.matchParentSize(),
      factory = {
        mapView.also { mv -> labelProvider?.let { mv.overlays.add(TilesOverlay(it, context)) } }
      },
      update = { view ->
        val center: GeoPoint? = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null

        // Track polyline with white casing (Dart: 5px green over 3px white border).
        val hasTrack = trackPoints.size >= 2
        if (hasTrack) {
          overlayState.trackCasing.setPoints(trackPoints)
          overlayState.trackLine.setPoints(trackPoints)
        }
        overlayState.trackVisible = hasTrack

        // Fence circle centered on the vehicle pin (Dart CircleLayer useRadiusInMeter).
        val radius = fenceRadiusMeters
        val showFence = center != null && radius != null && radius > 0
        if (showFence) {
          val circle = circleGeoPoints(center!!, radius!!)
          overlayState.fence.points = circle + listOf(circle.first())
          val base = if (fenceEnabled) android.graphics.Color.rgb(0x22, 0xC5, 0x5E) else android.graphics.Color.rgb(0xF5, 0x9E, 0x0B)
          overlayState.fence.fillPaint.color = base
          overlayState.fence.fillPaint.alpha = (255 * 0.12f).toInt()
          overlayState.fence.outlinePaint.color = base
          overlayState.fence.outlinePaint.alpha = (255 * 0.55f).toInt()
        }
        overlayState.fenceVisible = showFence

        // Vehicle pin.
        overlayState.pinVisible = center != null && showVehiclePin
        if (overlayState.pinVisible) {
          overlayState.pin.position = center
        }

        overlayState.sync(view)
        view.invalidate()

        // Camera moves only when the target itself changes (Dart initialCenter /
        // CameraFit semantics); recompositions must not snap the view back.
        val trackKey = if (hasTrack) {
          "${trackPoints.size}|${trackPoints.first().latitude},${trackPoints.first().longitude}|${trackPoints.last().latitude},${trackPoints.last().longitude}"
        } else {
          null
        }
        when {
          trackKey != null && camera.trackKey != trackKey -> {
            camera.trackKey = trackKey
            val box = BoundingBox.fromGeoPoints(trackPoints).increaseByScale(1.25f)
            view.post { view.zoomToBoundingBox(box, false, 64) }
          }
          trackKey == null && center != null &&
            (camera.centerLat != center.latitude || camera.centerLng != center.longitude) -> {
            camera.centerLat = center.latitude
            camera.centerLng = center.longitude
            view.controller.animateTo(center)
          }
          center == null && !camera.initialized -> {
            camera.initialized = true
            view.controller.setCenter(GeoPoint(30.2741, 120.1551))
          }
        }
        camera.initialized = true
      },
    )
    if (latitude == null || longitude == null) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .background(CyberHomeColors.mapPlaceholder.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          LucideIcon(icon = Lucide.map, size = 40.dp, color = CyberHomeColors.inkFaint)
          Spacer(Modifier.height(6.dp))
          androidx.compose.material3.Text(
            text = stringResource(R.string.map_view_no_location),
            style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkMuted),
          )
        }
      }
    }
  }
}
