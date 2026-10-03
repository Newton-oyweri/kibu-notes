package com.kibu.notes.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProgrammeUnit(
    val id: String = "",
    @SerialName("programme_id")
    val programmeId: String = "",
    @SerialName("unit_id")
    val unitId: String = "",
    @SerialName("academic_year")
    val academicYear: Int = 1,
    val semester: Int = 1
)
