package com.kibu.notes.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrendPost(
    val id: String = "",
    @SerialName("author_name")
    val authorName: String = "",
    @SerialName("author_handle")
    val authorHandle: String = "",
    val content: String = "",
    val category: String = "General",
    @SerialName("time_ago")
    val timeAgo: String = "",
    @SerialName("likes_count")
    var likesCount: Int = 0,
    @SerialName("comments_count")
    val commentsCount: Int = 0,
    @SerialName("is_liked")
    var isLiked: Boolean = false
)
