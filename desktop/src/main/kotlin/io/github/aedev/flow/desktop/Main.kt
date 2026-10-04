package io.github.aedev.flow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import javax.swing.JOptionPane

fun main() {
    val instanceLock = DesktopInstanceLock.tryAcquire()
    if (instanceLock == null) {
        JOptionPane.showMessageDialog(
            null,
            "Flow is already running.",
            "Flow",
            JOptionPane.INFORMATION_MESSAGE,
        )
        return
    }
    instanceLock.use {
        application {
            val trayState = rememberTrayState()
            val trayIcon = rememberVectorPainter(Icons.Default.Home)
            val settingsStore = remember { DesktopSettingsStore() }
            var settings by remember { mutableStateOf(settingsStore.load()) }
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
                FlowDesktopTheme(darkTheme = settings.darkTheme) {
                    FlowDesktopApp(
                        settings = settings,
                        onSettingsChange = { updated ->
                            runCatching {
                                settingsStore.save(updated)
                                settings = updated
                            }
                        },
                        onNotify = { title, message ->
                            if (isTraySupported) trayState.sendNotification(Notification(title, message))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowDesktopTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(), content = content)
}

@Composable
private fun FlowDesktopApp(
    settings: DesktopSettings,
    onSettingsChange: (DesktopSettings) -> Result<Unit>,
    onNotify: (String, String) -> Unit,
) {
    val repository = remember { DesktopYouTubeRepository() }
    val libraryStore = remember { DesktopLibraryStore() }
    val player = remember { DesktopMpvPlayer() }
    val downloader = remember { DesktopDownloader() }
    val scope = rememberCoroutineScope()
    val initialDestination = settings.visibleRootDestinations().first()
    var destination by remember { mutableStateOf(initialDestination) }
    var navigationHistory by remember { mutableStateOf(emptyList<DesktopDestination>()) }
    val searchState = remember { DesktopSearchState() }
    val initialSavedVideos = remember { libraryStore.load() }
    var savedVideos by remember { mutableStateOf(initialSavedVideos) }
    var history by remember { mutableStateOf(libraryStore.loadHistory()) }
    var subscriptions by remember { mutableStateOf(libraryStore.loadSubscriptions()) }
    var playlists by remember { mutableStateOf(libraryStore.loadPlaylists()) }
    var searchHistory by remember { mutableStateOf(libraryStore.loadSearchHistory()) }
    var currentVideo by remember { mutableStateOf<Video?>(null) }
    var playerPaused by remember { mutableStateOf(false) }
    var pendingPlayback by remember { mutableStateOf<Pair<String, Video>?>(null) }
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

    LaunchedEffect(settings) {
        val visible = settings.visibleRootDestinations()
        navigationHistory =
            navigationHistory.filter { previous ->
                previous in visible || previous == DesktopDestination.SEARCH || previous == DesktopDestination.DOWNLOADS
            }
    }

    fun navigateRoot(target: DesktopDestination) {
        destination = target
        navigationHistory = emptyList()
    }

    fun navigateNested(target: DesktopDestination) {
        if (destination == target) return
        navigationHistory = navigationHistory + destination
        destination = target
    }

    fun navigateBack() {
        val previous = navigationHistory.lastOrNull() ?: settings.visibleRootDestinations().first()
        navigationHistory = navigationHistory.dropLast(1)
        destination = previous
    }

    fun toggleSaved(video: Video) {
        scope.launch {
            runCatchingCancellable { withContext(Dispatchers.IO) { libraryStore.toggleSaved(video) } }
                .onSuccess { savedVideos = it }
                .onFailure { statusMessage = "Could not save library: ${it.message}" }
        }
    }

    fun toggleSubscription(video: Video) {
        scope.launch {
            runCatchingCancellable { withContext(Dispatchers.IO) { libraryStore.toggleSubscription(video) } }
                .onSuccess { subscriptions = it }
                .onFailure { statusMessage = "Could not update subscription: ${it.message}" }
        }
    }

    fun removeSubscription(channelId: String) {
        scope.launch {
            runCatchingCancellable { withContext(Dispatchers.IO) { libraryStore.removeSubscription(channelId) } }
                .onSuccess { subscriptions = it }
                .onFailure { statusMessage = "Could not update subscription: ${it.message}" }
        }
    }

    fun download(video: Video) {
        if (!downloader.isAvailable) {
            statusMessage = downloader.unavailableReason
            return
        }
        if (downloader.isDownloading(video.id)) {
            statusMessage = "${video.title} is already downloading."
            return
        }
        statusMessage = "Downloading ${video.title}…"
        scope.launch {
            try {
                val path = downloader.download(video)
                statusMessage = "Downloaded to $path"
                onNotify("Download complete", video.title)
            } catch (cancellation: CancellationException) {
                statusMessage = "Download cancelled: ${video.title}"
                throw cancellation
            } catch (failure: Throwable) {
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
        val mediaUrl = "https://www.youtube.com/watch?v=${video.id}"
        pendingPlayback = mediaUrl to video
        scope.launch {
            try {
                withContext(Dispatchers.IO) { player.play(mediaUrl) }
            } catch (cancellation: CancellationException) {
                if (pendingPlayback?.first == mediaUrl) pendingPlayback = null
                throw cancellation
            } catch (failure: Throwable) {
                if (pendingPlayback?.first == mediaUrl) pendingPlayback = null
                statusMessage = "Playback failed: ${failure.message}"
            }
        }
    }

    fun playFile(path: Path) {
        if (!player.canPlayLocal) {
            statusMessage = player.unavailableReason
            return
        }
        val mediaUrl = path.toAbsolutePath().toString()
        val localVideo =
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
        pendingPlayback = mediaUrl to localVideo
        scope.launch {
            try {
                withContext(Dispatchers.IO) { player.play(mediaUrl) }
            } catch (cancellation: CancellationException) {
                if (pendingPlayback?.first == mediaUrl) pendingPlayback = null
                throw cancellation
            } catch (failure: Throwable) {
                if (pendingPlayback?.first == mediaUrl) pendingPlayback = null
                statusMessage = "Playback failed: ${failure.message}"
            }
        }
    }

    LaunchedEffect(player) {
        var handledLoadGeneration = player.playbackState.value.loadGeneration
        player.playbackState.collect { state ->
            playerPaused = state.paused
            state.error?.let { statusMessage = "Playback failed: $it" }

            if (state.loadGeneration != handledLoadGeneration) {
                handledLoadGeneration = state.loadGeneration
                val loaded = pendingPlayback?.takeIf { it.first == state.lastLoadedMediaUrl }
                if (loaded != null) {
                    if (state.mediaUrl == state.lastLoadedMediaUrl) currentVideo = loaded.second
                    pendingPlayback = null
                    if (loaded.second.channelId != "local") {
                        try {
                            history = withContext(Dispatchers.IO) { libraryStore.recordWatched(loaded.second) }
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (failure: Throwable) {
                            statusMessage = "Could not update history: ${failure.message}"
                        }
                    }
                }
            }

            if (state.mediaUrl == null) currentVideo = null
            if (state.mediaUrl == null && state.loadingMediaUrl == null) pendingPlayback = null
        }
    }

    LaunchedEffect(destination, recommendationQuery, homeReloadKey) {
        if (destination != DesktopDestination.HOME) return@LaunchedEffect
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
                            runCatchingCancellable { withContext(Dispatchers.IO) { libraryStore.addToPlaylist(name, target) } }
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
            DesktopNavigationRail(destination = destination, settings = settings) { item ->
                navigateRoot(item)
            }

            VerticalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))

            Column(modifier = Modifier.fillMaxSize()) {
                DesktopGlobalActions(
                    destination = destination,
                    settings = settings,
                    canGoBack = navigationHistory.isNotEmpty(),
                    onBack = ::navigateBack,
                    onSearch = { navigateNested(DesktopDestination.SEARCH) },
                    onSettings = { navigateNested(DesktopDestination.SETTINGS) },
                )
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

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (destination) {
                        DesktopDestination.HOME -> {
                            HomeScreen(
                                videos = homeVideos,
                                loading = homeLoading,
                                error = homeError,
                                savedIds = savedIds,
                                subscribedIds = subscribedIds,
                                watchedIds = history.mapTo(hashSetOf(), Video::id),
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

                        DesktopDestination.SHORTS -> {
                            ShortsScreen(
                                repository = repository,
                                savedIds = savedIds,
                                subscribedIds = subscribedIds,
                                onPlay = ::play,
                                onToggleSaved = ::toggleSaved,
                                onDownload = ::download,
                                onToggleSubscription = ::toggleSubscription,
                                onCopyLink = ::copyLink,
                                onAddToPlaylist = ::addToPlaylist,
                            )
                        }

                        DesktopDestination.MUSIC -> {
                            MusicScreen(
                                repository = repository,
                                savedIds = savedIds,
                                subscribedIds = subscribedIds,
                                onPlay = ::play,
                                onToggleSaved = ::toggleSaved,
                                onDownload = ::download,
                                onToggleSubscription = ::toggleSubscription,
                                onCopyLink = ::copyLink,
                                onAddToPlaylist = ::addToPlaylist,
                            )
                        }

                        DesktopDestination.SEARCH -> {
                            SearchScreen(
                                repository = repository,
                                state = searchState,
                                savedIds = savedIds,
                                subscribedIds = subscribedIds,
                                searchHistory = searchHistory,
                                onSearch = { query ->
                                    scope.launch {
                                        runCatchingCancellable { withContext(Dispatchers.IO) { libraryStore.recordSearch(query) } }
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

                        DesktopDestination.EXPLORE -> {
                            ExploreScreen(
                                repository = repository,
                                savedIds = savedIds,
                                subscribedIds = subscribedIds,
                                onPlay = ::play,
                                onToggleSaved = ::toggleSaved,
                                onDownload = ::download,
                                onToggleSubscription = ::toggleSubscription,
                                onCopyLink = ::copyLink,
                                onAddToPlaylist = ::addToPlaylist,
                            )
                        }

                        DesktopDestination.SUBSCRIPTIONS -> {
                            SubscriptionsScreen(
                                subscriptions = subscriptions,
                                repository = repository,
                                savedIds = savedIds,
                                subscribedIds = subscribedIds,
                                onPlay = ::play,
                                onToggleSaved = ::toggleSaved,
                                onDownload = ::download,
                                onToggleSubscription = ::toggleSubscription,
                                onRemoveSubscription = ::removeSubscription,
                            )
                        }

                        DesktopDestination.LIBRARY -> {
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
                                onOpenDownloads = { navigateNested(DesktopDestination.DOWNLOADS) },
                                onOpenLocalFile = { pickMediaFile()?.let(::playFile) },
                                onClearHistory = {
                                    scope.launch {
                                        runCatchingCancellable { withContext(Dispatchers.IO) { libraryStore.clearHistory() } }
                                            .onSuccess { history = emptyList() }
                                            .onFailure { statusMessage = "Could not clear history: ${it.message}" }
                                    }
                                },
                                onRemovePlaylist = { name ->
                                    scope.launch {
                                        runCatchingCancellable { withContext(Dispatchers.IO) { libraryStore.removePlaylist(name) } }
                                            .onSuccess { playlists = it }
                                            .onFailure { statusMessage = "Could not remove playlist: ${it.message}" }
                                    }
                                },
                            )
                        }

                        DesktopDestination.DOWNLOADS -> {
                            DownloadsScreen(
                                downloader = downloader,
                                onPlayFile = ::playFile,
                                onOpenLocalFile = { pickMediaFile()?.let(::playFile) },
                            )
                        }

                        DesktopDestination.SETTINGS -> {
                            SettingsScreen(
                                player = player,
                                repository = repository,
                                downloader = downloader,
                                libraryStore = libraryStore,
                                settings = settings,
                                onSettingsChange = onSettingsChange,
                            )
                        }
                    }
                }
                DesktopPlayerBar(
                    video = currentVideo,
                    paused = playerPaused,
                    onSeekBack = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { player.seekBy(-10.0) }
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (failure: Throwable) {
                                statusMessage = "Playback failed: ${failure.message}"
                            }
                        }
                    },
                    onPauseToggle = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { player.togglePause() }
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (failure: Throwable) {
                                statusMessage = "Playback failed: ${failure.message}"
                            }
                        }
                    },
                    onSeekForward = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { player.seekBy(10.0) }
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (failure: Throwable) {
                                statusMessage = "Playback failed: ${failure.message}"
                            }
                        }
                    },
                    onStop = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { player.stop() }
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (failure: Throwable) {
                                statusMessage = "Playback failed: ${failure.message}"
                            }
                        }
                    },
                )
            }
        }
    }
}
