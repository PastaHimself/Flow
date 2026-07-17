package io.github.aedev.flow.ui.screens.subscriptions

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import org.junit.Rule
import org.junit.Test

class ContentScreenAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun subscriptionGroupActionsHaveAccessibleTargets() {
        val group = SubscriptionGroup(name = "Favorites", channelIds = emptyList())
        composeRule.setContent {
            MaterialTheme {
                SubscriptionGroupRow(group, false, false, {}, {}, {}, {})
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.edit_group))
            .assertHeightIsAtLeast(48.dp)
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.delete_group))
            .assertHeightIsAtLeast(48.dp)
    }
}
