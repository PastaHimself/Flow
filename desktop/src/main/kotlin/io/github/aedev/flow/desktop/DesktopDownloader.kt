package io.github.aedev.flow.desktop

import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class DesktopDownloader(
    val directory: Path = defaultDownloadDirectory().resolve("Flow"),
    private val resolver: Path? = findExecutable("yt-dlp") ?: findExecutable("youtube-dl"),
    private val ffmpeg: Path? = findExecutable("ffmpeg"),
) {
    val isAvailable: Boolean = resolver != null
    val unavailableReason: String? = if (resolver == null) "Install yt-dlp to enable downloads." else null

    suspend fun download(video: Video): Path =
        withContext(Dispatchers.IO) {
            val executable = resolver ?: error(unavailableReason ?: "yt-dlp is unavailable")
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
            check(downloaded != null && Files.isRegularFile(downloaded)) { "Download finished without producing a media file." }
            downloaded
        }
}
