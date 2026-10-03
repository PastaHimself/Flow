package io.github.aedev.flow.desktop

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.model.Video

@Composable
internal fun DesktopPlayerBar(
    video: Video?,
    player: DesktopMpvPlayer,
    paused: Boolean,
    onPauseToggle: () -> Unit,
    onStop: () -> Unit,
) {
    if (video == null) return
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                video.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(video.channelName, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = { player.seekBy(-10.0) }) {
                Icon(Icons.Default.Replay10, contentDescription = "Seek back 10 seconds")
            }
            IconButton(onClick = onPauseToggle) {
                Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = if (paused) "Resume" else "Pause")
            }
            IconButton(onClick = { player.seekBy(10.0) }) {
                Icon(Icons.Default.Forward10, contentDescription = "Seek forward 10 seconds")
            }
            IconButton(onClick = onStop) { Icon(Icons.Default.Stop, contentDescription = "Stop") }
        }
    }
}
