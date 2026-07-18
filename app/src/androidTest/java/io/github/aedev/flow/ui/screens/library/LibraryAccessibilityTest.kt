package io.github.aedev.flow.ui.screens.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class LibraryAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun libraryDestinationUsesAccessibleButtonTarget() {
        composeRule.setContent {
            MaterialTheme {
                LibraryCard(
                    icon = Icons.Outlined.History,
                    title = "History",
                    subtitle = "3 videos",
                    onClick = {},
                )
            }
        }

        composeRule
            .onNodeWithText("History")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }
}
