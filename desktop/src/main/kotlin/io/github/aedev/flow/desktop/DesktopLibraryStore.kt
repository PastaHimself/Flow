package io.github.aedev.flow.desktop

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.NeuroText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class DesktopLibraryStore(
    dataDirectory: Path = defaultDataDirectory(),
) {
    val file: Path = dataDirectory.resolve("library.json")
    var loadError: Throwable? = null
        private set

    private val json = Json { prettyPrint = true }

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
        Files.createDirectories(file.parent)
        val temporary = Files.createTempFile(file.parent, "library-", ".json")
        Files.writeString(temporary, json.encodeToString(videos.map(SavedVideo::fromVideo)))
        runCatching {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
        }
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
        private fun defaultDataDirectory(): Path {
            val xdg = System.getenv("XDG_DATA_HOME")?.takeIf(String::isNotBlank)
            val base = xdg?.let(Path::of) ?: Path.of(System.getProperty("user.home"), ".local", "share")
            return base.resolve("flow")
        }
    }
}

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
            )
    }
}
