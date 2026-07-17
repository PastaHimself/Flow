package io.github.aedev.flow.ui.tv.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvScreenStatesTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun errorStateExposesRecoveryAction() {
        var retries = 0
        composeRule.setContent {
            MaterialTheme {
                TvMessageState(title = "Offline", onRetry = { retries++ })
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.retry))
            .performClick()

        assertEquals(1, retries)
    }
}
