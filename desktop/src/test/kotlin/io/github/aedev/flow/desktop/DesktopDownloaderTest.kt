package io.github.aedev.flow.desktop

import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class DesktopDownloaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun downloadTracksActiveStateAndReturnsProducedFile() =
        runBlocking {
            val root = temporaryFolder.root.toPath()
            val resolver =
                executableScript(
                    root.resolve("fake-yt-dlp"),
                    """
                    output_template=''
                    while [ "${'$'}#" -gt 0 ]; do
                      if [ "${'$'}1" = '-o' ]; then shift; output_template=${'$'}1; fi
                      shift
                    done
                    output_dir=${'$'}(dirname "${'$'}output_template")
                    mkdir -p "${'$'}output_dir"
                    output="${'$'}output_dir/fake.mp4"
                    printf 'media' > "${'$'}output"
                    printf '%s\n' "${'$'}output"
                    """.trimIndent(),
                )
            val downloader = DesktopDownloader(root.resolve("downloads"), resolver, ffmpeg = null)

            val result = downloader.download(video())

            assertTrue(Files.isRegularFile(result))
            assertTrue(downloader.activeDownloads.value.isEmpty())
        }

    @Test
    fun duplicateDownloadIsRejectedAndActiveDownloadCanBeCancelled() =
        runBlocking {
            val root = temporaryFolder.root.toPath()
            val resolver =
                executableScript(
                    root.resolve("slow-yt-dlp"),
                    """
                    output_template=''
                    while [ "${'$'}#" -gt 0 ]; do
                      if [ "${'$'}1" = '-o' ]; then shift; output_template=${'$'}1; fi
                      shift
                    done
                    output_dir=${'$'}(dirname "${'$'}output_template")
                    mkdir -p "${'$'}output_dir"
                    echo ${'$'}${'$'} > "${'$'}output_dir/resolver.pid"
                    sleep 30
                    """.trimIndent(),
                )
            val downloader = DesktopDownloader(root.resolve("downloads"), resolver, ffmpeg = null)
            val video = video()
            val job = launch(Dispatchers.Default) { downloader.download(video) }

            withTimeout(2_000) {
                while (!downloader.isDownloading(video.id)) delay(10)
            }
            val duplicate = runCatching { downloader.download(video) }.exceptionOrNull()
            assertTrue(duplicate is IllegalStateException)
            assertTrue(downloader.cancel(video.id))
            job.cancelAndJoin()

            withTimeout(2_000) {
                while (downloader.activeDownloads.value.isNotEmpty()) delay(10)
            }
            assertFalse(downloader.isDownloading(video.id))
        }

    @Test
    fun deleteRejectsPathsOutsideDownloadDirectory() {
        val root = temporaryFolder.root.toPath()
        val directory = Files.createDirectories(root.resolve("downloads"))
        val inside = Files.writeString(directory.resolve("video.mp4"), "media")
        val outside = Files.writeString(root.resolve("outside.mp4"), "media")
        val downloader = DesktopDownloader(directory, resolver = null, ffmpeg = null)

        assertTrue(downloader.delete(inside))
        assertFalse(Files.exists(inside))
        val failure = runCatching { downloader.delete(outside) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(Files.exists(outside))
    }

    @Test
    fun listDownloadedFilesRejectsNonDirectoryAndReturnsRegularFiles() {
        val root = temporaryFolder.root.toPath()
        val missingDownloader = DesktopDownloader(root.resolve("missing"), resolver = null, ffmpeg = null)
        assertTrue(missingDownloader.listDownloadedFiles().isEmpty())

        val directory = Files.createDirectories(root.resolve("downloads"))
        val first = Files.writeString(directory.resolve("first.mp4"), "first")
        val second = Files.writeString(directory.resolve("second.mp4"), "second")
        Files.writeString(directory.resolve("unfinished.mp4.part"), "partial")
        Files.writeString(directory.resolve("unfinished.ytdl"), "resume metadata")
        Files.createDirectories(directory.resolve("nested"))
        val downloader = DesktopDownloader(directory, resolver = null, ffmpeg = null)

        assertEquals(setOf(first, second), downloader.listDownloadedFiles().toSet())

        val invalid = Files.writeString(root.resolve("not-a-directory"), "content")
        val invalidDownloader = DesktopDownloader(invalid, resolver = null, ffmpeg = null)
        assertTrue(runCatching { invalidDownloader.listDownloadedFiles() }.exceptionOrNull() is IllegalStateException)
    }

    private fun executableScript(
        path: Path,
        body: String,
    ): Path {
        Files.writeString(path, "#!/bin/sh\n$body\n")
        assertTrue(path.toFile().setExecutable(true, true))
        return path
    }

    private fun video() =
        Video(
            id = "desktop-download",
            title = "Desktop download",
            channelName = "Flow Test",
            channelId = "channel",
            thumbnailUrl = "",
            duration = 60,
            viewCount = 1,
            uploadDate = "2026-10-03",
        )
}
