package io.github.aedev.flow.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowDesignTokensTest {
    @Test
    fun minimumTouchTargetIs48Dp() {
        assertEquals(48.dp, FlowTouchTarget.minimum)
    }

    @Test
    fun spacingScaleIsStable() {
        assertEquals(listOf(4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp), FlowSpacing.all)
    }
}
