package io.github.aedev.flow.ui.tv.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.FlowShapeTokens

/** Shared focus treatment for TV cards and controls. */
object TvFocusStyle {
    const val focusedScale = 1.05f
    const val unfocusedScale = 1f
    val focusedBorderWidth = 2.dp
    val focusedElevation = 8.dp
    val unfocusedElevation = 1.dp
}

/** Material 3 card with a visible ten-foot focus state. */
@Composable
fun TvFocusableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) TvFocusStyle.focusedScale else TvFocusStyle.unfocusedScale,
        animationSpec = FlowMotion.feedbackSpec(),
        label = "tvCardScale",
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(FlowShapeTokens.card),
        colors = CardDefaults.cardColors(
            containerColor = if (focused) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            contentColor = if (focused) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
        border = if (focused) {
            BorderStroke(TvFocusStyle.focusedBorderWidth, MaterialTheme.colorScheme.outline)
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (focused) TvFocusStyle.focusedElevation else TvFocusStyle.unfocusedElevation,
        ),
    ) {
        Box(content = content)
    }
}
