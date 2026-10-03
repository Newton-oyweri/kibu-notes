package com.kibu.notes.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class School(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    @SerialName("created_at")
    val createdAt: String? = null
)
