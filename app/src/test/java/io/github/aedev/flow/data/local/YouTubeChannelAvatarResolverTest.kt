package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YouTubeChannelAvatarResolverTest {
    @Test
    fun buildsCanonicalAndHandleUrls() {
        assertThat(buildYouTubeChannelUrl("UC1234567890123456789012"))
            .isEqualTo("https://www.youtube.com/channel/UC1234567890123456789012")
        assertThat(buildYouTubeChannelUrl("creator"))
            .isEqualTo("https://www.youtube.com/@creator")
        assertThat(buildYouTubeChannelUrl("@creator"))
            .isEqualTo("https://www.youtube.com/@creator")
    }

    @Test
    fun rejectsBlankReferences() {
        assertThat(buildYouTubeChannelUrl("   ")).isNull()
    }
}
