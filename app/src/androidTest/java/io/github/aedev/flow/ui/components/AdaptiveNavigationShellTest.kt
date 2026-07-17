package io.github.aedev.flow.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AdaptiveNavigationShellTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mediumWidthUsesNavigationRail() {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.requiredWidth(700.dp)) {
                    AdaptiveNavigationShell(
                        compactNavigation = { Text("bottom") },
                        railNavigation = { Text("rail") },
                    ) {
                        Text("content")
                    }
                }
            }
        }

        composeRule.onNodeWithText("rail").assertExists()
        composeRule.onNodeWithText("bottom").assertDoesNotExist()
    }
}
