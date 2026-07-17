package io.github.aedev.flow.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import org.junit.Rule
import org.junit.Test

class SettingsAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsSearchUsesResourceBackedClearAction() {
        val label = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.settings_search_clear)

        composeRule.setContent {
            MaterialTheme {
                SettingsSearchTopBar(
                    query = "flow",
                    onQueryChange = {},
                    onClose = {},
                    onClear = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(label).assertExists()
    }
}
