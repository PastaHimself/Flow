package io.github.aedev.flow.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowDesignTokensTest {
    @Test
    fun `minimum touch target is 48 dp`() {
        assertEquals(48.dp, FlowTouchTarget.minimum)
    }

    @Test
    fun `spacing scale uses the shared rhythm`() {
        assertEquals(
            listOf(4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp),
            FlowSpacing.all,
        )
    }
}
