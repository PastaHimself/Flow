package io.github.aedev.flow.ui.screens.music

import io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlayerPalettePolicyTest {
    @Test
    fun paletteExtractionOnlyRunsForExpandedArtworkBackground() {
        assertFalse(
            MusicArtworkPalettePolicy.shouldExtract(
                isExpanded = false,
                backgroundStyle = MusicPlayerBackgroundStyle.BLUR_GRADIENT,
            )
        )
        assertTrue(
            MusicArtworkPalettePolicy.shouldExtract(
                isExpanded = true,
                backgroundStyle = MusicPlayerBackgroundStyle.BLUR_GRADIENT,
            )
        )
    }
}
