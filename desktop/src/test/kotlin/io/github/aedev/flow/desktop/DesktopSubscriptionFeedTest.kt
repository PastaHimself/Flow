package io.github.aedev.flow.desktop

import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSubscriptionFeedTest {
    @Test
    fun mixedSubscriptionFailuresKeepSuccessfulVideosAndReportFailedChannels() =
        runBlocking {
            val subscriptions =
                listOf(
                    DesktopSubscription("good", "Working channel"),
                    DesktopSubscription("bad", "Unavailable channel"),
                )

            val result =
                loadSubscriptionFeed(subscriptions) { subscription ->
                    if (subscription.channelId == "bad") error("network failure")
                    listOf(video("video-1"))
                }

            assertEquals(listOf("video-1"), result.videos.map(Video::id))
            assertEquals(listOf("Unavailable channel"), result.failedChannelNames)
        }

    @Test(expected = IllegalStateException::class)
    fun allSubscriptionFailuresStillFailTheFeed() {
        runBlocking<Unit> {
            loadSubscriptionFeed(listOf(DesktopSubscription("bad", "Unavailable channel"))) {
                error("network failure")
            }
        }
    }

    @Test
    fun cancellationIsNotConvertedIntoChannelFailure() {
        val failure =
            runCatching {
                runBlocking {
                    loadSubscriptionFeed(listOf(DesktopSubscription("cancelled", "Cancelled channel"))) {
                        throw CancellationException("cancelled")
                    }
                }
            }.exceptionOrNull()

        assertTrue(failure is CancellationException)
    }

    @Test
    fun videosAreDeduplicatedAndSortedNewestFirst() =
        runBlocking {
            val subscriptions =
                listOf(
                    DesktopSubscription("first", "First channel"),
                    DesktopSubscription("second", "Second channel"),
                )

            val result =
                loadSubscriptionFeed(subscriptions) { subscription ->
                    when (subscription.channelId) {
                        "first" -> listOf(video("shared", timestamp = 1L), video("old", timestamp = 2L))
                        else -> listOf(video("shared", timestamp = 1L), video("new", timestamp = 3L))
                    }
                }

            assertEquals(listOf("new", "old", "shared"), result.videos.map(Video::id))
        }

    private fun video(
        id: String,
        timestamp: Long = 1L,
    ) = Video(
        id = id,
        title = "Video",
        channelName = "Channel",
        channelId = "good",
        thumbnailUrl = "",
        duration = 60,
        viewCount = 1,
        uploadDate = "2026-10-03",
        timestamp = timestamp,
    )
}
