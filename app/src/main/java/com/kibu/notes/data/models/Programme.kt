package com.kibu.notes.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Programme(
    val id: String = "",
    @SerialName("school_id")
    val schoolId: String = "",
    val title: String = "",
    val code: String = "",
    @SerialName("duration_years")
    val durationYears: Int = 4,
    @SerialName("created_at")
    val createdAt: String? = null
)
