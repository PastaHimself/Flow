package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
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
    fun `rejects non xml input`() {
        assertThat(OpmlSubscriptionParser.parse("channel,name\nUC123,Example")).isEmpty()
    }
}
