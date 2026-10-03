package io.github.aedev.flow.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.data.model.Video
import java.util.Locale

@Composable
internal fun ScreenColumn(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
internal fun VideoList(
    videos: List<Video>,
    savedIds: Set<String>,
    onPlay: (Video) -> Unit,
    onToggleSaved: (Video) -> Unit,
    subscribedChannelIds: Set<String> = emptySet(),
    onDownload: ((Video) -> Unit)? = null,
    onToggleSubscription: ((Video) -> Unit)? = null,
    onCopyLink: ((Video) -> Unit)? = null,
    onAddToPlaylist: ((Video) -> Unit)? = null,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        items(videos, key = { it.id }) { video ->
            VideoCard(
                video = video,
                saved = video.id in savedIds,
                subscribed = video.channelId.isNotBlank() && video.channelId in subscribedChannelIds,
                onPlay = { onPlay(video) },
                onToggleSaved = { onToggleSaved(video) },
                onDownload = onDownload?.let { action -> { action(video) } },
                onToggleSubscription = onToggleSubscription?.let { action -> { action(video) } },
                onCopyLink = onCopyLink?.let { action -> { action(video) } },
                onAddToPlaylist = onAddToPlaylist?.let { action -> { action(video) } },
            )
        }
    }
}

@Composable
private fun VideoCard(
    video: Video,
    saved: Boolean,
    subscribed: Boolean,
    onPlay: () -> Unit,
    onToggleSaved: () -> Unit,
    onDownload: (() -> Unit)?,
    onToggleSubscription: (() -> Unit)?,
    onCopyLink: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(width = 224.dp, height = 126.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = video.channelName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val metadata =
                    buildList {
                        if (video.isLive) {
                            add("LIVE")
                        } else if (video.duration > 0) {
                            add(formatDuration(video.duration))
                        }
                        if (video.viewCount > 0) add("${formatCount(video.viewCount)} views")
                        if (video.uploadDate.isNotBlank()) add(video.uploadDate)
                    }
                if (metadata.isNotEmpty()) {
                    Text(
                        metadata.joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (onToggleSubscription != null && video.channelId.isNotBlank()) {
                    Button(onClick = onToggleSubscription, modifier = Modifier.padding(top = 8.dp)) {
                        Text(if (subscribed) "Subscribed" else "Subscribe")
                    }
                }
            }
            onCopyLink?.let { action ->
                IconButton(onClick = action) { Icon(Icons.Default.Link, contentDescription = "Copy YouTube link") }
            }
            onAddToPlaylist?.let { action ->
                IconButton(onClick = action) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to playlist") }
            }
            onDownload?.let { action ->
                IconButton(onClick = action) { Icon(Icons.Default.Download, contentDescription = "Download") }
            }
            IconButton(onClick = onToggleSaved) {
                Icon(
                    imageVector = if (saved) Icons.Default.Bookmarks else Icons.Outlined.BookmarkAdd,
                    contentDescription = if (saved) "Remove from library" else "Save to library",
                )
            }
            IconButton(onClick = onPlay) { Icon(Icons.Default.PlayArrow, contentDescription = "Play") }
        }
    }
}

@Composable
internal fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
internal fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Text("Could not load videos", style = MaterialTheme.typography.titleMedium)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
internal fun SettingCard(
    title: String,
    body: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remaining) else "%d:%02d".format(minutes, remaining)
}

private fun formatCount(value: Long): String =
    when {
        value >= 1_000_000_000 -> String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0)
        value >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format(Locale.ROOT, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
