package io.github.aedev.flow.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException

@Composable
internal fun ShortsScreen(
    repository: DesktopYouTubeRepository,
    savedIds: Set<String>,
    subscribedIds: Set<String>,
    onPlay: (Video) -> Unit,
    onToggleSaved: (Video) -> Unit,
    onDownload: (Video) -> Unit,
    onToggleSubscription: (Video) -> Unit,
    onCopyLink: (Video) -> Unit,
    onAddToPlaylist: (Video) -> Unit,
) {
    DiscoveryScreen(
        title = "Shorts",
        subtitle = "Short-form videos",
        repository = repository,
        loader = DesktopYouTubeRepository::discoverShorts,
        savedIds = savedIds,
        subscribedIds = subscribedIds,
        onPlay = onPlay,
        onToggleSaved = onToggleSaved,
        onDownload = onDownload,
        onToggleSubscription = onToggleSubscription,
        onCopyLink = onCopyLink,
        onAddToPlaylist = onAddToPlaylist,
    )
}

@Composable
internal fun MusicScreen(
    repository: DesktopYouTubeRepository,
    savedIds: Set<String>,
    subscribedIds: Set<String>,
    onPlay: (Video) -> Unit,
    onToggleSaved: (Video) -> Unit,
    onDownload: (Video) -> Unit,
    onToggleSubscription: (Video) -> Unit,
    onCopyLink: (Video) -> Unit,
    onAddToPlaylist: (Video) -> Unit,
) {
    DiscoveryScreen(
        title = "Music",
        subtitle = "Music discovery and playback",
        repository = repository,
        loader = DesktopYouTubeRepository::discoverMusic,
        savedIds = savedIds,
        subscribedIds = subscribedIds,
        onPlay = onPlay,
        onToggleSaved = onToggleSaved,
        onDownload = onDownload,
        onToggleSubscription = onToggleSubscription,
        onCopyLink = onCopyLink,
        onAddToPlaylist = onAddToPlaylist,
    )
}

@Composable
private fun DiscoveryScreen(
    title: String,
    subtitle: String,
    repository: DesktopYouTubeRepository,
    loader: suspend DesktopYouTubeRepository.() -> List<Video>,
    savedIds: Set<String>,
    subscribedIds: Set<String>,
    onPlay: (Video) -> Unit,
    onToggleSaved: (Video) -> Unit,
    onDownload: (Video) -> Unit,
    onToggleSubscription: (Video) -> Unit,
    onCopyLink: (Video) -> Unit,
    onAddToPlaylist: (Video) -> Unit,
) {
    var videos by remember { mutableStateOf(emptyList<Video>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(repository, reloadKey) {
        loading = true
        error = null
        try {
            videos = repository.loader()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            error = failure.message ?: failure.javaClass.simpleName
        } finally {
            loading = false
        }
    }

    ScreenColumn(title = title, subtitle = subtitle) {
        when {
            loading -> {
                LoadingState()
            }

            error != null -> {
                ErrorState(error!!, onRetry = { reloadKey++ })
            }

            videos.isEmpty() -> {
                EmptyState("No $title were returned.")
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
                    minCardWidth = if (title == "Shorts") 210.dp else 320.dp,
                    thumbnailAspectRatio = if (title == "Shorts") 9f / 16f else 16f / 9f,
                )
            }
        }
    }
}
