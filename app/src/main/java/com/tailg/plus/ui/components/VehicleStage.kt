package com.tailg.plus.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tailg.plus.ui.theme.AppColors
import com.tailg.plus.ui.theme.AppColorsLight
import com.tailg.plus.util.BitmapMemoryCache
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Port of `lib/widgets/vehicle_stage.dart` — fallback vehicle illustration.
 *
 * The Dart `CustomPainter` is ported 1:1 to a [DrawScope] extension
 * ([drawVehicleStage]) drawing the 340×172 viewBox with Compose [Path]s,
 * brushes and native blur-free strokes.
 *
 * **Color policy**: the painter uses the Dart illustration palette
 * (file-local constants, the same way the Dart file declares its own
 * `_vehicleFrameColor` etc. and the theme declares `ReplicaBikeColors`).
 * UI chrome tokens are used where the Dart referenced theme tokens:
 * - battery track `AppColors.card3` → [AppColorsLight.surfaceContainerHigh]
 * - dashboard `AppColors.inkBtn` → [AppColors.inkBtn]
 * - dashboard screen `AppColors.energyGreen` → [AppColors.energyGreen]
 * - wheel hub `AppColors.pageBgBot` → [AppColors.pageBgBot]
 *
 * Remote `carPhoto` values are loaded through the existing OkHttp dependency;
 * failed or unsupported URLs fall back to the local painter.
 */

// Painter palette (mirror of Dart `_vehicleFrameColor` etc.).
private val VehicleFrameColor = Color(0xFF2A313D)
private val VehicleBodyLight = Color(0xFFDFE5EC)
private val VehicleTireShadow = Color(0xFFC2CAD4)
private val VehicleAccentDark = Color(0xFF3A434F)
private val VehicleBodyTop = Color(0xFFFCFDFE)
private val VehicleSeatStitch = Color(0x4D4A5563)
private val VehicleHubStroke = Color(0xFF9AA3B0)
private val VehicleSpoke = Color(0xFFB6BEC9)
private val VehicleHeadlight = Color(0xFFFCE7B8)
private val VehicleBatteryBorder = Color(0xFF04231A).copy(alpha = 0.08f)

private val vehicleImageClient by lazy {
  OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .callTimeout(7, TimeUnit.SECONDS)
    .build()
}

private const val MAX_VEHICLE_IMAGE_BYTES = 5L * 1024L * 1024L

private const val VEHICLE_IMAGE_CACHE_PREFIX = "vehicle-photo:"

/**
 * In-flight deduplication for vehicle photos. The garage list renders several
 * cards at once and multiple cards often share the same car-photo URL (same
 * model). Without this, N visible cards fired N identical network requests;
 * the first caller wins and every concurrent caller awaits the same
 * [kotlinx.coroutines.CompletableDeferred] (ComicPlus_Pure's
 * `pageLoadsInFlight` pattern).
 */
private val vehicleImageLoadsInFlight =
  ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<Bitmap?>>()

/** Bounded decode: read bounds first, sample down to the target, then cache. */
private suspend fun loadVehicleImage(url: String): Bitmap? {
  if (!isRemoteVehicleImageUrl(url)) {
    return null
  }
  val cacheKey = VEHICLE_IMAGE_CACHE_PREFIX + url
  BitmapMemoryCache.get(cacheKey)?.let { return it }
  // Join an in-flight load for the same URL instead of starting a second
  // network request. The deferred completes when the FIRST caller's fetch
  // settles; every concurrent caller awaits the same instance, so the network
  // hit happens exactly once per URL per eviction window.
  vehicleImageLoadsInFlight[url]?.let { return it.await() }
  val deferred = kotlinx.coroutines.CompletableDeferred<Bitmap?>()
  val existing = vehicleImageLoadsInFlight.putIfAbsent(url, deferred)
  if (existing != null) return existing.await()
  try {
    val decoded = withContext(Dispatchers.IO) {
      runCatching {
        val request = Request.Builder().url(url).get().build()
        vehicleImageClient.newCall(request).execute().use { response ->
          val body = response.body ?: return@use null
          val declared = body.contentLength()
          if (!response.isSuccessful ||
            (declared >= 0 && declared > MAX_VEHICLE_IMAGE_BYTES)
          ) {
            return@use null
          }
          // Read the bounded body once into memory (guarded by the byte cap
          // above), then decode twice from the array: bounds pass + sampled
          // pass. A single InputStream cannot be decoded twice — the bounds
          // pass consumes the stream and network streams are not rewindable.
          val bytes = body.bytes()
          if (bytes.isEmpty() || bytes.size > MAX_VEHICLE_IMAGE_BYTES) return@use null
          decodeVehicleImageSampled(bytes)
        }
      }.getOrNull()?.also { bitmap ->
        BitmapMemoryCache.put(cacheKey, bitmap)
      }
    }
    deferred.complete(decoded)
    return decoded
  } catch (error: Throwable) {
    deferred.complete(null)
    throw error
  } finally {
    vehicleImageLoadsInFlight.remove(url, deferred)
  }
}

