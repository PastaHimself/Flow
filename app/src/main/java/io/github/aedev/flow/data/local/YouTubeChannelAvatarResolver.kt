package io.github.aedev.flow.data.local

import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo

internal fun buildYouTubeChannelUrl(channelId: String): String? {
    val channelRef = channelId.trim()
    if (channelRef.isEmpty()) return null

    return when {
        channelRef.startsWith("UC") && channelRef.length > 20 -> {
            "https://www.youtube.com/channel/$channelRef"
        }

        channelRef.startsWith("@") -> {
            "https://www.youtube.com/$channelRef"
        }

        else -> {
            "https://www.youtube.com/@$channelRef"
        }
    }
}

internal fun fetchYouTubeChannelAvatar(channelId: String): String =
    try {
        val url = buildYouTubeChannelUrl(channelId) ?: return ""
        val info = ChannelInfo.getInfo(ServiceList.YouTube, url)
        info.avatars.maxByOrNull { it.height }?.url ?: ""
    } catch (e: Exception) {
        ""
    }
