package io.github.aedev.flow.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class NavigationTitleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sectionTitleIsAnnouncedAsHeading() {
        composeRule.setContent {
            MaterialTheme {
                SectionTitle(title = "Recently added")
            }
        }

        composeRule
            .onNodeWithText("Recently added")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }
}