/**
 * Decode a bitmap byte array bounded to [MAX_VEHICLE_IMAGE_WIDTH] x
 * [MAX_VEHICLE_IMAGE_HEIGHT]. `inJustDecodeBounds` reads the header without
 * allocating, then `inSampleSize` (largest power of two that still covers the
 * target) is applied. The sampled decode is then scaled once to the exact
 * target — the same two-stage approach ComicPlus_Pure uses per page, with
 * [android.graphics.Bitmap.Config.RGB_565] when the source has no alpha so
 * memory stays at half of ARGB_8888.
 */
internal fun decodeVehicleImageSampled(bytes: ByteArray): Bitmap? {
  if (bytes.isEmpty()) return null
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
  val sampleSize = vehicleImageSampleSize(bounds.outWidth, bounds.outHeight)
  val hasAlpha = bounds.outConfig == android.graphics.Bitmap.Config.ARGB_8888 ||
    bounds.outConfig == android.graphics.Bitmap.Config.ALPHA_8 ||
    bounds.outConfig == android.graphics.Bitmap.Config.RGBA_F16
  val decodeOptions = BitmapFactory.Options().apply {
    inSampleSize = sampleSize
    inPreferredConfig =
      if (hasAlpha) android.graphics.Bitmap.Config.ARGB_8888
      else android.graphics.Bitmap.Config.RGB_565
  }
  val sampled = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return null
  if (sampled.width <= MAX_VEHICLE_IMAGE_WIDTH && sampled.height <= MAX_VEHICLE_IMAGE_HEIGHT) {
    return sampled
  }
  val target = vehicleImageTargetSize(sampled.width, sampled.height)
  val scaled = Bitmap.createScaledBitmap(sampled, target.first, target.second, true)
  if (scaled !== sampled) sampled.recycle()
  return scaled
}

// Static draw resources — geometry/brushes are fixed viewBox coordinates, so
// they are built once and shared read-only by every draw pass (the garage
// list renders this stage per vehicle card; per-frame allocation showed up
// as churn during scroll).
private val BrandMarkPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
  color = VehicleBodyLight.toArgb()
  textSize = 32f
  letterSpacing = 8f / 32f // Dart letterSpacing 8 at fontSize 32
  typeface = android.graphics.Typeface.create("Arial", android.graphics.Typeface.BOLD)
}

private val FloorBrush = Brush.radialGradient(
  colors = listOf(Color(0x18BFCAD6), Color(0x00BFCAD6)),
  center = Offset(170f, 145f),
  radius = 110f,
)

private val BodyGradient = Brush.linearGradient(
  colors = listOf(VehicleBodyTop, VehicleBodyLight),
  start = Offset(193f, 50f), // topCenter of rect(46,50,340,130)
  end = Offset(193f, 130f),
)

private val TireGradient = Brush.linearGradient(
  colors = listOf(Color(0xFFE3E8EF), VehicleTireShadow),
  start = Offset(170f, 100f), // topCenter of rect(0,100,340,172)
  end = Offset(170f, 172f),
)

