package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OpmlSubscriptionParserTest {
    @Test
    fun parsesFeedOutlinesAndDecodesNames() {
        val xml = """<opml><body><outline text="Tom &amp; Jerry"
            xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=UC1234567890123456789012" /></body></opml>"""
        assertThat(OpmlSubscriptionParser.parse(xml)).containsExactly(
            OpmlSubscriptionEntry("UC1234567890123456789012", "Tom & Jerry"),
        )
    }

    @Test
    fun deduplicatesIdsAndSupportsSingleQuotedAttributes() {
        val xml = """<opml><body>
            <outline text='First' xmlUrl='https://www.youtube.com/feeds/videos.xml?channel_id=UCabcdefghijklmnopqrstuv' />
            <outline text='Duplicate' htmlUrl='https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv' />
            </body></opml>"""
        assertThat(OpmlSubscriptionParser.parse(xml)).containsExactly(
            OpmlSubscriptionEntry("UCabcdefghijklmnopqrstuv", "First"),
        )
    }

    @Test
    fun acceptsExplicitIdsAndIgnoresNonYouTubeUrls() {
        val xml = """<opml><body>
            <outline text="Direct" channelId="UC1234567890123456789012" />
            <outline text="Other" xmlUrl="https://example.com/feed.xml?channel_id=UCabcdefghijklmnopqrstuv" />
            </body></opml>"""
        assertThat(OpmlSubscriptionParser.parse(xml)).containsExactly(
            OpmlSubscriptionEntry("UC1234567890123456789012", "Direct"),
        )
    }

    @Test
    fun rejectsInvalidInputAndMalformedQueries() {
        assertThat(OpmlSubscriptionParser.parse("channel,name
UC123,Example")).isEmpty()
        val xml = """<opml><body>
            <outline text="Malformed" xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=%ZZ" />
            <outline text="Valid" xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=UC1234567890123456789012" />
            </body></opml>"""
        assertThat(OpmlSubscriptionParser.parse(xml)).containsExactly(
            OpmlSubscriptionEntry("UC1234567890123456789012", "Valid"),
        )
    }

    @Test
    fun buildsOnlyMissingSubscriptions() {
        val existingId = "UC1234567890123456789012"
        val actual = buildMissingOpmlSubscriptions(
            entries = listOf(
                OpmlSubscriptionEntry(existingId, "Existing"),
                OpmlSubscriptionEntry("UCabcdefghijklmnopqrstuv", "New"),
            ),
            existingIds = setOf(existingId),
            subscribedAt = 1_000L,
        )
        assertThat(actual).containsExactly(
            ChannelSubscription("UCabcdefghijklmnopqrstuv", "New", "", 1_000L),
        )
    }
}
