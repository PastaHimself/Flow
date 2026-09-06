package io.github.aedev.flow.ui.tv

import android.content.ActivityNotFoundException
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.aedev.flow.ui.tv.screens.launchTvImportPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvImportPickerTest {
    @Test
    fun reportsMissingDocumentPickerWithoutCrashing() {
        assertFalse(launchTvImportPicker { throw ActivityNotFoundException("No document picker") })
    }

    @Test
    fun launchesAvailablePickerOnce() {
        var launches = 0

        assertTrue(launchTvImportPicker { launches++ })
        assertEquals(1, launches)
    }
}
