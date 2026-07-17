package io.github.aedev.flow.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class PersistentMiniMusicPlayerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun miniPlayerExposesExpandAndPlayControlsAt48Dp() {
        composeRule.setContent {
            MaterialTheme {
                MiniPlayerLayout(
                    title = "Track",
                    artworkUrl = "",
                    progress = 0.5f,
                    isPlaying = true,
                    onExpand = {},
                    onPlayPause = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Expand player")
            .assertHeightIsAtLeast(48.dp)
        composeRule
            .onNodeWithContentDescription("Pause")
            .assertHeightIsAtLeast(48.dp)
    }
}