private val EnergyGradient = Brush.linearGradient(
  colors = listOf(Color(0xFF00E0A6), Color(0xFF00A57C)),
  start = Offset(108f, 98.5f), // centerLeft of battery bar
  end = Offset(148f, 98.5f),
)

private val BodyStroke = Stroke(width = 4.5f, join = StrokeJoin.Round)

private val RearMudguardPath = quadPath(46f, 116f, 80f, 80f, 118f, 104f)
private val FrontMudguardPath = quadPath(232f, 112f, 266f, 80f, 304f, 110f)

private val RearBodyPath = Path().apply {
  moveTo(92f, 72f)
  lineTo(150f, 68f)
  quadraticBezierTo(160f, 70f, 162f, 82f)
  lineTo(168f, 120f)
  lineTo(104f, 124f)
  quadraticBezierTo(90f, 104f, 88f, 84f)
  quadraticBezierTo(88f, 75f, 92f, 72f)
  close()
}

private val FrontShieldPath = Path().apply {
  moveTo(214f, 124f)
  quadraticBezierTo(214f, 104f, 224f, 90f)
  quadraticBezierTo(236f, 70f, 250f, 60f)
  lineTo(266f, 54f)
  quadraticBezierTo(272f, 60f, 268f, 74f)
  quadraticBezierTo(260f, 96f, 244f, 112f)
  quadraticBezierTo(232f, 122f, 224f, 124f)
  close()
}

private val SeatPath = Path().apply {
  moveTo(60f, 70f)
  quadraticBezierTo(54f, 58f, 72f, 55f)
  lineTo(150f, 50f)
  quadraticBezierTo(164f, 50f, 160f, 63f)
  lineTo(154f, 70f)
  quadraticBezierTo(150f, 73f, 142f, 72f)
  lineTo(70f, 73f)
  quadraticBezierTo(62f, 73f, 60f, 70f)
  close()
}

private val HeadlightPath = Path().apply {
  moveTo(244f, 96f)
  lineTo(266f, 102f)
  lineTo(263f, 116f)
  lineTo(241f, 110f)
  close()
}

/** Draws the 340x172 vehicle illustration, centered and aspect-scaled. */
fun DrawScope.drawVehicleStage(batteryLevel: Float) {
  val transform = vehicleStageTransform(size.width, size.height) ?: return
  // Pivot must be origin: Compose scale() defaults to the canvas center, which
  // on xxhdpi (s>2) pushes the bike into the left edge - the cold-start flash.
  translate(left = transform.originX, top = transform.originY) {
    scale(scaleX = transform.scale, scaleY = transform.scale, pivot = Offset.Zero) {
      drawFloor()
      drawBrandMark()
      drawVehicle(batteryLevel)
    }
  }
}

private fun DrawScope.drawFloor() {
  drawOval(
    brush = FloorBrush,
    topLeft = Offset(80f, 135f),
    size = Size(180f, 20f),
  )
}

private fun DrawScope.drawBrandMark() {
  drawContext.canvas.nativeCanvas.drawText("TAILG", 110f, -18f + 32f, BrandMarkPaint)
}

