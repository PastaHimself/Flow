package io.github.aedev.flow.data.recommendation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeuroTextTest {
    @Test
    fun foldsStyledTextToPlainLowercase() {
        assertEquals("phonk", NeuroText.fold("𝙋𝙃𝙊𝙉𝙆"))
        assertEquals("phonk", NeuroText.fold("ᴘʜᴏɴᴋ"))
    }

    @Test
    fun preservesKnownShortTopics() {
        assertTrue(NeuroText.isTopicSized("ai"))
        assertFalse(NeuroText.isTopicSized("of"))
    }
}
