package io.github.aedev.flow.desktop

import org.junit.Assert.assertEquals
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

    @Test
    fun parsesMpvResponsesAndPlaybackEvents() {
        assertEquals(
            MpvIpcMessage.CommandResponse(requestId = 7L, error = "success"),
            parseMpvIpcMessage("""{"request_id":7,"error":"success"}"""),
        )
        assertEquals(
            MpvIpcMessage.FileLoaded,
            parseMpvIpcMessage("""{"event":"file-loaded","playlist_entry_id":4}"""),
        )
        assertEquals(
            MpvIpcMessage.PauseChanged(paused = true),
            parseMpvIpcMessage("""{"event":"property-change","name":"pause","data":true}"""),
        )
        assertEquals(
            MpvIpcMessage.EndFile(reason = "error", detail = "loading failed"),
            parseMpvIpcMessage("""{"event":"end-file","reason":"error","file_error":"loading failed"}"""),
        )
        assertNull(parseMpvIpcMessage("not-json"))
    }

    @Test
    fun replacementStopDoesNotClearPendingLoad() {
        val media = "https://www.youtube.com/watch?v=new"
        var tracker = MpvPlaybackTracker(pendingMedia = media, pendingLoads = listOf(media))

        tracker = reduceMpvPlayback(tracker, MpvIpcMessage.EndFile(reason = "stop"))

        assertNull(tracker.pendingMedia)
        assertNull(tracker.activeMedia)
        assertNull(tracker.error)

        tracker = MpvPlaybackTracker(activeMedia = "old", pendingMedia = media, pendingLoads = listOf(media))
        tracker = reduceMpvPlayback(tracker, MpvIpcMessage.EndFile(reason = "stop"))
        assertEquals(media, tracker.pendingMedia)
        assertEquals(listOf(media), tracker.pendingLoads)
        assertNull(tracker.activeMedia)

        tracker = reduceMpvPlayback(tracker, MpvIpcMessage.FileLoaded)

        assertNull(tracker.pendingMedia)
        assertTrue(tracker.pendingLoads.isEmpty())
        assertEquals(media, tracker.activeMedia)
        assertEquals(media, tracker.lastLoadedMedia)
        assertEquals(1L, tracker.loadGeneration)
    }

    @Test
    fun delayedFileLoadedIsAssociatedWithTheOlderQueuedRequest() {
        val first = "https://www.youtube.com/watch?v=first"
        val second = "https://www.youtube.com/watch?v=second"
        var tracker =
            MpvPlaybackTracker(
                pendingMedia = second,
                pendingLoads = listOf(first, second),
            )

        tracker = reduceMpvPlayback(tracker, MpvIpcMessage.FileLoaded)

        assertEquals(first, tracker.activeMedia)
        assertEquals(first, tracker.lastLoadedMedia)
        assertEquals(second, tracker.pendingMedia)
        assertEquals(listOf(second), tracker.pendingLoads)
        assertEquals(1L, tracker.loadGeneration)

        tracker = reduceMpvPlayback(tracker, MpvIpcMessage.EndFile(reason = "stop"))
        tracker = reduceMpvPlayback(tracker, MpvIpcMessage.FileLoaded)

        assertEquals(second, tracker.activeMedia)
        assertEquals(second, tracker.lastLoadedMedia)
        assertNull(tracker.pendingMedia)
        assertTrue(tracker.pendingLoads.isEmpty())
        assertEquals(2L, tracker.loadGeneration)
    }

    @Test
    fun playbackErrorClearsPendingMediaAndPauseFollowsMpv() {
        var tracker = MpvPlaybackTracker(pendingMedia = "broken", paused = false)
        tracker = reduceMpvPlayback(tracker, MpvIpcMessage.PauseChanged(paused = true))
        assertTrue(tracker.paused)

        tracker = reduceMpvPlayback(tracker, MpvIpcMessage.EndFile(reason = "error", detail = "network error"))

        assertNull(tracker.pendingMedia)
        assertNull(tracker.activeMedia)
        assertFalse(tracker.paused)
        assertEquals("network error", tracker.error)
    }
}
