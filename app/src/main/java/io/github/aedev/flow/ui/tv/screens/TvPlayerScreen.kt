package io.github.aedev.flow.ui.tv.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.components.VideoPlayerSurface
import io.github.aedev.flow.ui.tv.components.TvFocusableCard
import io.github.aedev.flow.ui.tv.input.TvPlayerAction
import io.github.aedev.flow.ui.tv.input.TvPlayerKeyMapper
import kotlinx.coroutines.delay

private const val TV_SEEK_INCREMENT_MS = 10_000L

@Composable
fun TvPlayerScreen(
    video: Video,
    viewModel: VideoPlayerViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val manager = remember { EnhancedPlayerManager.getInstance() }
    val playerState by manager.playerState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(video.id) {
        while (true) {
            position = manager.getCurrentPosition().coerceAtLeast(0L)
            duration = manager.getDuration().coerceAtLeast(0L)
            delay(500L)
        }
    }

    fun perform(action: TvPlayerAction) {
        when (action) {
            TvPlayerAction.TOGGLE_PLAYBACK -> if (playerState.isPlaying) manager.pause() else manager.play()
            TvPlayerAction.PLAY -> manager.play()
            TvPlayerAction.PAUSE -> manager.pause()
            TvPlayerAction.SEEK_BACK -> manager.seekTo((manager.getCurrentPosition() - TV_SEEK_INCREMENT_MS).coerceAtLeast(0L))
            TvPlayerAction.SEEK_FORWARD -> manager.seekTo(manager.getCurrentPosition() + TV_SEEK_INCREMENT_MS)
        }
    }

    BackHandler(onBack = onClose)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val action = TvPlayerKeyMapper.map(event.nativeKeyEvent.keyCode) ?: return@onPreviewKeyEvent false
                perform(action)
                true
            },
    ) {
        VideoPlayerSurface(
            video = video,
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 48.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = video.channelName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            TvPlayerActionRow(
                onClose = onClose,
                onPlayPause = { perform(TvPlayerAction.TOGGLE_PLAYBACK) },
                onMore = {},
                onSeekBack = { perform(TvPlayerAction.SEEK_BACK) },
                onSeekForward = { perform(TvPlayerAction.SEEK_FORWARD) },
                playPauseLabel = if (playerState.isPlaying) {
                    stringResource(R.string.pause)
                } else {
                    stringResource(R.string.play)
                },
                isPlaying = playerState.isPlaying,
                showMore = false,
            )
        }

        if (uiState.isLoading || playerState.isBuffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        uiState.error?.let { error ->
            Text(
                text = error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(24.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
internal fun TvPlayerActionRow(
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    onSeekBack: (() -> Unit)? = null,
    onSeekForward: (() -> Unit)? = null,
    playPauseLabel: String? = null,
    isPlaying: Boolean = false,
    showMore: Boolean = true,
) {
    val resolvedPlayPauseLabel = playPauseLabel ?: stringResource(R.string.play)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onSeekBack != null) {
            PlayerActionCard(
                label = stringResource(R.string.tv_player_rewind),
                icon = { Icon(Icons.Outlined.Replay10, contentDescription = null) },
                onClick = onSeekBack,
            )
        }
        PlayerActionCard(
            label = resolvedPlayPauseLabel,
            icon = {
                Icon(
                    imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = null,
                )
            },
            onClick = onPlayPause,
        )
        if (onSeekForward != null) {
            PlayerActionCard(
                label = stringResource(R.string.tv_player_fast_forward),
                icon = { Icon(Icons.Outlined.FastForward, contentDescription = null) },
                onClick = onSeekForward,
            )
        }
        if (showMore) {
            PlayerActionCard(
                label = stringResource(R.string.more_options),
                icon = { Icon(Icons.Outlined.MoreVert, contentDescription = null) },
                onClick = onMore,
            )
        }
        PlayerActionCard(
            label = stringResource(R.string.tv_player_close),
            icon = { Icon(Icons.Outlined.Close, contentDescription = null) },
            onClick = onClose,
        )
    }
}

@Composable
private fun PlayerActionCard(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    TvFocusableCard(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = label },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(label)
        }
    }
}