private fun DrawScope.drawVehicle(batteryLevel: Float) {
  // --- Rear mudguard ---
  drawPath(
    path = RearMudguardPath,
    color = VehicleTireShadow,
    style = Stroke(width = 9f, cap = StrokeCap.Round),
  )
  // --- Front mudguard ---
  drawPath(
    path = FrontMudguardPath,
    color = VehicleTireShadow,
    style = Stroke(width = 9f, cap = StrokeCap.Round),
  )

  // --- Rear body (seat/battery compartment) ---
  drawPath(RearBodyPath, brush = BodyGradient)
  drawPath(RearBodyPath, color = VehicleFrameColor, style = BodyStroke)

  // --- Battery energy bar (dynamic: scales with batteryLevel) ---
  val barX = 108f
  val barY = 92f
  val barW = 40f
  val barH = 13f
  val fillW = barW * batteryLevel.coerceIn(0f, 1f)
  drawRoundRect(
    color = AppColorsLight.surfaceContainerHigh, // Dart AppColors.card3
    topLeft = Offset(barX, barY),
    size = Size(barW, barH),
    cornerRadius = CornerRadius(4f),
  )
  if (fillW > 0f) {
    drawRoundRect(
      brush = EnergyGradient,
      topLeft = Offset(barX, barY),
      size = Size(fillW, barH),
      cornerRadius = CornerRadius(4f),
    )
  }
  drawRoundRect(
    color = VehicleBatteryBorder,
    topLeft = Offset(barX, barY),
    size = Size(barW, barH),
    cornerRadius = CornerRadius(4f),
    style = Stroke(width = 1f),
  )

  // --- Footboard (low step-through) ---
  drawLine(
    color = VehicleFrameColor,
    start = Offset(150f, 124f),
    end = Offset(214f, 124f),
    strokeWidth = 9f,
    cap = StrokeCap.Round,
  )
  drawLine(
    color = VehicleAccentDark.copy(alpha = 0.5f), // Dart ColorFilter srcOver 0x803A434F
    start = Offset(156f, 124f),
    end = Offset(210f, 124f),
    strokeWidth = 3f,
    cap = StrokeCap.Round,
  )

  // --- Front leg shield + stem ---
  drawPath(FrontShieldPath, brush = BodyGradient)
  drawPath(FrontShieldPath, color = VehicleFrameColor, style = BodyStroke)

  // --- Seat ---
  drawPath(SeatPath, color = VehicleFrameColor)
  drawLine(
    color = VehicleSeatStitch,
    start = Offset(74f, 58f),
    end = Offset(148f, 54f),
    strokeWidth = 2f,
    cap = StrokeCap.Round,
  )

  // --- Handlebar stem + handlebar ---
  drawLine(
    color = VehicleFrameColor,
    start = Offset(258f, 60f),
    end = Offset(278f, 32f),
    strokeWidth = 6.5f,
    cap = StrokeCap.Round,
  )
  drawLine(
    color = VehicleFrameColor,
    start = Offset(266f, 32f),
    end = Offset(296f, 27f),
    strokeWidth = 8f,
    cap = StrokeCap.Round,
  )

  // --- Dashboard ---
  drawRoundRect(
    color = AppColors.inkBtn,
    topLeft = Offset(252f, 40f),
    size = Size(22f, 15f),
    cornerRadius = CornerRadius(4f),
  )
  drawRoundRect(
    color = AppColors.energyGreen.copy(alpha = 0.9f),
    topLeft = Offset(256f, 44f),
    size = Size(14f, 7f),
    cornerRadius = CornerRadius(2f),
  )

  // --- Headlight ---
  drawPath(HeadlightPath, color = VehicleAccentDark)
  drawOval(
    color = VehicleHeadlight,
    topLeft = Offset(249f, 100f),
    size = Size(10f, 11f),
  )

  // --- Wheels ---
  drawWheel(82f, 130f, TireGradient)
  drawWheel(268f, 130f, TireGradient)
}

private fun DrawScope.drawWheel(cx: Float, cy: Float, tireBrush: Brush) {
  // Tire
  drawCircle(brush = tireBrush, radius = 30f, center = Offset(cx, cy))
  drawCircle(
    color = VehicleFrameColor,
    radius = 30f,
    center = Offset(cx, cy),
    style = Stroke(width = 5.5f),
  )
  // Hub
  drawCircle(color = AppColors.pageBgBot, radius = 13.5f, center = Offset(cx, cy))
  drawCircle(
    color = VehicleHubStroke,
    radius = 13.5f,
    center = Offset(cx, cy),
    style = Stroke(width = 2f),
  )
  // Axle
  drawCircle(color = VehicleFrameColor, radius = 3.6f, center = Offset(cx, cy))
  // Spokes
  drawLine(VehicleSpoke, Offset(cx, cy - 12f), Offset(cx, cy + 12f), strokeWidth = 2f)
  drawLine(VehicleSpoke, Offset(cx - 12f, cy), Offset(cx + 12f, cy), strokeWidth = 2f)
  drawLine(VehicleSpoke, Offset(cx - 8.5f, cy - 8.5f), Offset(cx + 8.5f, cy + 8.5f), strokeWidth = 2f)
  drawLine(VehicleSpoke, Offset(cx - 8.5f, cy + 8.5f), Offset(cx + 8.5f, cy - 8.5f), strokeWidth = 2f)
}

