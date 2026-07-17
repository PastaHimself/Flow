package io.github.aedev.flow.ui.screens.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PremiumControlsOverlayTest {
    @Test
    fun overlayActionsUseTheMinimumTouchTarget() {
        assertEquals(48.dp, OverlayActionButtonSize)
    }
}
