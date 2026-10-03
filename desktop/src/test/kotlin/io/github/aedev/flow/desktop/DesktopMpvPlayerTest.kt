package io.github.aedev.flow.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class DesktopMpvPlayerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun localPlaybackRemainsAvailableWithoutYouTubeResolver() {
        val mpv = temporaryFolder.newFile("mpv").toPath()
        val player = DesktopMpvPlayer(mpv = mpv, youtubeResolver = null)

        assertTrue(player.isAvailable)
        assertTrue(player.canPlayLocal)
        assertFalse(player.canPlayYouTube)
        assertNull(player.unavailableReason)
        assertTrue(player.youtubeUnavailableReason!!.contains("yt-dlp"))
    }

    @Test
    fun failedMpvStartupKeepsDiagnostics() {
        val mpv = temporaryFolder.newFile("mpv-failure").toPath()
        Files.writeString(mpv, "#!/bin/sh\nprintf 'mpv test failure' >&2\nexit 12\n")
        assertTrue(mpv.toFile().setExecutable(true, true))
        val diagnostics = temporaryFolder.root.toPath().resolve("cache/mpv.log")
        val player = DesktopMpvPlayer(mpv = mpv, youtubeResolver = null, diagnosticsFile = diagnostics)

        val failure = runCatching { player.play(temporaryFolder.newFile("local.mp4").absolutePath) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure!!.message!!.contains(diagnostics.toString()))
        assertTrue(Files.readString(diagnostics).contains("mpv test failure"))
    }
}
