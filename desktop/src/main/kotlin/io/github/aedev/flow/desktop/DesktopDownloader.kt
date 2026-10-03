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
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class DesktopDownloader(
    val directory: Path = defaultDownloadDirectory().resolve("Flow"),
    private val resolver: Path? = findExecutable("yt-dlp"),
    private val ffmpeg: Path? = findExecutable("ffmpeg"),
) {
    val isAvailable: Boolean = resolver != null
    val unavailableReason: String? = if (resolver == null) "Install yt-dlp to enable downloads." else null
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
            val executable = resolver ?: error(unavailableReason ?: "yt-dlp is unavailable")
            val job = currentCoroutineContext().job
            check(activeJobs.putIfAbsent(video.id, job) == null) { "${video.title} is already downloading." }
            updateActive(video.id, video.title)
            try {
                Files.createDirectories(directory)
                val outputTemplate = directory.resolve("%(title).180B [%(id)s].%(ext)s").toString()
                val command =
                    buildList {
                        add(executable.toString())
                        add("--no-playlist")
                        add("--no-warnings")
                        add("--restrict-filenames")
                        add("--print")
                        add("after_move:filepath")
                        if (ffmpeg != null) {
                            add("--merge-output-format")
                            add("mp4")
                        }
                        add("-o")
                        add(outputTemplate)
                        add("https://www.youtube.com/watch?v=${video.id}")
                    }
                val result = runProcess(command, timeout = Duration.ofMinutes(20))
                check(result.exitCode == 0) {
                    result.stderr.lineSequence().lastOrNull(String::isNotBlank)
                        ?: "Download failed with exit code ${result.exitCode}"
                }
                val downloaded =
                    result.stdout
                        .lineSequence()
                        .lastOrNull(String::isNotBlank)
                        ?.let(Path::of)
                check(downloaded != null && Files.isRegularFile(downloaded)) {
                    "Download finished without producing a media file."
                }
                downloaded
            } finally {
                activeJobs.remove(video.id, job)
                updateActive(video.id, null)
            }
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
}

private fun Path.isCompletedDownload(): Boolean {
    val name = fileName.toString()
    return !name.endsWith(".part", ignoreCase = true) && !name.endsWith(".ytdl", ignoreCase = true)
}
