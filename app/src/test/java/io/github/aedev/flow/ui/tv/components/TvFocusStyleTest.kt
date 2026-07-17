package io.github.aedev.flow.ui.tv.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TvFocusStyleTest {
    @Test
    fun focusedCardUsesVisibleScale() {
        assertEquals(1.05f, TvFocusStyle.focusedScale)
    }

    @Test
    fun unfocusedCardDoesNotScale() {
        assertEquals(1f, TvFocusStyle.unfocusedScale)
    }
}
