package io.github.aedev.flow.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.onboarding.OnboardingBottomBar
import org.junit.Rule
import org.junit.Test

class OnboardingAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun onboardingContinueHas48DpTarget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            MaterialTheme {
                OnboardingBottomBar(
                    isFirstStep = false,
                    isLastStep = false,
                    canAdvance = true,
                    onBack = {},
                    onNext = {},
                    onSkip = {},
                )
            }
        }

        composeRule
            .onNodeWithText(context.getString(R.string.onboarding_btn_continue))
            .assertHeightIsAtLeast(48.dp)
    }
}
