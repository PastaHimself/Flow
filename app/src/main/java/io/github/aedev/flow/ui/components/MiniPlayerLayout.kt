package io.github.aedev.flow.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.FlowIconSize
import io.github.aedev.flow.ui.theme.FlowShapeTokens
import io.github.aedev.flow.ui.theme.FlowSpacing
import io.github.aedev.flow.ui.theme.FlowTouchTarget

@Composable
internal fun MiniPlayerLayout(
    title: String,
    artworkUrl: String,
    progress: Float,
    isPlaying: Boolean,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    artist: String? = null,
) {
    val expandLabel = "Expand player"
    val playPauseLabel = stringResource(if (isPlaying) R.string.pause else R.string.play)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FlowTouchTarget.minimum)
            .semantics { contentDescription = expandLabel }
            .clickable(onClick = onExpand),
        shape = RoundedCornerShape(FlowShapeTokens.card),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FlowSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(FlowSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(FlowTouchTarget.minimum)
                        .clip(RoundedCornerShape(FlowShapeTokens.control)),
                    contentScale = ContentScale.Crop,
                )
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(FlowSpacing.xxs),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!artist.isNullOrBlank()) {
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    modifier = Modifier
                        .size(FlowTouchTarget.minimum)
                        .semantics { contentDescription = playPauseLabel },
                    onClick = onPlayPause,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(FlowIconSize.standard),
                    )
                }
            }
        }
    }
}
