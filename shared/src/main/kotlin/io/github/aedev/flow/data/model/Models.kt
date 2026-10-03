package io.github.aedev.flow.data.model

data class VideoCollaborator(
    val name: String,
    val channelId: String = "",
    val thumbnailUrl: String = "",
    val subscriberCountText: String = "",
)

data class Video(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val duration: Int,
    val viewCount: Long,
    val likeCount: Long = 0,
    val uploadDate: String,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String = "",
    val channelThumbnailUrl: String = "",
    val tags: List<String> = emptyList(),
    val isMusic: Boolean = false,
    val isLive: Boolean = false,
    val isShort: Boolean = false,
    val isUpcoming: Boolean = false,
    val isScheduledLive: Boolean = false,
    val membersOnlyText: String? = null,
    val commentCountText: String = "",
    val channelThumbnailUrls: List<String> = emptyList(),
    val collaborators: List<VideoCollaborator> = emptyList(),
    val isVerifiedChannel: Boolean = false,
    val badges: List<String> = emptyList(),
    val snippet: String = "",
    val snippetHighlights: List<IntRange> = emptyList(),
    val addedAtInPlaylist: Long? = null,
)

data class Channel(
    val id: String,
    val name: String,
    val thumbnailUrl: String,
    val subscriberCount: Long,
    val description: String = "",
    val isSubscribed: Boolean = false,
    val isMusic: Boolean = false,
    val handle: String = "",
    val videoCount: Int = 0,
    val isVerified: Boolean = false,
    val url: String = "",
)

data class Playlist(
    val id: String,
    val name: String,
    val thumbnailUrl: String,
    val videoCount: Int,
    val description: String = "",
    val videos: List<Video> = emptyList(),
    val isLocal: Boolean = true,
)

enum class SearchFilter {
    ALL,
    VIDEOS,
    CHANNELS,
    PLAYLISTS,
}
