package io.github.aedev.flow.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

object FlowMotion {
    const val feedbackDurationMillis = 150
    const val shellDurationMillis = 240

    fun resolveDurationMillis(reducedMotion: Boolean, durationMillis: Int): Int =
        if (reducedMotion) 0 else durationMillis

    fun <T> feedbackSpec(reducedMotion: Boolean = false): TweenSpec<T> =
        tween(durationMillis = resolveDurationMillis(reducedMotion, feedbackDurationMillis))

    fun <T> shellSpec(reducedMotion: Boolean = false): TweenSpec<T> =
        tween(durationMillis = resolveDurationMillis(reducedMotion, shellDurationMillis))

    fun <T> tweenSpec(
        durationMillis: Int,
        reducedMotion: Boolean = false,
    ): AnimationSpec<T> = tween(durationMillis = resolveDurationMillis(reducedMotion, durationMillis))
}
