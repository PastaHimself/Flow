package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class OpmlSubscriptionParserTest {
    @Test
    fun `parses YouTube feed outlines and decodes names`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="1.0">
              <body>
                <outline
                    text="Tom &amp; Jerry"
                    title="Tom &amp; Jerry"
                    type="rss"
                    xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=UC1234567890123456789012"
                    htmlUrl="https://www.youtube.com/channel/UC1234567890123456789012" />
              </body>
            </opml>
            """.trimIndent()

        assertThat(OpmlSubscriptionParser.parse(xml))
            .containsExactly(
                OpmlSubscriptionEntry(
                    channelId = "UC1234567890123456789012",
                    channelName = "Tom & Jerry",
                ),
            )
    }

    @Test
    fun `deduplicates channel ids and supports single quoted attributes`() {
        val xml =
            """
            <opml>
              <body>
                <outline text='First name' xmlUrl='https://www.youtube.com/feeds/videos.xml?channel_id=UCabcdefghijklmnopqrstuv' />
                <outline text='Duplicate name' htmlUrl='https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv' />
              </body>
            </opml>
            """.trimIndent()

        assertThat(OpmlSubscriptionParser.parse(xml))
            .containsExactly(
                OpmlSubscriptionEntry(
                    channelId = "UCabcdefghijklmnopqrstuv",
                    channelName = "First name",
                ),
            )
    }

    @Test
    fun `accepts an explicit canonical channel id attribute`() {
        val xml =
            """
            <opml>
              <body>
                <outline text="Direct channel" channelId="UC1234567890123456789012" />
              </body>
            </opml>
            """.trimIndent()

        assertThat(OpmlSubscriptionParser.parse(xml))
            .containsExactly(
                OpmlSubscriptionEntry(
                    channelId = "UC1234567890123456789012",
                    channelName = "Direct channel",
                ),
            )
    }

    @Test
    fun `ignores outlines without a canonical YouTube channel id`() {
        val xml =
            """
            <opml>
              <body>
                <outline text="Folder">
                  <outline text="Generic RSS" xmlUrl="https://example.com/feed.xml" />
                </outline>
              </body>
            </opml>
            """.trimIndent()

        assertThat(OpmlSubscriptionParser.parse(xml)).isEmpty()
    }

    @Test
    fun `does not scan labels non YouTube URLs or comments for channel ids`() {
        val xml =
            """
            <opml>
              <body>
                <outline
                    text="UC1234567890123456789012"
                    xmlUrl="https://example.com/feed.xml?channel_id=UC1234567890123456789012" />
                <!-- <outline text="Commented" xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=UCabcdefghijklmnopqrstuv" /> -->
              </body>
            </opml>
            """.trimIndent()

        assertThat(OpmlSubscriptionParser.parse(xml)).isEmpty()
    }

    @Test
    fun `skips malformed feed query and keeps valid outlines`() {
        val xml =
            """
            <opml>
              <body>
                <outline text="Malformed" xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=%ZZ" />
                <outline text="Valid" xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=UC1234567890123456789012" />
              </body>
            </opml>
            """.trimIndent()

        assertThat(OpmlSubscriptionParser.parse(xml))
            .containsExactly(
                OpmlSubscriptionEntry(
                    channelId = "UC1234567890123456789012",
                    channelName = "Valid",
                ),
            )
    }

    @Test
    fun `rejects non xml input`() {
        assertThat(OpmlSubscriptionParser.parse("channel,name\nUC123,Example")).isEmpty()
    }

    @Test
    fun `builds subscriptions only for channel ids that are not already followed`() {
        val existingId = "UC1234567890123456789012"
        val newId = "UCabcdefghijklmnopqrstuv"
        val subscriptions =
            buildMissingOpmlSubscriptions(
                entries =
                    listOf(
                        OpmlSubscriptionEntry(existingId, "Existing channel"),
                        OpmlSubscriptionEntry(newId, "New channel"),
                    ),
                existingIds = setOf(existingId),
                subscribedAt = 1_000L,
            )

        assertThat(subscriptions)
            .containsExactly(
                ChannelSubscription(
                    channelId = newId,
                    channelName = "New channel",
                    channelThumbnail = "",
                    subscribedAt = 1_000L,
                ),
            )
    }

    @Test
    fun `enriches new subscriptions with fetched channel avatars`() =
        runBlocking {
            val channelId = "UCabcdefghijklmnopqrstuv"
            val progress = mutableListOf<Pair<Int, Int>>()
            val subscriptions =
                enrichOpmlSubscriptionAvatars(
                    subscriptions =
                        listOf(
                            ChannelSubscription(
                                channelId = channelId,
                                channelName = "New channel",
                                channelThumbnail = "",
                                subscribedAt = 1_000L,
                            ),
                        ),
                    avatarFetcher = { "https://yt3.ggpht.com/avatar-$it" },
                    onProgress = { current, total -> progress += current to total },
                )

            assertThat(subscriptions.single().channelThumbnail)
                .isEqualTo("https://yt3.ggpht.com/avatar-$channelId")
            assertThat(progress).containsExactly(0 to 1, 1 to 1).inOrder()
        }
}