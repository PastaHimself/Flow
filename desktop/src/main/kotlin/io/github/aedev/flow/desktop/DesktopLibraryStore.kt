package io.github.aedev.flow.desktop

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.NeuroText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

class DesktopLibraryStore(
    val dataDirectory: Path = defaultDataDirectory(),
) {
    val file: Path = dataDirectory.resolve("library.json")
    val historyFile: Path = dataDirectory.resolve("history.json")
    val subscriptionsFile: Path = dataDirectory.resolve("subscriptions.json")
    val playlistsFile: Path = dataDirectory.resolve("playlists.json")
    val searchHistoryFile: Path = dataDirectory.resolve("search-history.json")
    var loadError: Throwable? = null
        private set
    val loadErrors: Map<Path, Throwable>
        get() =
            buildMap {
                loadError?.let { put(file, it) }
                putAll(loadErrorsByPath)
            }

    private val json = Json { prettyPrint = true }
    private val loadErrorsByPath = ConcurrentHashMap<Path, Throwable>()

    fun load(): List<Video> {
        if (!Files.isRegularFile(file)) {
            loadError = null
            return emptyList()
        }
        return runCatching {
            json.decodeFromString<List<SavedVideo>>(Files.readString(file)).map(SavedVideo::toVideo)
        }.onSuccess {
            loadError = null
        }.onFailure { error ->
            loadError = error
        }.getOrDefault(emptyList())
    }

    fun save(videos: List<Video>) {
        check(loadError == null) {
            "Existing library could not be read; refusing to overwrite $file. Restart Flow after repairing or removing that file."
        }
        writeAtomically(file, json.encodeToString(videos.map(SavedVideo::fromVideo)))
    }

    fun loadHistory(): List<Video> = readVideos(historyFile)

    fun recordWatched(video: Video): List<Video> {
        val updated =
            (listOf(video.copy(timestamp = System.currentTimeMillis())) + loadHistory().filterNot { it.id == video.id })
                .take(MAX_HISTORY_ITEMS)
        writeAtomically(historyFile, json.encodeToString(updated.map(SavedVideo::fromVideo)))
        return updated
    }

    fun clearHistory() {
        Files.deleteIfExists(historyFile)
        loadErrorsByPath.remove(historyFile)
    }

    fun loadSubscriptions(): List<DesktopSubscription> = readList(subscriptionsFile)

    fun toggleSubscription(video: Video): List<DesktopSubscription> {
        val channelId = video.channelId.ifBlank { return loadSubscriptions() }
        val current = loadSubscriptions()
        val updated =
            if (current.any { it.channelId == channelId }) {
                current.filterNot { it.channelId == channelId }
            } else {
                listOf(
                    DesktopSubscription(
                        channelId = channelId,
                        channelName = video.channelName.ifBlank { channelId },
                        thumbnailUrl = video.channelThumbnailUrl,
                    ),
                ) + current
            }
        writeAtomically(subscriptionsFile, json.encodeToString(updated))
        return updated
    }

    fun loadPlaylists(): List<DesktopPlaylist> = readList<SavedPlaylist>(playlistsFile).map(SavedPlaylist::toPlaylist)

    fun addToPlaylist(
        playlistName: String,
        video: Video,
    ): List<DesktopPlaylist> {
        val name = playlistName.trim()
        require(name.isNotEmpty()) { "Playlist name cannot be empty." }
        val current = loadPlaylists()
        val existing = current.firstOrNull { it.name.equals(name, ignoreCase = true) }
        val updatedPlaylist =
            if (existing == null) {
                DesktopPlaylist(name, listOf(video))
            } else {
                existing.copy(videos = listOf(video) + existing.videos.filterNot { it.id == video.id })
            }
        val updated = listOf(updatedPlaylist) + current.filterNot { it.name.equals(name, ignoreCase = true) }
        writeAtomically(playlistsFile, json.encodeToString(updated.map(SavedPlaylist::fromPlaylist)))
        return updated
    }

    fun removePlaylist(name: String): List<DesktopPlaylist> {
        val updated = loadPlaylists().filterNot { it.name == name }
        writeAtomically(playlistsFile, json.encodeToString(updated.map(SavedPlaylist::fromPlaylist)))
        return updated
    }

    fun loadSearchHistory(): List<String> = readList(searchHistoryFile)

    fun recordSearch(query: String): List<String> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return loadSearchHistory()
        val updated = (listOf(normalized) + loadSearchHistory().filterNot { it.equals(normalized, ignoreCase = true) }).take(20)
        writeAtomically(searchHistoryFile, json.encodeToString(updated))
        return updated
    }

    fun recommendationQuery(videos: List<Video>): String? {
        val counts = mutableMapOf<String, Int>()
        videos
            .take(30)
            .flatMap { video -> NeuroText.words(NeuroText.fold("${video.title} ${video.channelName}")) }
            .map { word -> word.trim { char -> !NeuroText.isWordChar(char) } }
            .filter(NeuroText::isTopicSized)
            .forEach { word -> counts[word] = counts.getOrDefault(word, 0) + 1 }
        return counts.maxByOrNull { it.value }?.key
    }

    companion object {
        private const val MAX_HISTORY_ITEMS = 500
    }

    private fun readVideos(path: Path): List<Video> = readList<SavedVideo>(path).map(SavedVideo::toVideo)

    private inline fun <reified T> readList(path: Path): List<T> {
        if (!Files.isRegularFile(path)) {
            loadErrorsByPath.remove(path)
            return emptyList()
        }
        return runCatching {
            json.decodeFromString<List<T>>(Files.readString(path))
        }.onSuccess {
            loadErrorsByPath.remove(path)
        }.onFailure { error ->
            loadErrorsByPath[path] = error
        }.getOrDefault(emptyList())
    }

    private fun writeAtomically(
        path: Path,
        content: String,
    ) {
        check(loadErrorsByPath[path] == null) {
            "Existing ${path.fileName} could not be read; refusing to overwrite $path. Repair or remove that file and reload it first."
        }
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, "${path.fileName}-", ".tmp")
        try {
            Files.writeString(temporary, content)
            runCatching {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            }.getOrElse {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

@Serializable
data class DesktopSubscription(
    val channelId: String,
    val channelName: String,
    val thumbnailUrl: String = "",
)

data class DesktopPlaylist(
    val name: String,
    val videos: List<Video>,
)

@Serializable
private data class SavedVideo(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val duration: Int,
    val viewCount: Long,
    val uploadDate: String,
    val description: String,
    val isLive: Boolean,
    val isShort: Boolean,
    val timestamp: Long = 0L,
) {
    fun toVideo(): Video =
        Video(
            id = id,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnailUrl,
            duration = duration,
            viewCount = viewCount,
            uploadDate = uploadDate,
            description = description,
            isLive = isLive,
            isShort = isShort,
            timestamp = timestamp,
        )

    companion object {
        fun fromVideo(video: Video): SavedVideo =
            SavedVideo(
                id = video.id,
                title = video.title,
                channelName = video.channelName,
                channelId = video.channelId,
                thumbnailUrl = video.thumbnailUrl,
                duration = video.duration,
                viewCount = video.viewCount,
                uploadDate = video.uploadDate,
                description = video.description,
                isLive = video.isLive,
                isShort = video.isShort,
                timestamp = video.timestamp,
            )
    }
}

@Serializable
private data class SavedPlaylist(
    val name: String,
    val videos: List<SavedVideo>,
) {
    fun toPlaylist() = DesktopPlaylist(name, videos.map(SavedVideo::toVideo))

    companion object {
        fun fromPlaylist(playlist: DesktopPlaylist) = SavedPlaylist(playlist.name, playlist.videos.map(SavedVideo::fromVideo))
    }
}
