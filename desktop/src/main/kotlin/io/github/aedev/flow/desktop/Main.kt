package io.github.aedev.flow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

private enum class Destination(
    val label: String,
) {
    HOME("Home"),
    SEARCH("Search"),
    SUBSCRIPTIONS("Subscriptions"),
    LIBRARY("Library"),
    DOWNLOADS("Downloads"),
    SETTINGS("Settings"),
}

fun main() =
    application {
        val trayState = rememberTrayState()
        val trayIcon = rememberVectorPainter(Icons.Default.Home)
        if (isTraySupported) {
            Tray(
                icon = trayIcon,
                state = trayState,
                tooltip = "Flow",
                menu = {
                    Item("Open Flow website", onClick = { openExternalUrl("https://github.com/A-EDev/Flow") })
                    Separator()
                    Item("Quit", onClick = ::exitApplication)
                },
            )
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Flow",
        ) {
            MenuBar {
                Menu("Flow") {
                    Item("Project website", onClick = { openExternalUrl("https://github.com/A-EDev/Flow") })
                    Separator()
                    Item("Quit", onClick = ::exitApplication)
                }
            }
            FlowDesktopTheme {
                FlowDesktopApp(
                    onNotify = { title, message ->
                        if (isTraySupported) trayState.sendNotification(Notification(title, message))
                    },
                )
            }
        }
    }

@Composable
private fun FlowDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

