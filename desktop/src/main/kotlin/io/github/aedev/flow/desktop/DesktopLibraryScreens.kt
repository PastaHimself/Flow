package io.github.aedev.flow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.streams.toList

@Composable
internal fun LibraryScreen(
    savedVideos: List<Video>,
    history: List<Video>,
    playlists: List<DesktopPlaylist>,
    savedIds: Set<String>,
    subscribedIds: Set<String>,
    onPlay: (Video) -> Unit,
    onToggleSaved: (Video) -> Unit,
    onDownload: (Video) -> Unit,
    onToggleSubscription: (Video) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenLocalFile: () -> Unit,
    onClearHistory: () -> Unit,
    onRemovePlaylist: (String) -> Unit,
) {
    var section by remember { mutableStateOf(LibrarySection.SAVED) }
    ScreenColumn(title = "Library", subtitle = "Your local Linux library") {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenDownloads) { Text("Downloads") }
                Button(onClick = onOpenLocalFile) { Text("Local media") }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibrarySection.entries.forEach { item ->
                    Button(onClick = { section = item }, enabled = section != item) { Text(item.label) }
                }
            }
            Spacer(Modifier.height(16.dp))
            when (section) {
                LibrarySection.SAVED -> {
                    if (savedVideos.isEmpty()) {
                        EmptyState("Save videos from Home or Search to build your library.")
                    } else {
                        VideoList(
                            videos = savedVideos,
                            savedIds = savedIds,
                            onPlay = onPlay,
                            onToggleSaved = onToggleSaved,
                            subscribedChannelIds = subscribedIds,
                            onDownload = onDownload,
                            onToggleSubscription = onToggleSubscription,
                        )
                    }
                }

                LibrarySection.HISTORY -> {
                    if (history.isEmpty()) {
                        EmptyState("Videos you play appear here.")
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TextButton(onClick = onClearHistory) { Text("Clear history") }
                            VideoList(
                                videos = history,
                                savedIds = savedIds,
                                onPlay = onPlay,
                                onToggleSaved = onToggleSaved,
                                subscribedChannelIds = subscribedIds,
                                onDownload = onDownload,
                                onToggleSubscription = onToggleSubscription,
                            )
                        }
                    }
                }

                LibrarySection.PLAYLISTS -> {
                    if (playlists.isEmpty()) {
                        EmptyState("Create a playlist from the video actions in Home or Search.")
                    } else {
                        PlaylistList(
                            playlists,
                            savedIds,
                            subscribedIds,
                            onPlay,
                            onToggleSaved,
                            onDownload,
                            onToggleSubscription,
                            onRemovePlaylist,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<DesktopPlaylist>,
    savedIds: Set<String>,
    subscribedIds: Set<String>,
    onPlay: (Video) -> Unit,
    onToggleSaved: (Video) -> Unit,
    onDownload: (Video) -> Unit,
    onToggleSubscription: (Video) -> Unit,
    onRemovePlaylist: (String) -> Unit,
) {
    var selected by remember(playlists) { mutableStateOf(playlists.firstOrNull()?.name) }
    val playlist = playlists.firstOrNull { it.name == selected }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(playlists, key = DesktopPlaylist::name) { item ->
                    Button(onClick = { selected = item.name }, enabled = item.name != selected) { Text(item.name) }
                }
            }
            playlist?.let {
                IconButton(onClick = { onRemovePlaylist(it.name) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete playlist")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (playlist == null || playlist.videos.isEmpty()) {
            EmptyState("This playlist is empty.")
        } else {
            VideoList(
                videos = playlist.videos,
                savedIds = savedIds,
                onPlay = onPlay,
                onToggleSaved = onToggleSaved,
                subscribedChannelIds = subscribedIds,
                onDownload = onDownload,
                onToggleSubscription = onToggleSubscription,
            )
        }
    }
}

@Composable
internal fun SubscriptionsScreen(
    subscriptions: List<DesktopSubscription>,
    repository: DesktopYouTubeRepository,
    savedIds: Set<String>,
    subscribedIds: Set<String>,
    onPlay: (Video) -> Unit,
    onToggleSaved: (Video) -> Unit,
    onDownload: (Video) -> Unit,
    onToggleSubscription: (Video) -> Unit,
    onRemoveSubscription: (String) -> Unit,
) {
    var videos by remember { mutableStateOf(emptyList<Video>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var partialFailures by remember { mutableStateOf(emptyList<String>()) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(subscriptions, reload) {
        if (subscriptions.isEmpty()) {
            videos = emptyList()
            loading = false
            error = null
            partialFailures = emptyList()
            return@LaunchedEffect
        }
        loading = true
        error = null
        partialFailures = emptyList()
        try {
            val result =
                loadSubscriptionFeed(subscriptions) { subscription ->
                    repository.channelVideos(subscription.channelId, VIDEOS_PER_CHANNEL)
                }
            videos = result.videos
            partialFailures = result.failedChannelNames
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            error = failure.message ?: failure.javaClass.simpleName
        } finally {
            loading = false
        }
    }

    ScreenColumn(title = "Subscriptions", subtitle = "Latest videos from locally followed channels") {
        Column(modifier = Modifier.fillMaxSize()) {
            if (subscriptions.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(subscriptions, key = DesktopSubscription::channelId) { subscription ->
                        TextButton(onClick = { onRemoveSubscription(subscription.channelId) }) {
                            Text("${subscription.channelName} · Unfollow")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (partialFailures.isNotEmpty()) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        "Could not refresh ${partialFailures.size} subscription${if (partialFailures.size == 1) "" else "s"}.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { reload++ }) { Text("Retry") }
                }
                Spacer(Modifier.height(8.dp))
            }
            when {
                subscriptions.isEmpty() -> {
                    EmptyState("Subscribe to channels from Home or Search.")
                }

                loading -> {
                    LoadingState()
                }

                error != null -> {
                    ErrorState(error!!, onRetry = { reload++ })
                }

                videos.isEmpty() -> {
                    EmptyState("No subscription videos were returned.")
                }

                else -> {
                    VideoList(
                        videos = videos,
                        savedIds = savedIds,
                        onPlay = onPlay,
                        onToggleSaved = onToggleSaved,
                        subscribedChannelIds = subscribedIds,
                        onDownload = onDownload,
                        onToggleSubscription = onToggleSubscription,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DownloadsScreen(
    downloader: DesktopDownloader,
    onPlayFile: (Path) -> Unit,
    onOpenLocalFile: () -> Unit,
) {
    var files by remember { mutableStateOf(emptyList<Path>()) }
    var refresh by remember { mutableIntStateOf(0) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var listingError by remember { mutableStateOf<String?>(null) }
    val activeDownloads by downloader.activeDownloads.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(refresh, downloader.directory, activeDownloads) {
        try {
            files = withContext(Dispatchers.IO) { downloader.listDownloadedFiles() }
            listingError = null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            files = emptyList()
            listingError = failure.message ?: failure.javaClass.simpleName
        }
    }

    ScreenColumn(title = "Downloads", subtitle = downloader.directory.toString()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { refresh++ }) { Text("Refresh") }
                Button(onClick = onOpenLocalFile) { Text("Open local media") }
                Button(
                    onClick = {
                        actionError =
                            runCatching {
                                Files.createDirectories(downloader.directory)
                                openPath(downloader.directory).getOrThrow()
                            }.exceptionOrNull()?.let {
                                "Could not open downloads folder: ${it.message ?: it.javaClass.simpleName}"
                            }
                    },
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Text(" Open folder")
                }
            }
            actionError?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (activeDownloads.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(activeDownloads.entries.toList(), key = { it.key }) { (videoId, title) ->
                        TextButton(onClick = { downloader.cancel(videoId) }) { Text("Cancel $title", maxLines = 1) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (listingError != null) {
                ErrorState("Could not read downloads folder: $listingError", onRetry = { refresh++ })
            } else if (files.isEmpty()) {
                EmptyState("Downloaded videos will appear here.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(files, key = Path::toString) { file ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(file.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { onPlayFile(file) }) { Text("Play") }
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) { downloader.delete(file) }
                                                actionError = null
                                                refresh++
                                            } catch (cancellation: CancellationException) {
                                                throw cancellation
                                            } catch (failure: Throwable) {
                                                actionError = "Could not delete ${file.name}: ${failure.message}"
                                            }
                                        }
                                    },
                                ) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class LibrarySection(
    val label: String,
) {
    SAVED("Watch later"),
    HISTORY("History"),
    PLAYLISTS("Playlists"),
}

private const val VIDEOS_PER_CHANNEL = 8
