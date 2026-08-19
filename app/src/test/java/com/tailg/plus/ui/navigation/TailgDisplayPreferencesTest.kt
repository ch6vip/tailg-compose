package com.tailg.plus.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TailgDisplayPreferencesTest {

    @Test
    fun resolveAppFontScale_matchesOriginalBounds() {
        assertEquals(0.85f, resolveAppFontScale(0.7f, true), 0f)
        assertEquals(1.2f, resolveAppFontScale(1.2f, true), 0f)
        assertEquals(1.5f, resolveAppFontScale(2f, true), 0f)
        assertEquals(1f, resolveAppFontScale(1.4f, false), 0f)
    }
}
