package io.github.aedev.flow.desktop

import io.github.aedev.flow.data.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.Instant
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
    fun parsesYouTubeChannelAtomFeed() {
        val repository = DesktopYouTubeRepository(null)
        val videos =
            repository.parseChannelFeed(
                payload =
                    """
                    <feed xmlns="http://www.w3.org/2005/Atom"
                          xmlns:yt="http://www.youtube.com/xml/schemas/2015"
                          xmlns:media="http://search.yahoo.com/mrss/">
                      <entry>
                        <yt:videoId>feedvideo01</yt:videoId>
                        <title>Feed video</title>
                        <published>2026-10-03T09:20:23+00:00</published>
                        <author><name>Flow Channel</name></author>
                        <media:group>
                          <media:thumbnail url="https://example.invalid/thumb.jpg" width="480" height="360"/>
                          <media:description>Feed description</media:description>
                          <media:community><media:statistics views="1234"/></media:community>
                        </media:group>
                      </entry>
                    </feed>
                    """.trimIndent(),
                channelId = "UC1234567890123456789012",
                limit = 8,
            )

        assertEquals(1, videos.size)
        assertEquals("feedvideo01", videos.single().id)
        assertEquals("Flow Channel", videos.single().channelName)
        assertEquals("UC1234567890123456789012", videos.single().channelId)
        assertEquals("https://example.invalid/thumb.jpg", videos.single().thumbnailUrl)
        assertEquals(1234L, videos.single().viewCount)
        assertEquals("2026-10-03", videos.single().uploadDate)
        assertEquals(Instant.parse("2026-10-03T09:20:23Z").toEpochMilli(), videos.single().timestamp)
        assertEquals("Feed description", videos.single().description)
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

    @Test
    fun channelFeedFallbackHandlesResolverFailureForChannelIds() =
        kotlinx.coroutines.runBlocking {
            val script = temporaryFolder.root.toPath().resolve("failing-yt-dlp")
            Files.writeString(script, "#!/bin/sh\nprintf 'resolver failed' >&2\nexit 9\n")
            assertTrue(script.toFile().setExecutable(true, true))
            val channelId = "UC1234567890123456789012"
            val repository =
                DesktopYouTubeRepository(
                    resolver = script,
                    channelFeedFetcher = {
                        """
                        <feed xmlns="http://www.w3.org/2005/Atom"
                              xmlns:yt="http://www.youtube.com/xml/schemas/2015"
                              xmlns:media="http://search.yahoo.com/mrss/">
                          <entry>
                            <yt:videoId>fallback001</yt:videoId>
                            <title>Fallback video</title>
                            <published>2026-10-03T09:20:23Z</published>
                            <author><name>Fallback Channel</name></author>
                          </entry>
                        </feed>
                        """.trimIndent()
                    },
                )

            val result = repository.channelVideos(channelId, limit = 8)

            assertEquals(listOf("fallback001"), result.map { it.id })
        }

    @Test
    fun canonicalChannelFeedDoesNotRequireYtDlp() =
        kotlinx.coroutines.runBlocking {
            val channelId = "UC1234567890123456789012"
            val repository =
                DesktopYouTubeRepository(
                    resolver = null,
                    channelFeedFetcher = {
                        """
                        <feed xmlns="http://www.w3.org/2005/Atom"
                              xmlns:yt="http://www.youtube.com/xml/schemas/2015"
                              xmlns:media="http://search.yahoo.com/mrss/">
                          <entry>
                            <yt:videoId>feedonly001</yt:videoId>
                            <title>Feed-only video</title>
                            <published>2026-10-03T09:20:23Z</published>
                            <author><name>Feed-only Channel</name></author>
                          </entry>
                        </feed>
                        """.trimIndent()
                    },
                )

            val result = repository.channelVideos(channelId, limit = 8)

            assertEquals(listOf("feedonly001"), result.map { it.id })
        }

    @Test
    fun directVideoUrlSearchUsesBuiltInMediaResolverWithoutYtDlp() =
        kotlinx.coroutines.runBlocking {
            val expected =
                Video(
                    id = "direct123",
                    title = "Direct video",
                    channelName = "Flow",
                    channelId = "UCdirect",
                    thumbnailUrl = "",
                    duration = 10,
                    viewCount = 1,
                    uploadDate = "today",
                )
            val repository =
                DesktopYouTubeRepository(
                    resolver = null,
                    mediaResolver =
                        DesktopYouTubeMediaResolver {
                            DesktopResolvedMedia(
                                title = expected.title,
                                playbackUrl = "https://media.example/video.m3u8",
                                video = expected,
                            )
                        },
                )

            val result = repository.search("https://www.youtube.com/watch?v=direct123")

            assertEquals(listOf(expected), result)
        }

    @Test
    fun shortCandidatesRejectLongFormResultsInsteadOfRelabelingThem() {
        val short = testVideo("short", duration = 60)
        val threeMinutes = testVideo("three-minutes", duration = 180)
        val long = testVideo("long", duration = 181)
        val unknown = testVideo("unknown", duration = 0)

        val result = shortCandidates(listOf(short, long, unknown, threeMinutes))

        assertEquals(listOf("short", "three-minutes"), result.map(Video::id))
        assertTrue(result.all(Video::isShort))
    }

    private fun testVideo(
        id: String,
        duration: Int,
    ) = Video(
        id = id,
        title = id,
        channelName = "Flow",
        channelId = "UCflow",
        thumbnailUrl = "",
        duration = duration,
        viewCount = 0,
        uploadDate = "",
    )
}
