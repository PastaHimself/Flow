package io.github.aedev.flow.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class DesktopRuntimeSmokeTest {
    @Test
    fun realNewPipeMpvAndFfmpegSmoke() =
        runBlocking {
            assumeTrue(
                "Set FLOW_RUN_NETWORK_SMOKE=1 to run the external YouTube/mpv/ffmpeg smoke.",
                System.getenv("FLOW_RUN_NETWORK_SMOKE") == "1",
            )
            val mediaUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
            val resolver = NewPipeYouTubeMediaResolver()
            val resolved = withContext(Dispatchers.IO) { resolver.resolve(mediaUrl) }
            assertTrue(resolved.playbackUrl.startsWith("http"))

            val root = Files.createTempDirectory("flow-runtime-smoke-")
            val player =
                DesktopMpvPlayer(
                    mpv = Path.of("/usr/bin/mpv"),
                    youtubeResolver = resolver,
                    diagnosticsFile = root.resolve("mpv.log"),
                )
            try {
                withContext(Dispatchers.IO) { player.play(mediaUrl) }
                var attempts = 0
                while (player.playbackState.value.mediaUrl != mediaUrl && attempts < 150) {
                    delay(100)
                    attempts++
                }
                assertEquals(mediaUrl, player.playbackState.value.mediaUrl)
                withContext(Dispatchers.IO) {
                    player.seekBy(3.0)
                    player.togglePause()
                    player.togglePause()
                    player.stop()
                }
            } finally {
                player.close()
            }

            val video = requireNotNull(resolved.video)
            val downloader =
                DesktopDownloader(
                    directory = root.resolve("downloads"),
                    resolver = resolver,
                    ffmpeg = Path.of("/usr/bin/ffmpeg"),
                )
            val downloaded = downloader.download(video)
            assertTrue(Files.size(downloaded) > 0L)

            val probe =
                runProcess(
                    listOf(
                        "/usr/bin/ffprobe",
                        "-v",
                        "error",
                        "-show_entries",
                        "stream=codec_type",
                        "-of",
                        "csv=p=0",
                        downloaded.toString(),
                    ),
                    timeout = Duration.ofSeconds(30),
                )
            assertEquals(probe.stderr, 0, probe.exitCode)
            assertTrue(probe.stdout.lineSequence().any { it.trim() == "video" })
            assertTrue(probe.stdout.lineSequence().any { it.trim() == "audio" })
        }
}
