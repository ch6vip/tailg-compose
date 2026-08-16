package com.tailg.plus.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tailg.plus.ui.theme.CyberHomeColors
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

/**
 * Shared map composable — port of the Dart `_MapPanel` (lib/pages/location_page.dart):
 * tile layer (+ Tianditu annotation overlay when configured), vehicle pin,
 * fence circle (green enabled / amber warning, 0.12 fill + 0.55 border alpha),
 * track polyline (green with white casing) and track bounds auto-fit.
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
  val annotationTemplate = remember { MapTileConfig.annotationUrlTemplate() }
  val baseTemplate = remember { MapTileConfig.baseUrlTemplate() }
  val subdomains = remember { MapTileConfig.subdomains() }

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
    }
  }

  fun rebuildOverlays() {
    mapView.overlays.clear()
    if (annotationTemplate != null) {
      // Tianditu label overlay (cva_w) as a second tiles-only layer.
      val labelProvider = org.osmdroid.tileprovider.MapTileProviderBasic(
        context,
        TemplateTileSource(annotationTemplate, subdomains, "tailg-label"),
      )
      mapView.overlays.add(org.osmdroid.views.overlay.TilesOverlay(labelProvider, context))
    }
    val center: GeoPoint? = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null

    // Track polyline with white casing (Dart: 5px green over 3px white border).
    if (trackPoints.size >= 2) {
      val casing = Polyline(mapView).apply {
        setPoints(trackPoints)
        outlinePaint.color = android.graphics.Color.WHITE
        outlinePaint.strokeWidth = 9f
        outlinePaint.alpha = (255 * 0.55f).toInt()
      }
      val line = Polyline(mapView).apply {
        setPoints(trackPoints)
        outlinePaint.color = android.graphics.Color.rgb(0x22, 0xC5, 0x5E)
        outlinePaint.strokeWidth = 5f
      }
      mapView.overlays.add(casing)
      mapView.overlays.add(line)
    }

    // Fence circle centered on the vehicle pin (Dart CircleLayer useRadiusInMeter).
    val radius = fenceRadiusMeters
    if (center != null && radius != null && radius > 0) {
      val fence = Polygon(mapView).apply {
        points = circleGeoPoints(center, radius) + listOf(circleGeoPoints(center, radius).first())
        val base = if (fenceEnabled) android.graphics.Color.rgb(0x22, 0xC5, 0x5E) else android.graphics.Color.rgb(0xF5, 0x9E, 0x0B)
        fillPaint.color = base
        fillPaint.alpha = (255 * 0.12f).toInt()
        outlinePaint.color = base
        outlinePaint.alpha = (255 * 0.55f).toInt()
        outlinePaint.strokeWidth = 2f
      }
      mapView.overlays.add(fence)
    }

    if (center != null && showVehiclePin) {
      val pin = Marker(mapView).apply {
        position = center
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        title = "车辆位置"
      }
      mapView.overlays.add(pin)
    }
    mapView.invalidate()
  }

  Box(modifier = modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
      modifier = Modifier.matchParentSize(),
      factory = { mapView },
      update = { view ->
        rebuildOverlays()
        val center: GeoPoint? = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null
        when {
          trackPoints.size >= 2 -> {
            val box = BoundingBox.fromGeoPoints(trackPoints).increaseByScale(1.25f)
            view.post { view.zoomToBoundingBox(box, false, 64) }
          }
          center != null -> view.controller.animateTo(center)
          else -> view.controller.setCenter(GeoPoint(30.2741, 120.1551))
        }
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
            text = "暂无位置数据",
            style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W600, color = CyberHomeColors.inkMuted),
          )
        }
      }
    }
  }
}
