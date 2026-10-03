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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    onClearHistory: () -> Unit,
    onRemovePlaylist: (String) -> Unit,
) {
    var section by remember { mutableStateOf(LibrarySection.SAVED) }
    ScreenColumn(title = "Library", subtitle = "Your local Linux library") {
        Column(modifier = Modifier.fillMaxSize()) {
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
) {
    var videos by remember { mutableStateOf(emptyList<Video>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(subscriptions, reload) {
        if (subscriptions.isEmpty()) {
            videos = emptyList()
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            videos =
                coroutineScope {
                    val outcomes = mutableListOf<Result<List<Video>>>()
                    subscriptions
                        .chunked(MAX_CONCURRENT_CHANNEL_FETCHES)
                        .forEach { batch ->
                            outcomes +=
                                batch
                                    .map { subscription ->
                                        async {
                                            try {
                                                Result.success(repository.channelVideos(subscription.channelId, VIDEOS_PER_CHANNEL))
                                            } catch (cancellation: CancellationException) {
                                                throw cancellation
                                            } catch (failure: Throwable) {
                                                Result.failure(failure)
                                            }
                                        }
                                    }.awaitAll()
                        }
                    if (outcomes.isNotEmpty() && outcomes.all { it.isFailure }) {
                        throw outcomes.firstNotNullOf { it.exceptionOrNull() }
                    }
                    outcomes
                        .mapNotNull { it.getOrNull() }
                        .flatten()
                        .distinctBy(Video::id)
                        .sortedByDescending(Video::timestamp)
                }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            error = failure.message ?: failure.javaClass.simpleName
        } finally {
            loading = false
        }
    }

    ScreenColumn(title = "Subscriptions", subtitle = "Latest videos from locally followed channels") {
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

@Composable
internal fun DownloadsScreen(
    downloader: DesktopDownloader,
    onPlayFile: (Path) -> Unit,
    onOpenLocalFile: () -> Unit,
) {
    var files by remember { mutableStateOf(emptyList<Path>()) }
    var refresh by remember { mutableIntStateOf(0) }
    var folderError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refresh, downloader.directory) {
        files =
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!Files.isDirectory(downloader.directory)) return@runCatching emptyList()
                    Files.list(downloader.directory).use { stream ->
                        stream
                            .filter(Files::isRegularFile)
                            .toList()
                            .sortedByDescending { path ->
                                runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
                            }
                    }
                }.getOrDefault(emptyList())
            }
    }

    ScreenColumn(title = "Downloads", subtitle = downloader.directory.toString()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { refresh++ }) { Text("Refresh") }
                Button(onClick = onOpenLocalFile) { Text("Open local media") }
                Button(
                    onClick = {
                        folderError =
                            runCatching {
                                Files.createDirectories(downloader.directory)
                                openPath(downloader.directory).getOrThrow()
                            }.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
                    },
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Text(" Open folder")
                }
            }
            folderError?.let { message ->
                Text(
                    "Could not open downloads folder: $message",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            if (files.isEmpty()) {
                EmptyState("Downloaded videos will appear here.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(files, key = Path::toString) { file ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(file.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { onPlayFile(file) }) { Text("Play") }
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
    SAVED("Saved"),
    HISTORY("History"),
    PLAYLISTS("Playlists"),
}

private const val MAX_CONCURRENT_CHANNEL_FETCHES = 3
private const val VIDEOS_PER_CHANNEL = 8
