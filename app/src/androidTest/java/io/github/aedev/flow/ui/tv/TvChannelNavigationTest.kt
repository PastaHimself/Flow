package io.github.aedev.flow.ui.tv

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.aedev.flow.ui.tv.components.TvButton
import io.github.aedev.flow.ui.tv.navigation.TvRoutes
import io.github.aedev.flow.ui.tv.navigation.tvChannelDestination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvChannelNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun remoteOpensChannelWithoutDecodingItsUrlAgain() {
        val channelRef = "https://www.youtube.com/@caf%C3%A9?source=a+b&percent=%25"
        compose.setContent {
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(navController, startDestination = "player") {
                    composable("player") {
                        val focus = remember { FocusRequester() }
                        val inputModeManager = LocalInputModeManager.current
                        TvButton(
                            text = "Open channel",
                            onClick = { navController.navigate(TvRoutes.channel(channelRef)) },
                            modifier = Modifier.focusRequester(focus),
                        )
                        LaunchedEffect(Unit) {
                            inputModeManager.requestInputMode(InputMode.Keyboard)
                            focus.requestFocus()
                        }
                    }
                    tvChannelDestination { Text(it) }
                }
            }
        }

        compose.onNodeWithText("Open channel").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        compose.onNodeWithText(channelRef).assertIsDisplayed()
    }
}
