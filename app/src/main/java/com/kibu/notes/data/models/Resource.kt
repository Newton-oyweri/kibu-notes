package com.kibu.notes.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Resource(
    val id: String = "",
    @SerialName("unit_id")
    val unitId: String = "",
    @SerialName("topic_id")
    val topicId: String? = null,
    @SerialName("uploaded_by")
    val uploadedBy: String? = null,
    val title: String = "",
    val description: String? = null,
    @SerialName("resource_type")
    val resourceType: String = "",
    @SerialName("file_name")
    val fileName: String = "",
    @SerialName("file_path")
    val filePath: String = "",
    @SerialName("file_size")
    val fileSize: Long = 0L,
    @SerialName("mime_type")
    val mimeType: String = "application/pdf",
    @SerialName("exam_year")
    val examYear: Int? = null,
    @SerialName("academic_year_offered")
    val academicYearOffered: String? = null,
    val status: String = "pending",
    val visibility: String = "public",
    @SerialName("downloads_count")
    val downloadsCount: Int = 0,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val category: String = "shared"
)
