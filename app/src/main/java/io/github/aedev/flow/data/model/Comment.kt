package io.github.aedev.flow.data.model

import org.schabi.newpipe.extractor.Page

data class Comment(
    val id: String,
    val author: String,
    val authorThumbnail: String,
    val text: String,
    val likeCount: Int,
    val publishedTime: String,
    val replies: List<Comment> = emptyList(),
    val replyCount: Int = 0,
    val repliesPage: Page? = null,
    val isPinned: Boolean = false,
    val continuationToken: String? = null,
    val authorChannelId: String = "",
    val richText: RichText? = null,
    val likeCountText: String = "",
    val pinnedByText: String? = null,
    val isHearted: Boolean = false,
    val heartedByText: String? = null,
    val isVerified: Boolean = false,
    val isCreator: Boolean = false,
    val isArtist: Boolean = false,
)
