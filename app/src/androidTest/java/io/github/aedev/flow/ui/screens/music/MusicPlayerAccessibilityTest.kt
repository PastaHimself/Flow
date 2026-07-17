package io.github.aedev.flow.ui.screens.music

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.screens.music.player.PlayerTopBar
import org.junit.Rule
import org.junit.Test

class MusicPlayerAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun musicPlayerMoreControlHasAccessibleTarget() {
        composeRule.setContent {
            MaterialTheme {
                PlayerTopBar(
                    playingFrom = "Flow",
                    onBackClick = {},
                    onMoreOptionsClick = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("More options")
            .assertHeightIsAtLeast(48.dp)
    }
}
