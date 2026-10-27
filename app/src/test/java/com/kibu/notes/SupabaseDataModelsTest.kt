package com.kibu.notes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kibu.notes.data.SupabaseRepository
import com.kibu.notes.data.models.AcademicUnit
import com.kibu.notes.data.models.Programme
import com.kibu.notes.data.models.ProgrammeUnit
import com.kibu.notes.data.models.Resource
import com.kibu.notes.data.models.School
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SupabaseDataModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Test data is generated, nothing is hardcoded.
    private fun uuid() = UUID.randomUUID().toString()

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    @Test
    fun schoolDeserialization() {
        val id = uuid()
        val name = "school_${uuid()}"
        val code = "code_${uuid().take(6)}"
        val createdAt = now()

        val jsonText = """
            {
                "id": "$id",
                "name": "$name",
                "code": "$code",
                "created_at": "$createdAt"
            }
        """.trimIndent()

        val school = json.decodeFromString<School>(jsonText)
        assertThat(school.id).isEqualTo(id)
        assertThat(school.name).isEqualTo(name)
        assertThat(school.code).isEqualTo(code)
        assertThat(school.createdAt).isEqualTo(createdAt)
    }

    @Test
    fun programmeDeserialization() {
        val id = uuid()
        val schoolId = uuid()
        val title = "programme_${uuid()}"
        val code = "code_${uuid().take(6)}"
        val durationYears = (1..6).random()

        val jsonText = """
            {
                "id": "$id",
                "school_id": "$schoolId",
                "title": "$title",
                "code": "$code",
                "duration_years": $durationYears,
                "created_at": "${now()}"
            }
        """.trimIndent()

        val programme = json.decodeFromString<Programme>(jsonText)
        assertThat(programme.id).isEqualTo(id)
        assertThat(programme.schoolId).isEqualTo(schoolId)
        assertThat(programme.title).isEqualTo(title)
        assertThat(programme.code).isEqualTo(code)
        assertThat(programme.durationYears).isEqualTo(durationYears)
    }

    @Test
    fun academicUnitDeserialization() {
        val id = uuid()
        val code = "code_${uuid().take(6)}"
        val title = "unit_${uuid()}"

        val jsonText = """
            {
                "id": "$id",
                "code": "$code",
                "title": "$title",
                "created_at": "${now()}"
            }
        """.trimIndent()

        val unit = json.decodeFromString<AcademicUnit>(jsonText)
        assertThat(unit.id).isEqualTo(id)
        assertThat(unit.code).isEqualTo(code)
        assertThat(unit.title).isEqualTo(title)
    }

    @Test
    fun programmeUnitDeserialization() {
        val id = uuid()
        val programmeId = uuid()
        val unitId = uuid()
        val academicYear = (1..6).random()
        val semester = (1..3).random()

        val jsonText = """
            {
                "id": "$id",
                "programme_id": "$programmeId",
                "unit_id": "$unitId",
                "academic_year": $academicYear,
                "semester": $semester
            }
        """.trimIndent()

        val pu = json.decodeFromString<ProgrammeUnit>(jsonText)
        assertThat(pu.id).isEqualTo(id)
        assertThat(pu.programmeId).isEqualTo(programmeId)
        assertThat(pu.unitId).isEqualTo(unitId)
        assertThat(pu.academicYear).isEqualTo(academicYear)
        assertThat(pu.semester).isEqualTo(semester)
    }

    /**
     * JSON here has EXACTLY the columns of public.resources
     * (no resource_type, no status, no description, no downloads_count).
     * If the Resource model still requires a dropped column, this test fails.
     */
    @Test
    fun resourceDeserialization() {
        // resource_category enum values from the schema
        val categories = listOf("topics", "past_papers", "cats")

        for (category in categories) {
            val id = uuid()
            val unitId = uuid()
            val title = "title_${uuid()}"
            val fileName = "${uuid()}.pdf"
            val filePath = "uploads/${unitId}_${id}_$fileName"
            val fileSize = (1_000L..5_000_000L).random()
            val createdAt = now()

            val jsonText = """
                {
                    "id": "$id",
                    "unit_id": "$unitId",
                    "uploaded_by": null,
                    "title": "$title",
                    "file_name": "$fileName",
                    "file_path": "$filePath",
                    "file_size": $fileSize,
                    "mime_type": "application/pdf",
                    "visibility": "public",
                    "created_at": "$createdAt",
                    "updated_at": "$createdAt",
                    "category": "$category"
                }
            """.trimIndent()

            val resource = json.decodeFromString<Resource>(jsonText)
            assertThat(resource.id).isEqualTo(id)
            assertThat(resource.unitId).isEqualTo(unitId)
            assertThat(resource.title).isEqualTo(title)
            assertThat(resource.category).isEqualTo(category)
            assertThat(resource.fileName).isEqualTo(fileName)
            assertThat(resource.filePath).isEqualTo(filePath)
            assertThat(resource.fileSize).isEqualTo(fileSize)
            assertThat(resource.mimeType).isEqualTo("application/pdf")
            assertThat(resource.visibility).isEqualTo("public")
            assertThat(resource.createdAt).isEqualTo(createdAt)
        }
    }

    @Test
    fun localResourceFileAndDownloadStatus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SupabaseRepository()
        val id = uuid()
        val fileName = "${uuid()}.pdf"
        val resource = Resource(id = id, fileName = fileName)

        val localFile = repository.getLocalResourceFile(context, resource)
        try {
            assertThat(localFile.name).isEqualTo("${id}_$fileName")
            assertThat(repository.isResourceDownloaded(context, resource)).isFalse()

            localFile.writeText(uuid())
            assertThat(repository.isResourceDownloaded(context, resource)).isTrue()
        } finally {
            localFile.delete()
        }
        assertThat(repository.isResourceDownloaded(context, resource)).isFalse()
    }
}