package io.github.aedev.flow.ui.screens.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImportErrorMappingTest {
    @Test
    fun preservesKnownImportErrorCodes() {
        assertThat(safeImportErrorMessage("no_entries", "fallback")).isEqualTo("no_entries")
        assertThat(safeImportErrorMessage("no_videos", "fallback")).isEqualTo("no_videos")
        assertThat(safeImportErrorMessage("no_content", "fallback")).isEqualTo("no_content")
        assertThat(safeImportErrorMessage("invalid_format", "fallback")).isEqualTo("invalid_format")
    }

    @Test
    fun replacesArbitraryMessagesWithFallback() {
        assertThat(safeImportErrorMessage("database path and stack details", "fallback"))
            .isEqualTo("fallback")
        assertThat(safeImportErrorMessage(null, "fallback")).isEqualTo("fallback")
    }
}