/** Dart `_cubicPath` — quadratic curve from (x1,y1) via (cx,cy) to (x2,y2). */
private fun quadPath(x1: Float, y1: Float, cx: Float, cy: Float, x2: Float, y2: Float): Path =
  Path().apply {
    moveTo(x1, y1)
    quadraticBezierTo(cx, cy, x2, y2)
  }

/**
 * Wrapper for the vehicle stage with proper sizing (Dart `VehicleStage`).
 * Remote `carPhoto` values stay empty until decoded so the vector fallback
 * cannot flash at a wrong offset; failed or empty URLs use the local painter.
 * Drawing is also skipped while the box is still a leftover landscape width
 * (rotation) so the left half of the bike cannot appear as a rear-wheel sliver.
 * The ready check runs in draw, not composition, so collapse constraint
 * changes do not rebuild the image loader.
 */
@Composable
fun VehicleStage(
  modifier: Modifier = Modifier,
  batteryLevel: Float = 0.84f,
  height: Dp = 200.dp,
  imageUrl: String? = null,
) {
  val density = LocalDensity.current
  val viewportWidthPx = with(density) {
    LocalConfiguration.current.screenWidthDp.dp.toPx()
  }
  val expectedHeightPx = with(density) { height.toPx() }
  // Dart officialHorizontalPadding = 20.0
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .padding(horizontal = 20.dp)
      .clipToBounds()
      .drawWithContent {
        val ready = vehicleStageLayoutReady(
          widthPx = size.width,
          heightPx = size.height,
          viewportWidthPx = viewportWidthPx,
          expectedHeightPx = expectedHeightPx,
        )
        if (ready) {
          drawContent()
        }
      },
  ) {
    VehicleImageOrFallback(
      imageUrl = imageUrl,
      batteryLevel = batteryLevel,
      modifier = Modifier.fillMaxSize(),
    )
  }
}

private sealed interface VehicleImageLoad {
  data object Pending : VehicleImageLoad
  data class Ready(val bitmap: Bitmap) : VehicleImageLoad
  data object Fallback : VehicleImageLoad
}

@Composable
internal fun VehicleImageOrFallback(
  imageUrl: String?,
  batteryLevel: Float,
  modifier: Modifier = Modifier,
) {
  val normalizedUrl = imageUrl?.trim().orEmpty()
  val remote = isRemoteVehicleImageUrl(normalizedUrl)
  // Register the shared trim callbacks lazily (idempotent) so the cache works
  // even when this composable is exercised outside the Application path.
  LocalContext.current.applicationContext.let(BitmapMemoryCache::register)
  val load by produceState<VehicleImageLoad>(
    initialValue = if (remote) VehicleImageLoad.Pending else VehicleImageLoad.Fallback,
    key1 = normalizedUrl,
  ) {
    value = loadVehicleImage(normalizedUrl)?.let { VehicleImageLoad.Ready(it) }
      ?: VehicleImageLoad.Fallback
  }
  when (val current = load) {
    VehicleImageLoad.Pending -> Box(modifier)
    is VehicleImageLoad.Ready -> {
      val imageBitmap = remember(current.bitmap) { current.bitmap.asImageBitmap() }
      Image(
        bitmap = imageBitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = Alignment.Center,
        modifier = modifier.clipToBounds(),
      )
    }
    VehicleImageLoad.Fallback -> Canvas(modifier = modifier.clipToBounds()) {
      drawVehicleStage(batteryLevel)
    }
  }
}
