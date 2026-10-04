package io.github.aedev.flow.desktop

import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

internal class DesktopDownloader(
    val directory: Path = defaultDownloadDirectory().resolve("Flow"),
    private val resolver: DesktopYouTubeMediaResolver? = NewPipeYouTubeMediaResolver(),
    private val ffmpeg: Path? = findExecutable("ffmpeg"),
) {
    val isAvailable: Boolean = resolver != null && ffmpeg != null
    val unavailableReason: String? =
        when {
            resolver == null -> "YouTube media extraction is unavailable."
            ffmpeg == null -> "Install ffmpeg to enable downloads."
            else -> null
        }
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeStateLock = Any()
    private val _activeDownloads = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, String>> = _activeDownloads.asStateFlow()

    fun isDownloading(videoId: String): Boolean = activeJobs.containsKey(videoId)

    fun cancel(videoId: String): Boolean {
        val job = activeJobs[videoId] ?: return false
        job.cancel(CancellationException("Download cancelled"))
        return true
    }

    fun delete(path: Path): Boolean {
        val root = directory.toAbsolutePath().normalize()
        val target = path.toAbsolutePath().normalize()
        require(target.startsWith(root)) { "Refusing to delete a file outside $root" }
        return Files.deleteIfExists(target)
    }

    fun listDownloadedFiles(): List<Path> {
        if (Files.notExists(directory)) return emptyList()
        check(Files.isDirectory(directory)) { "Download path is not a directory: $directory" }
        return Files.list(directory).use { stream ->
            stream
                .filter { path -> Files.isRegularFile(path) && path.isCompletedDownload() }
                .toList()
                .sortedByDescending { path ->
                    runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
                }
        }
    }

    suspend fun download(video: Video): Path =
        withContext(Dispatchers.IO) {
            require(!video.isLive) { "Live streams cannot be downloaded until the stream has ended." }
            val mediaResolver = resolver ?: error(unavailableReason ?: "YouTube media extraction is unavailable")
            val ffmpegExecutable = ffmpeg ?: error(unavailableReason ?: "ffmpeg is unavailable")
            val job = currentCoroutineContext().job
            check(activeJobs.putIfAbsent(video.id, job) == null) { "${video.title} is already downloading." }
            updateActive(video.id, video.title)
            var partialOutput: Path? = null
            try {
                Files.createDirectories(directory)
                val media = mediaResolver.resolve("https://www.youtube.com/watch?v=${video.id}")
                val output = uniqueDownloadPath(media.title.ifBlank { video.title }, video.id)
                val staging = output.resolveSibling("${output.fileName}.part")
                partialOutput = staging
                val command = ffmpegDownloadCommand(ffmpegExecutable, media, staging)
                val result = runProcess(command, timeout = Duration.ofMinutes(20))
                check(result.exitCode == 0) {
                    result.stderr.lineSequence().lastOrNull(String::isNotBlank)
                        ?: "Download failed with exit code ${result.exitCode}"
                }
                check(Files.isRegularFile(staging) && Files.size(staging) > 0L) {
                    "Download finished without producing a media file."
                }
                moveCompletedDownload(staging, output)
                partialOutput = null
                output
            } finally {
                partialOutput?.let { path -> runCatching { Files.deleteIfExists(path) } }
                activeJobs.remove(video.id, job)
                updateActive(video.id, null)
            }
        }

    private fun ffmpegDownloadCommand(
        executable: Path,
        media: DesktopResolvedMedia,
        output: Path,
    ): List<String> {
        val videoUrl = media.videoUrl
        val audioUrl = media.audioUrl
        return if (videoUrl != null && audioUrl != null) {
            listOf(
                executable.toString(),
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-i",
                videoUrl,
                "-i",
                audioUrl,
                "-map",
                "0:v:0",
                "-map",
                "1:a:0",
                "-c",
                "copy",
                "-f",
                "mp4",
                output.toString(),
            )
        } else {
            val source = media.progressiveUrl ?: media.downloadUrl ?: error("YouTube returned no MP4-compatible download stream.")
            listOf(
                executable.toString(),
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-i",
                source,
                "-c",
                "copy",
                "-f",
                "mp4",
                output.toString(),
            )
        }
    }

    private fun moveCompletedDownload(
        staging: Path,
        output: Path,
    ) {
        runCatching {
            Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse {
            Files.move(staging, output)
        }
    }

    private fun uniqueDownloadPath(
        title: String,
        videoId: String,
    ): Path {
        val safeTitle =
            title
                .replace(Regex("[^A-Za-z0-9._ -]+"), "_")
                .trim(' ', '.', '_')
                .take(MAX_FILE_TITLE_LENGTH)
                .ifBlank { "video" }
        val base = "$safeTitle [$videoId]"
        var candidate = directory.resolve("$base.mp4")
        var suffix = 2
        while (Files.exists(candidate)) {
            candidate = directory.resolve("$base ($suffix).mp4")
            suffix++
        }
        return candidate
    }

    private fun updateActive(
        videoId: String,
        title: String?,
    ) {
        synchronized(activeStateLock) {
            _activeDownloads.value =
                if (title == null) {
                    _activeDownloads.value - videoId
                } else {
                    _activeDownloads.value + (videoId to title)
                }
        }
    }

    private companion object {
        const val MAX_FILE_TITLE_LENGTH = 180
    }
}

private fun Path.isCompletedDownload(): Boolean {
    val name = fileName.toString()
    return !name.endsWith(".part", ignoreCase = true) && !name.endsWith(".ytdl", ignoreCase = true)
}
