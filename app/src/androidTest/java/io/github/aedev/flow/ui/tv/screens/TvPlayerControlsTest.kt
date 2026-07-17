package io.github.aedev.flow.ui.tv.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TvPlayerControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun closePlayerActionInvokesCallback() {
        var closed = 0
        composeRule.setContent {
            MaterialTheme {
                TvPlayerActionRow(
                    onClose = { closed++ },
                    onPlayPause = {},
                    onMore = {},
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.tv_player_close))
            .performClick()

        assertEquals(1, closed)
    }
}