@Composable
private fun FlowDesktopApp(onNotify: (String, String) -> Unit) {
    val repository = remember { DesktopYouTubeRepository() }
    val libraryStore = remember { DesktopLibraryStore() }
    val player = remember { DesktopMpvPlayer() }
    val downloader = remember { DesktopDownloader() }
    val scope = rememberCoroutineScope()
    var destination by remember { mutableStateOf(Destination.HOME) }
    val initialSavedVideos = remember { libraryStore.load() }
    var savedVideos by remember { mutableStateOf(initialSavedVideos) }
    var history by remember { mutableStateOf(libraryStore.loadHistory()) }
    var subscriptions by remember { mutableStateOf(libraryStore.loadSubscriptions()) }
    var playlists by remember { mutableStateOf(libraryStore.loadPlaylists()) }
    var searchHistory by remember { mutableStateOf(libraryStore.loadSearchHistory()) }
    var currentVideo by remember { mutableStateOf<Video?>(null) }
    var playerPaused by remember { mutableStateOf(false) }
    var playlistTarget by remember { mutableStateOf<Video?>(null) }
    var playlistName by remember { mutableStateOf("My playlist") }
    var statusMessage by remember {
        mutableStateOf(
            libraryStore.loadErrors.entries.firstOrNull()?.let { (path, error) ->
                "Could not read ${path.fileName}: ${error.message ?: error.javaClass.simpleName}. Flow will not overwrite it."
            },
        )
    }
    var homeVideos by remember { mutableStateOf(emptyList<Video>()) }
    var homeLoading by remember { mutableStateOf(true) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var homeReloadKey by remember { mutableStateOf(0) }
    val recommendationQuery = libraryStore.recommendationQuery(savedVideos)
    val savedIds = savedVideos.mapTo(hashSetOf(), Video::id)
    val subscribedIds = subscriptions.mapTo(hashSetOf(), DesktopSubscription::channelId)

    fun toggleSaved(video: Video) {
        val updatedVideos =
            if (savedVideos.any { it.id == video.id }) {
                savedVideos.filterNot { it.id == video.id }
            } else {
                listOf(video) + savedVideos
            }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { libraryStore.save(updatedVideos) } }
                .onSuccess { savedVideos = updatedVideos }
                .onFailure { statusMessage = "Could not save library: ${it.message}" }
        }
    }

    fun toggleSubscription(video: Video) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { libraryStore.toggleSubscription(video) } }
                .onSuccess { subscriptions = it }
                .onFailure { statusMessage = "Could not update subscription: ${it.message}" }
        }
    }

    fun download(video: Video) {
        if (!downloader.isAvailable) {
            statusMessage = downloader.unavailableReason
            return
        }
        statusMessage = "Downloading ${video.title}…"
        scope.launch {
            runCatching { downloader.download(video) }
                .onSuccess { path ->
                    statusMessage = "Downloaded to $path"
                    onNotify("Download complete", video.title)
                }.onFailure { failure ->
                    statusMessage = "Download failed: ${failure.message}"
                    onNotify("Download failed", video.title)
                }
        }
    }

    fun copyLink(video: Video) {
        copyToClipboard("https://www.youtube.com/watch?v=${video.id}")
            .onSuccess { statusMessage = "Copied video link." }
            .onFailure { statusMessage = "Could not copy link: ${it.message}" }
    }

    fun addToPlaylist(video: Video) {
        playlistTarget = video
        playlistName = playlists.firstOrNull()?.name ?: "My playlist"
    }

    fun play(video: Video) {
        if (!player.canPlayYouTube) {
            statusMessage = player.youtubeUnavailableReason
            return
        }
        scope.launch {
            val error =
                withContext(Dispatchers.IO) {
                    runCatching { player.play("https://www.youtube.com/watch?v=${video.id}") }.exceptionOrNull()
                }
            if (error != null) {
                statusMessage = "Playback failed: ${error.message}"
            } else {
                currentVideo = video
                playerPaused = false
                runCatching { withContext(Dispatchers.IO) { libraryStore.recordWatched(video) } }
                    .onSuccess { history = it }
                    .onFailure { statusMessage = "Could not update history: ${it.message}" }
            }
        }
    }

    fun playFile(path: Path) {
        if (!player.canPlayLocal) {
            statusMessage = player.unavailableReason
            return
        }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { player.play(path.toAbsolutePath().toString()) } }
                .onSuccess {
                    currentVideo =
                        Video(
                            id = "local:${path.toAbsolutePath()}",
                            title = path.fileName.toString(),
                            channelName = "Local media",
                            channelId = "local",
                            thumbnailUrl = "",
                            duration = 0,
                            viewCount = 0,
                            uploadDate = "",
                        )
                    playerPaused = false
                }.onFailure { statusMessage = "Playback failed: ${it.message}" }
        }
    }

    LaunchedEffect(destination, recommendationQuery, homeReloadKey) {
        if (destination != Destination.HOME) return@LaunchedEffect
        homeLoading = true
        homeError = null
        try {
            homeVideos = repository.discover(recommendationQuery)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            homeError = error.message ?: error.javaClass.simpleName
        } finally {
            homeLoading = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.close() }
    }

    playlistTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { playlistTarget = null },
            title = { Text("Add to playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    singleLine = true,
                    label = { Text("Playlist name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = playlistName.isNotBlank(),
                    onClick = {
                        val name = playlistName
                        playlistTarget = null
                        scope.launch {
                            runCatching { withContext(Dispatchers.IO) { libraryStore.addToPlaylist(name, target) } }
                                .onSuccess {
                                    playlists = it
                                    statusMessage = "Added to $name."
                                }.onFailure { statusMessage = "Could not update playlist: ${it.message}" }
                        }
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { playlistTarget = null }) { Text("Cancel") } },
        )
    }

    Scaffold { padding ->
        Row(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                header = {
                    Text(
                        text = "Flow",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                },
            ) {
                Destination.entries.forEach { item ->
                    NavigationRailItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = {
                            Icon(
                                imageVector =
                                    when (item) {
                                        Destination.HOME -> Icons.Default.Home
                                        Destination.SEARCH -> Icons.Default.Search
                                        Destination.SUBSCRIPTIONS -> Icons.Default.Subscriptions
                                        Destination.LIBRARY -> Icons.Default.LibraryMusic
                                        Destination.DOWNLOADS -> Icons.Default.Download
                                        Destination.SETTINGS -> Icons.Default.Settings
                                    },
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            }

            VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

            Column(modifier = Modifier.fillMaxSize()) {
                statusMessage?.let { message ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Text(message, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { statusMessage = null }) { Text("Dismiss") }
                        }
                    }
                }

                when (destination) {
                    Destination.HOME -> {
                        HomeScreen(
                            videos = homeVideos,
                            loading = homeLoading,
                            error = homeError,
                            savedIds = savedIds,
                            subscribedIds = subscribedIds,
                            recommendationQuery = recommendationQuery,
                            onRetry = { homeReloadKey++ },
                            onPlay = ::play,
                            onToggleSaved = ::toggleSaved,
                            onDownload = ::download,
                            onToggleSubscription = ::toggleSubscription,
                            onCopyLink = ::copyLink,
                            onAddToPlaylist = ::addToPlaylist,
                        )
                    }

                    Destination.SEARCH -> {
                        SearchScreen(
                            repository = repository,
                            savedIds = savedIds,
                            subscribedIds = subscribedIds,
                            searchHistory = searchHistory,
                            onSearch = { query ->
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { libraryStore.recordSearch(query) } }
                                        .onSuccess { searchHistory = it }
                                        .onFailure { statusMessage = "Could not update search history: ${it.message}" }
                                }
                            },
                            onPlay = ::play,
                            onToggleSaved = ::toggleSaved,
                            onDownload = ::download,
                            onToggleSubscription = ::toggleSubscription,
                            onCopyLink = ::copyLink,
                            onAddToPlaylist = ::addToPlaylist,
                        )
                    }

                    Destination.SUBSCRIPTIONS -> {
                        SubscriptionsScreen(
                            subscriptions = subscriptions,
                            repository = repository,
                            savedIds = savedIds,
                            subscribedIds = subscribedIds,
                            onPlay = ::play,
                            onToggleSaved = ::toggleSaved,
                            onDownload = ::download,
                            onToggleSubscription = ::toggleSubscription,
                        )
                    }

                    Destination.LIBRARY -> {
                        LibraryScreen(
                            savedVideos = savedVideos,
                            history = history,
                            playlists = playlists,
                            savedIds = savedIds,
                            subscribedIds = subscribedIds,
                            onPlay = ::play,
                            onToggleSaved = ::toggleSaved,
                            onDownload = ::download,
                            onToggleSubscription = ::toggleSubscription,
                            onClearHistory = {
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { libraryStore.clearHistory() } }
                                        .onSuccess { history = emptyList() }
                                        .onFailure { statusMessage = "Could not clear history: ${it.message}" }
                                }
                            },
                            onRemovePlaylist = { name ->
                                scope.launch {
                                    runCatching { withContext(Dispatchers.IO) { libraryStore.removePlaylist(name) } }
                                        .onSuccess { playlists = it }
                                        .onFailure { statusMessage = "Could not remove playlist: ${it.message}" }
                                }
                            },
                        )
                    }

                    Destination.DOWNLOADS -> {
                        DownloadsScreen(
                            downloader = downloader,
                            onPlayFile = ::playFile,
                            onOpenLocalFile = { pickMediaFile()?.let(::playFile) },
                        )
                    }

                    Destination.SETTINGS -> {
                        SettingsScreen(player = player, repository = repository, downloader = downloader, libraryStore = libraryStore)
                    }
                }
                DesktopPlayerBar(
                    video = currentVideo,
                    player = player,
                    paused = playerPaused,
                    onPauseToggle = {
                        player.togglePause()
                        playerPaused = player.paused
                    },
                    onStop = {
                        player.stop()
                        currentVideo = null
                        playerPaused = false
                    },
                )
            }
        }
    }
}

private fun pickMediaFile(): Path? {
    val dialog = FileDialog(null as Frame?, "Open media", FileDialog.LOAD)
    dialog.isVisible = true
    val file = dialog.file ?: return null
    return Path.of(dialog.directory, file)
}
