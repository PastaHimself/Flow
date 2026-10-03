package io.github.aedev.flow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.launch

@Composable
internal fun HomeScreen(
    videos: List<Video>,
    loading: Boolean,
    error: String?,
    savedIds: Set<String>,
    subscribedIds: Set<String>,
    recommendationQuery: String?,
    onRetry: () -> Unit,
    onPlay: (Video) -> Unit,
    onToggleSaved: (Video) -> Unit,
    onDownload: (Video) -> Unit,
    onToggleSubscription: (Video) -> Unit,
    onCopyLink: (Video) -> Unit,
    onAddToPlaylist: (Video) -> Unit,
) {
    ScreenColumn(
        title = "Home",
        subtitle = recommendationQuery?.let { "Local recommendation seed: $it" } ?: "Private discovery without an account",
    ) {
        when {
            loading -> {
                LoadingState()
            }

            error != null -> {
                ErrorState(error, onRetry)
            }

            videos.isEmpty() -> {
                EmptyState("No videos were returned.")
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
                    onCopyLink = onCopyLink,
                    onAddToPlaylist = onAddToPlaylist,
                )
            }
        }
    }
}

@Composable
internal fun SearchScreen(
    repository: DesktopYouTubeRepository,
    savedIds: Set<String>,
    subscribedIds: Set<String>,
    searchHistory: List<String>,
    onSearch: (String) -> Unit,
    onPlay: (Video) -> Unit,
    onToggleSaved: (Video) -> Unit,
    onDownload: (Video) -> Unit,
    onToggleSubscription: (Video) -> Unit,
    onCopyLink: (Video) -> Unit,
    onAddToPlaylist: (Video) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Video>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchRequestId by remember { mutableStateOf(0L) }

    fun search() {
        val submittedQuery = query.trim()
        if (submittedQuery.isBlank()) return
        onSearch(submittedQuery)
        val requestId = ++searchRequestId
        loading = true
        error = null
        scope.launch {
            val outcome = runCatching { repository.searchVideos(submittedQuery) }
            if (requestId != searchRequestId) return@launch
            outcome
                .onSuccess { results = it }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
            loading = false
        }
    }

    ScreenColumn(title = "Search", subtitle = "Search YouTube without an account") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { value ->
                    query = value
                    searchRequestId++
                    loading = false
                    error = null
                },
                singleLine = true,
                label = { Text("Search videos") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = ::search, enabled = query.isNotBlank() && !loading) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Search")
            }
        }
        Spacer(Modifier.height(20.dp))
        if (results.isEmpty() && !loading && error == null && searchHistory.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                searchHistory.take(5).forEach { previous ->
                    Button(onClick = { query = previous }) { Text(previous, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        when {
            loading -> {
                LoadingState()
            }

            error != null -> {
                ErrorState(error!!, ::search)
            }

            results.isEmpty() -> {
                EmptyState("Enter a query to find videos.")
            }

            else -> {
                VideoList(
                    videos = results,
                    savedIds = savedIds,
                    onPlay = onPlay,
                    onToggleSaved = onToggleSaved,
                    subscribedChannelIds = subscribedIds,
                    onDownload = onDownload,
                    onToggleSubscription = onToggleSubscription,
                    onCopyLink = onCopyLink,
                    onAddToPlaylist = onAddToPlaylist,
                )
            }
        }
    }
}

@Composable
internal fun SettingsScreen(
    player: DesktopMpvPlayer,
    repository: DesktopYouTubeRepository,
    downloader: DesktopDownloader,
    libraryStore: DesktopLibraryStore,
) {
    ScreenColumn(title = "Settings", subtitle = "Linux desktop") {
        LazyColumn {
            item {
                SettingCard(
                    title = "Playback",
                    body =
                        when {
                            !player.canPlayLocal -> {
                                player.unavailableReason.orEmpty()
                            }

                            player.canPlayYouTube -> {
                                "mpv + yt-dlp detected. Local and YouTube playback open in an mpv window. Logs: ${player.diagnosticsFile}"
                            }

                            else -> {
                                "mpv detected for local playback. ${player.youtubeUnavailableReason.orEmpty()} Logs: ${player.diagnosticsFile}"
                            }
                        },
                )
            }
            item {
                SettingCard(
                    title = "YouTube access",
                    body =
                        if (repository.isAvailable) {
                            "yt-dlp detected for search and discovery."
                        } else {
                            repository.unavailableReason.orEmpty()
                        },
                )
            }
            item {
                SettingCard(
                    title = "Downloads",
                    body =
                        if (downloader.isAvailable) {
                            "Saved to ${downloader.directory}"
                        } else {
                            downloader.unavailableReason.orEmpty()
                        },
                )
            }
            item {
                SettingCard(
                    title = "Local data",
                    body = "Library, history, subscriptions, playlists and search history: ${libraryStore.dataDirectory}",
                )
            }
            item {
                SettingCard(
                    title = "Privacy",
                    body = "Desktop recommendations are seeded from your local library. No Flow account or telemetry service is used.",
                )
            }
        }
    }
}
