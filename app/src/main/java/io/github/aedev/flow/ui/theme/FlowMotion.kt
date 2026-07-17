package io.github.aedev.flow.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween

/** Central timing policy for nonessential interface motion. */
object FlowMotion {
    const val feedbackDurationMillis = 150
    const val shellDurationMillis = 240

    fun <T> feedbackSpec(reducedMotion: Boolean = false): AnimationSpec<T> =
        tween(durationMillis = if (reducedMotion) 0 else feedbackDurationMillis)

    fun <T> shellSpec(reducedMotion: Boolean = false): AnimationSpec<T> =
        tween(durationMillis = if (reducedMotion) 0 else shellDurationMillis)
}
