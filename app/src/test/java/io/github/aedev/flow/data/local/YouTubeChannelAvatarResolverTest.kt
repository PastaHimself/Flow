package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YouTubeChannelAvatarResolverTest {
    @Test
    fun `builds channel url for canonical id and handle`() {
        assertThat(buildYouTubeChannelUrl("UC1234567890123456789012"))
            .isEqualTo("https://www.youtube.com/channel/UC1234567890123456789012")
        assertThat(buildYouTubeChannelUrl("creator"))
            .isEqualTo("https://www.youtube.com/@creator")
        assertThat(buildYouTubeChannelUrl("@creator"))
            .isEqualTo("https://www.youtube.com/@creator")
    }

    @Test
    fun `rejects blank channel reference`() {
        assertThat(buildYouTubeChannelUrl("   ")).isNull()
    }
}