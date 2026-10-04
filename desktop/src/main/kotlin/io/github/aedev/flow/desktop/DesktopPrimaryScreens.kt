package io.github.aedev.flow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun HomeScreen(
    videos: List<Video>,
    loading: Boolean,
    error: String?,
    savedIds: Set<String>,
    subscribedIds: Set<String>,
    watchedIds: Set<String>,
    recommendationQuery: String?,
    onRetry: () -> Unit,
    onPlay: (Video) -> Unit,
    onToggleSaved: (Video) -> Unit,
    onDownload: (Video) -> Unit,
    onToggleSubscription: (Video) -> Unit,
    onCopyLink: (Video) -> Unit,
    onAddToPlaylist: (Video) -> Unit,
) {
    var chip by remember { mutableStateOf(HomeChip.ALL) }
    val visibleVideos =
        when (chip) {
            HomeChip.ALL -> videos
            HomeChip.NEW_TO_YOU -> videos.filterNot { it.id in watchedIds }
            HomeChip.RECENT -> videos.sortedByDescending(Video::timestamp)
            HomeChip.LIVE -> videos.filter(Video::isLive)
            HomeChip.WATCHED -> videos.filter { it.id in watchedIds }
        }
    ScreenColumn(
        title = "Home",
        subtitle = recommendationQuery?.let { "Local recommendation seed: $it" } ?: "Private discovery without an account",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            HomeChip.entries.forEach { item ->
                Button(onClick = { chip = item }, enabled = chip != item) { Text(item.label) }
            }
        }
        Spacer(Modifier.height(14.dp))
        when {
            loading -> {
                LoadingState()
            }

            error != null -> {
                ErrorState(error, onRetry)
            }

            visibleVideos.isEmpty() -> {
                EmptyState(if (videos.isEmpty()) "No videos were returned." else "No videos match ${chip.label}.")
            }

            else -> {
                VideoList(
                    videos = visibleVideos,
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

private enum class HomeChip(
    val label: String,
) {
    ALL("All"),
    NEW_TO_YOU("New to you"),
    RECENT("Recently uploaded"),
    LIVE("Live"),
    WATCHED("Watched"),
}

internal class DesktopSearchState {
    var query by mutableStateOf("")
    var results by mutableStateOf(emptyList<Video>())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

@Composable
internal fun SearchScreen(
    repository: DesktopYouTubeRepository,
    state: DesktopSearchState,
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
    var searchRequestId by remember { mutableStateOf(0L) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun search(candidate: String = state.query) {
        val submittedQuery = candidate.trim()
        if (submittedQuery.isBlank()) return
        onSearch(submittedQuery)
        searchJob?.cancel()
        val requestId = ++searchRequestId
        state.loading = true
        state.error = null
        searchJob =
            scope.launch {
                val outcome =
                    try {
                        Result.success(repository.search(submittedQuery))
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Throwable) {
                        Result.failure(failure)
                    }
                if (requestId != searchRequestId) return@launch
                outcome
                    .onSuccess { state.results = it }
                    .onFailure { state.error = it.message ?: it.javaClass.simpleName }
                state.loading = false
                searchJob = null
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            searchJob?.cancel()
            state.loading = false
        }
    }

    ScreenColumn(title = "Search", subtitle = "Search YouTube without an account") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { value ->
                    searchJob?.cancel()
                    searchJob = null
                    state.query = value
                    searchRequestId++
                    state.loading = false
                    state.error = null
                },
                singleLine = true,
                label = { Text("Search videos") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier =
                    Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                search()
                                true
                            } else {
                                false
                            }
                        },
            )
            Button(onClick = ::search, enabled = state.query.isNotBlank() && !state.loading) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Search")
            }
        }
        Spacer(Modifier.height(20.dp))
        if (state.results.isEmpty() && !state.loading && state.error == null && searchHistory.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                searchHistory.take(5).forEach { previous ->
                    Button(
                        onClick = {
                            state.query = previous
                            search(previous)
                        },
                    ) { Text(previous, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        when {
            state.loading -> {
                LoadingState()
            }

            state.error != null -> {
                ErrorState(state.error!!, ::search)
            }

            state.results.isEmpty() -> {
                EmptyState("Enter a query to find videos.")
            }

            else -> {
                VideoList(
                    videos = state.results,
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
