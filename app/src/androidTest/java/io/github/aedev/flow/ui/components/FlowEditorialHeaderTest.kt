package io.github.aedev.flow.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class FlowEditorialHeaderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectedTabHasSelectedSemantics() {
        composeRule.setContent {
            MaterialTheme {
                FlowEditorialHeader(
                    title = "Channel",
                    tabs = listOf("Videos", "Shorts"),
                    selectedTab = 0,
                    onTabSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Videos").assertIsSelected()
    }
}
