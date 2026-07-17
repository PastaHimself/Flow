package io.github.aedev.flow.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FlowScreenStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun errorStateExposesRetryAction() {
        var retryInvoked = false

        composeRule.setContent {
            MaterialTheme {
                FlowErrorState(
                    title = "Couldn't refresh",
                    onRetry = { retryInvoked = true },
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(R.string.retry)).performClick()

        assertTrue(retryInvoked)
    }
}
