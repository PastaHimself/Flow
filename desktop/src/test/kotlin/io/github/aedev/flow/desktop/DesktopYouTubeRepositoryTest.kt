package io.github.aedev.flow.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopYouTubeRepositoryTest {
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
}
