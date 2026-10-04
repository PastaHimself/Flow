package io.github.aedev.flow.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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

@Composable
internal fun ExploreScreen(
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
    var category by remember { mutableStateOf(ExploreCategory.TRENDING) }
    var videos by remember { mutableStateOf(emptyList<Video>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(repository, category, reloadKey) {
        loading = true
        error = null
        try {
            videos = repository.searchVideos(category.query)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            error = failure.message ?: failure.javaClass.simpleName
        } finally {
            loading = false
        }
    }

    ScreenColumn(title = "Explore", subtitle = "Browse YouTube categories") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ExploreCategory.entries.forEach { item ->
                Button(onClick = { category = item }, enabled = category != item) { Text(item.label) }
            }
        }
        Spacer(Modifier.height(14.dp))
        when {
            loading -> {
                LoadingState()
            }

            error != null -> {
                ErrorState(error!!, onRetry = { reloadKey++ })
            }

            videos.isEmpty() -> {
                EmptyState("No videos were returned for ${category.label}.")
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

private enum class ExploreCategory(
    val label: String,
    val query: String,
) {
    TRENDING("Trending", "trending today"),
    GAMING("Gaming", "gaming"),
    NEWS("News", "news today"),
    SPORTS("Sports", "sports"),
    LEARNING("Learning", "education learning"),
}
