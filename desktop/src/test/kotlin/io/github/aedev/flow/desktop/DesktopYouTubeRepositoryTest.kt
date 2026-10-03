package io.github.aedev.flow.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneOffset

class DesktopYouTubeRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun parsesSupportedYouTubeVideoUrls() {
        assertEquals("watch-id", youtubeVideoId("https://www.youtube.com/watch?v=watch-id&feature=share"))
        assertEquals("short-id", youtubeVideoId("https://www.youtube.com/shorts/short-id?feature=share"))
        assertEquals("short-id", youtubeVideoId("https://youtu.be/short-id?t=5"))
        assertEquals("live-id", youtubeVideoId("https://www.youtube.com/live/live-id?feature=share"))
    }

    @Test
    fun rejectsUrlsWithoutVideoIds() {
        assertNull(youtubeVideoId("https://www.youtube.com/shorts/"))
        assertNull(youtubeVideoId("https://www.youtube.com/watch?feature=share"))
        assertNull(youtubeVideoId("not a url"))
    }

    @Test
    fun parsesFlatPlaylistMetadata() {
        val repository = DesktopYouTubeRepository(null)
        val videos =
            repository.parseVideoList(
                """{"entries":[{"id":"abc123xyz00","title":"Desktop test","channel":"Flow Channel","channel_id":"UC123","duration":125.0,"view_count":4200,"upload_date":"20261003","thumbnails":[{"url":"small","width":120,"height":90},{"url":"large","width":1280,"height":720}]}]}""",
            )

        assertEquals(1, videos.size)
        assertEquals("abc123xyz00", videos.single().id)
        assertEquals("Flow Channel", videos.single().channelName)
        assertEquals("large", videos.single().thumbnailUrl)
        assertEquals(125, videos.single().duration)
        assertEquals(4200L, videos.single().viewCount)
        assertEquals("2026-10-03", videos.single().uploadDate)
        assertEquals(
            LocalDate
                .of(2026, 10, 3)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
            videos.single().timestamp,
        )
    }

    @Test
    fun searchUsesConfiguredResolver() =
        kotlinx.coroutines.runBlocking {
            val script = temporaryFolder.root.toPath().resolve("fake-yt-dlp")
            Files.writeString(
                script,
                "#!/bin/sh\nprintf '%s\\n' '{\"entries\":[{\"id\":\"resolver001\",\"title\":\"Resolved video\",\"channel\":\"Resolver\",\"duration\":60}]}'\n",
            )
            assertTrue(script.toFile().setExecutable(true, true))

            val result = DesktopYouTubeRepository(script).searchVideos("kotlin desktop")

            assertEquals(listOf("resolver001"), result.map { it.id })
        }
}
