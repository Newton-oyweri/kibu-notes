package com.kibu.notes.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AcademicUnit(
    val id: String = "",
    val code: String = "",
    val title: String = "",
    @SerialName("created_at")
    val createdAt: String? = null
)
