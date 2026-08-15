package com.tailg.plus.data.model

import com.tailg.plus.util.formatFixed
import java.net.URI
import kotlin.math.abs

/**
 * Port of `lib/models/geo_coordinate.dart` (top-level helpers).
 *
 * No data classes here — the Dart file only exposes pure functions.
 * `googleMapsSearchUri` maps Dart `Uri.https` to `java.net.URI`.
 */
fun formatCoordinateText(latitude: Double, longitude: Double): String =
    "${formatFixed(latitude, 6)}, ${formatFixed(longitude, 6)}"

/** External Google Maps search URI for a WGS84 coordinate pair. */
fun googleMapsSearchUri(latitude: Double, longitude: Double): URI =
    URI.create("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")

fun isZeroCoordinate(
    latitude: Double,
    longitude: Double,
    tolerance: Double = 0.0,
): Boolean {
    val threshold = abs(tolerance)
    if (threshold == 0.0) return latitude == 0.0 && longitude == 0.0
    return abs(latitude) < threshold && abs(longitude) < threshold
}
