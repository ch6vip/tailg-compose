package com.tailg.plus.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Pinned collapsing header for the Cyber control home.
 *
 * Dart uses `SliverPersistentHeader(pinned: true)`: one scroll offset shrinks
 * the header in place from [rangePx] below expanded height down to collapsed,
 * then the list moves. Compose has no sliver protocol, so this state is the
 * equivalent: collapse first on the way to the end, expand only after the
 * list is back at the top.
 *
 * [offsetPx] is snapshot state. Read it only inside the header composable so
 * list content (grid / map) does not recompose per collapse pixel.
 */
class CollapsingHeaderState(rangePx: Float) {
  var rangePx by mutableFloatStateOf(rangePx.coerceAtLeast(1f))
    private set

  var offsetPx by mutableFloatStateOf(0f)
    private set

  val fraction: Float
    get() = (offsetPx / rangePx).coerceIn(0f, 1f)

  fun updateRange(newRangePx: Float) {
    val next = newRangePx.coerceAtLeast(1f)
    if (next == rangePx) return
    rangePx = next
    offsetPx = offsetPx.coerceIn(0f, rangePx)
  }

  /**
   * Nested-scroll `available.y`: negative is toward the end (collapse).
   * Returns consumed y, same sign as [availableY].
   */
  fun consumePreScroll(availableY: Float): Float {
    if (availableY >= 0f) return 0f
    val old = offsetPx
    offsetPx = (old - availableY).coerceIn(0f, rangePx)
    return old - offsetPx
  }

  /**
   * Leftover nested-scroll `available.y` after the list: positive is toward
   * the start (expand). Returns consumed y, same sign as [availableY].
   */
  fun consumePostScroll(availableY: Float): Float {
    if (availableY <= 0f) return 0f
    val old = offsetPx
    offsetPx = (old - availableY).coerceIn(0f, rangePx)
    return old - offsetPx
  }

  /**
   * Header-local [androidx.compose.foundation.gestures.ScrollableState] delta.
   * Positive is toward the end. Remainder is forwarded to the list so dragging
   * the pinned vehicle still scrolls the page (Dart sliver behavior).
   */
  fun consumeScrollableDelta(delta: Float, dispatchToList: (Float) -> Float): Float {
    if (delta > 0f) {
      val headerConsumed = -consumePreScroll(-delta)
      return headerConsumed + dispatchToList(delta - headerConsumed)
    }
    if (delta < 0f) {
      val listConsumed = dispatchToList(delta)
      val leftover = delta - listConsumed
      return listConsumed + (-consumePostScroll(-leftover))
    }
    return 0f
  }

  val listConnection: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      val consumed = consumePreScroll(available.y)
      return if (consumed == 0f) Offset.Zero else Offset(0f, consumed)
    }

    override fun onPostScroll(
      consumed: Offset,
      available: Offset,
      source: NestedScrollSource,
    ): Offset {
      val extra = consumePostScroll(available.y)
      return if (extra == 0f) Offset.Zero else Offset(0f, extra)
    }
  }
}
