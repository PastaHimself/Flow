package io.github.aedev.flow.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

internal fun pickMediaFile(): Path? {
    val dialog = FileDialog(null as Frame?, "Open media", FileDialog.LOAD)
    return try {
        dialog.isVisible = true
        val file = dialog.file ?: return null
        Path.of(dialog.directory, file)
    } finally {
        dialog.dispose()
    }
}
