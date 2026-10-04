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
            val ffmpeg =
                executableScript(
                    root.resolve("fake-ffmpeg"),
                    """
                    output=''
                    while [ "${'$'}#" -gt 0 ]; do
                      output=${'$'}1
                      shift
                    done
                    printf 'media' > "${'$'}output"
                    """.trimIndent(),
                )
            val downloader = DesktopDownloader(root.resolve("downloads"), resolver(), ffmpeg)

            val result = downloader.download(video())

            assertTrue(Files.isRegularFile(result))
            assertTrue(downloader.activeDownloads.value.isEmpty())
        }

    @Test
    fun duplicateDownloadIsRejectedAndActiveDownloadCanBeCancelled() =
        runBlocking {
            val root = temporaryFolder.root.toPath()
            val ffmpeg =
                executableScript(
                    root.resolve("slow-ffmpeg"),
                    """
                    output=''
                    while [ "${'$'}#" -gt 0 ]; do
                      output=${'$'}1
                      shift
                    done
                    printf 'partial' > "${'$'}output"
                    sleep 30
                    """.trimIndent(),
                )
            val downloader = DesktopDownloader(root.resolve("downloads"), resolver(), ffmpeg)
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
            assertTrue(downloader.listDownloadedFiles().isEmpty())
            assertTrue(Files.list(root.resolve("downloads")).use { paths -> paths.noneMatch { it.fileName.toString().endsWith(".part") } })
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

    @Test
    fun downloadRequiresFfmpegEvenWhenYouTubeExtractionIsAvailable() {
        val downloader = DesktopDownloader(temporaryFolder.root.toPath(), resolver(), ffmpeg = null)

        assertFalse(downloader.isAvailable)
        assertTrue(downloader.unavailableReason!!.contains("ffmpeg"))
    }

    @Test
    fun liveStreamsAreRejectedBeforeResolutionOrFfmpeg() =
        runBlocking {
            var resolved = false
            val downloader =
                DesktopDownloader(
                    temporaryFolder.root.toPath(),
                    DesktopYouTubeMediaResolver {
                        resolved = true
                        error("should not resolve")
                    },
                    ffmpeg = temporaryFolder.newFile("ffmpeg").toPath(),
                )

            val failure = runCatching { downloader.download(video().copy(isLive = true)) }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
            assertFalse(resolved)
        }

    @Test
    fun failedDownloadRemovesPartialArtifact() =
        runBlocking {
            val root = temporaryFolder.root.toPath()
            val ffmpeg =
                executableScript(
                    root.resolve("failing-ffmpeg"),
                    """
                    output=''
                    while [ "${'$'}#" -gt 0 ]; do
                      output=${'$'}1
                      shift
                    done
                    printf 'partial' > "${'$'}output"
                    exit 9
                    """.trimIndent(),
                )
            val downloader = DesktopDownloader(root.resolve("downloads"), resolver(), ffmpeg)

            val failure = runCatching { downloader.download(video()) }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertTrue(downloader.listDownloadedFiles().isEmpty())
            assertTrue(Files.list(root.resolve("downloads")).use { paths -> paths.noneMatch { it.fileName.toString().endsWith(".part") } })
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

    private fun resolver() =
        DesktopYouTubeMediaResolver {
            DesktopResolvedMedia(
                title = "Desktop download",
                playbackUrl = "https://media.example/master.m3u8",
                videoUrl = "https://media.example/video.mp4",
                audioUrl = "https://media.example/audio.m4a",
                progressiveUrl = "https://media.example/progressive.mp4",
            )
        }
}
