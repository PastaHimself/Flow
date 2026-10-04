package io.github.aedev.flow.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class DesktopSettingsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun persistsDesktopAppearanceAndNavigationPreferences() {
        val store = DesktopSettingsStore(temporaryFolder.root.toPath())
        val settings =
            DesktopSettings(
                darkTheme = false,
                showHome = false,
                showShorts = false,
                showMusic = true,
                showSearch = true,
                showExplore = true,
            )

        store.save(settings)

        assertEquals(settings, DesktopSettingsStore(temporaryFolder.root.toPath()).load())
    }

    @Test
    fun malformedSettingsAreNotSilentlyOverwritten() {
        val store = DesktopSettingsStore(temporaryFolder.root.toPath())
        Files.createDirectories(store.file.parent)
        Files.writeString(store.file, "not-json")

        assertEquals(DesktopSettings(), store.load())
        assertNotNull(store.loadError)
        assertTrue(runCatching { store.save(DesktopSettings(darkTheme = false)) }.exceptionOrNull() is IllegalStateException)
        assertEquals("not-json", Files.readString(store.file))
    }
}
