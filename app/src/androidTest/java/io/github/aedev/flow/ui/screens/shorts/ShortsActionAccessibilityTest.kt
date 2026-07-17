package io.github.aedev.flow.ui.screens.shorts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class ShortsActionAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shortsActionsHave48DpTargetsAndLabels() {
        composeRule.setContent {
            MaterialTheme {
                ShortsActionButton(
                    icon = Icons.Default.Share,
                    text = "Share",
                    contentDescription = "Share",
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Share").assertHeightIsAtLeast(48.dp)
    }
}
