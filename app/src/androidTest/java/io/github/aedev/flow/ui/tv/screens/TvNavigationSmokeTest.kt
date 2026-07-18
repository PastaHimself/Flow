package io.github.aedev.flow.ui.tv.screens

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.aedev.flow.ui.tv.components.TvNavigationRail
import io.github.aedev.flow.ui.tv.navigation.TvDestination
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvNavigationSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectingLibraryRoutesToLibraryDestination() {
        var selected = TvDestination.HOME
        composeRule.setContent {
            MaterialTheme {
                TvNavigationRail(
                    selected = selected,
                    onSelected = { selected = it },
                )
            }
        }

        composeRule.onNodeWithTag(TvDestination.LIBRARY.testTag).performClick()

        composeRule.runOnIdle {
            assertEquals(TvDestination.LIBRARY, selected)
        }
    }
}
