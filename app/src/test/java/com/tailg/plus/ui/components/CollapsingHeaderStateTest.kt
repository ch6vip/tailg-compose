package com.tailg.plus.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CollapsingHeaderStateTest {

  @Test
  fun preScrollCollapsesThenClampsToRange() {
    val state = CollapsingHeaderState(rangePx = 224f)
    assertEquals(0f, state.fraction)
    assertEquals(-100f, state.consumePreScroll(-100f))
    assertEquals(100f, state.offsetPx, 0.001f)
    assertEquals(-124f, state.consumePreScroll(-200f))
    assertEquals(224f, state.offsetPx, 0.001f)
    assertEquals(1f, state.fraction)
    assertEquals(0f, state.consumePreScroll(-10f))
  }

  @Test
  fun postScrollExpandsOnlyTowardStart() {
    val state = CollapsingHeaderState(rangePx = 224f)
    state.consumePreScroll(-224f)
    assertEquals(0f, state.consumePostScroll(-20f))
    assertEquals(80f, state.consumePostScroll(80f), 0.001f)
    assertEquals(144f, state.offsetPx, 0.001f)
    assertEquals(144f, state.consumePostScroll(400f), 0.001f)
    assertEquals(0f, state.offsetPx, 0.001f)
    assertEquals(0f, state.fraction)
  }

  @Test
  fun headerDragCollapsesBeforeListMoves() {
    val state = CollapsingHeaderState(rangePx = 100f)
    var listDelta = 0f
    val consumed = state.consumeScrollableDelta(140f) { leftover ->
      listDelta = leftover
      leftover
    }
    assertEquals(140f, consumed, 0.001f)
    assertEquals(100f, state.offsetPx, 0.001f)
    assertEquals(40f, listDelta, 0.001f)
  }

  @Test
  fun headerDragExpandsOnlyAfterListIsAtTop() {
    val state = CollapsingHeaderState(rangePx = 100f)
    state.consumePreScroll(-100f)
    var remainingList = 30f
    val consumed = state.consumeScrollableDelta(-80f) { delta ->
      val take = delta.coerceAtLeast(-remainingList)
      remainingList += take
      take
    }
    assertEquals(-80f, consumed, 0.001f)
    assertEquals(50f, state.offsetPx, 0.001f)
    assertEquals(0f, remainingList, 0.001f)
  }

  @Test
  fun updateRangeCoercesOffsetWithoutResettingWhenUnchanged() {
    val state = CollapsingHeaderState(rangePx = 200f)
    state.consumePreScroll(-150f)
    state.updateRange(200f)
    assertEquals(150f, state.offsetPx, 0.001f)
    state.updateRange(80f)
    assertEquals(80f, state.offsetPx, 0.001f)
    assertEquals(80f, state.rangePx, 0.001f)
  }
}
