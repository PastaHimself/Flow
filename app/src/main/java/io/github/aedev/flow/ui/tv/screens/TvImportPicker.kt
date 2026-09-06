package io.github.aedev.flow.ui.tv.screens

import android.content.ActivityNotFoundException

internal fun launchTvImportPicker(launch: () -> Unit): Boolean =
    try {
        launch()
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
