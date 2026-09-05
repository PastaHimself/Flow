package io.github.aedev.flow.ui.tv

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class TvFeedbackRegressionTest {
    @Test
    fun uiModeChangesDoNotClearFocusBeforePersistence() {
        val source = readMainSource("io/github/aedev/flow/ui/tv/screens/settings/TvSystemSettingsPanes.kt")
        assertThat(source).doesNotContain("clearFocus")
        assertThat(source).doesNotContain("withFrameNanos")
        assertThat(source).contains("modePreferences.setMode(mode)")
    }

    @Test
    fun playerChannelActionRoutesToTvChannelDestination() {
        val appSource = readMainSource("io/github/aedev/flow/ui/tv/FlowTvApp.kt")
        val overlaySource = readMainSource("io/github/aedev/flow/ui/tv/player/TvPlayerOverlay.kt")
        assertThat(appSource).contains("navController.navigate(TvRoutes.channel(channelRef))")
        assertThat(appSource).contains("LocalTvPlayerChannelAction provides")
        assertThat(overlaySource).contains("channelAction.onOpenChannel(channelRef)")
        assertThat(overlaySource).contains("TvButton(")
    }

    private fun readMainSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("app/src/main/java/$relativePath"),
            File("../app/src/main/java/$relativePath"),
        )
        val sourceFile = candidates.firstOrNull(File::isFile)
        checkNotNull(sourceFile) { "Could not locate production source: $relativePath" }
        return sourceFile.readText()
    }
}
