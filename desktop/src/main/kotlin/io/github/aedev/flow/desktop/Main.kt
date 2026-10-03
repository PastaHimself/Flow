package io.github.aedev.flow.desktop

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.compose.AsyncImage
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class Destination(
    val label: String,
) {
    HOME("Home"),
    SEARCH("Search"),
    LIBRARY("Library"),
    SETTINGS("Settings"),
}

fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Flow",
        ) {
            FlowDesktopTheme {
                FlowDesktopApp()
            }
        }
    }

@Composable
private fun FlowDesktopTheme(content: @Composable () -> Unit) {
    val colors =
        darkColorScheme(
            primary = Color(0xFFFF0000),
            onPrimary = Color.White,
            secondary = Color(0xFFAAAAAA),
            background = Color(0xFF0F0F0F),
            surface = Color(0xFF1D1D1D),
            onSurface = Color(0xFFF4F4F4),
            onSurfaceVariant = Color(0xFFB8B8B8),
            outline = Color(0xFF343434),
            error = Color(0xFFEF5350),
        )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun FlowDesktopApp() {
    val repository = remember { DesktopYouTubeRepository() }
    val libraryStore = remember { DesktopLibraryStore() }
    val player = remember { DesktopMpvPlayer() }
    val scope = rememberCoroutineScope()
    var destination by remember { mutableStateOf(Destination.HOME) }
    val initialSavedVideos = remember { libraryStore.load() }
    var savedVideos by remember { mutableStateOf(initialSavedVideos) }
    var statusMessage by remember {
        mutableStateOf(
            libraryStore.loadError?.let { error ->
                "Could not read the existing library: ${error.message ?: error.javaClass.simpleName}. Flow will not overwrite it."
            },
        )
    }
    var homeVideos by remember { mutableStateOf(emptyList<Video>()) }
    var homeLoading by remember { mutableStateOf(true) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var homeReloadKey by remember { mutableStateOf(0) }
    val recommendationQuery = libraryStore.recommendationQuery(savedVideos)

    fun toggleSaved(video: Video) {
        val updatedVideos =
            if (savedVideos.any { it.id == video.id }) {
                savedVideos.filterNot { it.id == video.id }
            } else {
                listOf(video) + savedVideos
            }
        runCatching { libraryStore.save(updatedVideos) }
            .onSuccess { savedVideos = updatedVideos }
            .onFailure { statusMessage = "Could not save library: ${it.message}" }
    }

    fun play(video: Video) {
        if (!player.isAvailable) {
            statusMessage = player.unavailableReason
            return
        }
        scope.launch {
            val error =
                withContext(Dispatchers.IO) {
                    runCatching { player.play("https://www.youtube.com/watch?v=${video.id}") }.exceptionOrNull()
                }
            if (error != null) statusMessage = "Playback failed: ${error.message}"
        }
    }

    LaunchedEffect(destination, recommendationQuery, homeReloadKey) {
        if (destination != Destination.HOME) return@LaunchedEffect
        homeLoading = true
        homeError = null
        try {
            homeVideos = if (recommendationQuery == null) repository.trending() else repository.searchVideos(recommendationQuery)
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
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
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
                                        Destination.LIBRARY -> Icons.Default.LibraryMusic
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
                            savedIds = savedVideos.mapTo(hashSetOf()) { it.id },
                            recommendationQuery = recommendationQuery,
                            onRetry = { homeReloadKey++ },
                            onPlay = ::play,
                            onToggleSaved = ::toggleSaved,
                        )
                    }

                    Destination.SEARCH -> {
                        SearchScreen(
                            repository = repository,
                            savedIds = savedVideos.mapTo(hashSetOf()) { it.id },
                            onPlay = ::play,
                            onToggleSaved = ::toggleSaved,
                        )
                    }

                    Destination.LIBRARY -> {
                        LibraryScreen(
                            videos = savedVideos,
                            onPlay = ::play,
                            onToggleSaved = ::toggleSaved,
                        )
                    }

                    Destination.SETTINGS -> {
                        SettingsScreen(player = player, libraryStore = libraryStore)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    videos: List<Video>,
    loading: Boolean,
    error: String?,
    savedIds: Set<String>,
    recommendationQuery: String?,
    onRetry: () -> Unit,
    onPlay: (Video) -> Unit,
    onToggleSaved: (Video) -> Unit,
) {
    ScreenColumn(
        title = "Home",
        subtitle = recommendationQuery?.let { "Local recommendation seed: $it" } ?: "Trending on YouTube",
    ) {
        when {
            loading -> LoadingState()
            error != null -> ErrorState(error, onRetry)
            videos.isEmpty() -> EmptyState("No videos were returned.")
            else -> VideoList(videos, savedIds, onPlay, onToggleSaved)
        }
    }
}

@Composable
private fun SearchScreen(
    repository: DesktopYouTubeRepository,
    savedIds: Set<String>,
    onPlay: (Video) -> Unit,
    onToggleSaved: (Video) -> Unit,
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
        when {
            loading -> LoadingState()
            error != null -> ErrorState(error!!, ::search)
            results.isEmpty() -> EmptyState("Enter a query to find videos.")
            else -> VideoList(results, savedIds, onPlay, onToggleSaved)
        }
    }
}

@Composable
private fun LibraryScreen(
    videos: List<Video>,
    onPlay: (Video) -> Unit,
    onToggleSaved: (Video) -> Unit,
) {
    ScreenColumn(title = "Library", subtitle = "Saved locally on this computer") {
        if (videos.isEmpty()) {
            EmptyState("Save videos from Home or Search to build your desktop library.")
        } else {
            VideoList(videos, videos.mapTo(hashSetOf()) { it.id }, onPlay, onToggleSaved)
        }
    }
}

@Composable
private fun SettingsScreen(
    player: DesktopMpvPlayer,
    libraryStore: DesktopLibraryStore,
) {
    ScreenColumn(title = "Settings", subtitle = "Linux (experimental)") {
        SettingCard(
            title = "Playback",
            body =
                if (player.isAvailable) {
                    "mpv + yt-dlp detected. Playback opens in an mpv window."
                } else {
                    player.unavailableReason.orEmpty()
                },
        )
        SettingCard(
            title = "Local data",
            body = "Saved library: ${libraryStore.file}",
        )
        SettingCard(
            title = "Current desktop scope",
            body =
                "Home/discovery, search, metadata, thumbnails, local saved library and optional mpv playback are implemented. " +
                    "Android-only downloads, casting, widgets, notifications, CameraX, WorkManager and Media3 features remain " +
                    "unavailable on Linux.",
        )
        SettingCard(
            title = "Recommendations",
            body =
                "The desktop seed picker reuses FlowNeuro's shared text normalization and keeps its data local. " +
                    "The full Android FlowNeuro profile/storage engine is not enabled yet because it currently depends on " +
                    "Android lifecycle, " +
                    "preferences and storage APIs.",
        )
    }
}

@Composable
private fun SettingCard(
    title: String,
    body: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.height(6.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScreenColumn(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(22.dp))
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun VideoList(
    videos: List<Video>,
    savedIds: Set<String>,
    onPlay: (Video) -> Unit,
    onToggleSaved: (Video) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(videos, key = { it.id }) { video ->
            VideoCard(
                video = video,
                saved = video.id in savedIds,
                onPlay = { onPlay(video) },
                onToggleSaved = { onToggleSaved(video) },
            )
        }
    }
}

@Composable
private fun VideoCard(
    video: Video,
    saved: Boolean,
    onPlay: () -> Unit,
    onToggleSaved: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(width = 224.dp, height = 126.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
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
                        text = metadata.joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                if (video.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = video.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                    )
                }
            }
            IconButton(onClick = onToggleSaved) {
                Icon(
                    imageVector = if (saved) Icons.Default.Bookmarks else Icons.Outlined.BookmarkAdd,
                    contentDescription = if (saved) "Remove from library" else "Save to library",
                )
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Text("Could not load videos", fontWeight = FontWeight.SemiBold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Retry") }
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
