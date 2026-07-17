package io.github.aedev.flow.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared dimensions for Flow's Material 3 UI components. */
object FlowSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp

    val all: List<Dp> = listOf(xxs, xs, sm, md, lg, xl)
}

object FlowTouchTarget {
    val minimum: Dp = 48.dp
}

object FlowIconSize {
    val compact: Dp = 20.dp
    val standard: Dp = 24.dp
}

object FlowShapeTokens {
    val compact: Dp = 8.dp
    val control: Dp = 12.dp
    val card: Dp = 16.dp
    val sheet: Dp = 24.dp
}
