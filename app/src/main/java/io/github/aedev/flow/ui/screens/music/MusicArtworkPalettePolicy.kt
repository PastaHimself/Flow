package io.github.aedev.flow.ui.screens.music

import io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle

/** Avoids artwork palette work when the expanded player cannot display it. */
object MusicArtworkPalettePolicy {
    fun shouldExtract(
        isExpanded: Boolean,
        backgroundStyle: MusicPlayerBackgroundStyle,
    ): Boolean = isExpanded && backgroundStyle != MusicPlayerBackgroundStyle.DEFAULT
}
