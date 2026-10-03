package com.kibu.notes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kibu.notes.data.models.AcademicUnit
import com.kibu.notes.data.models.Programme
import com.kibu.notes.data.models.ProgrammeUnit
import com.kibu.notes.data.models.Resource
import com.kibu.notes.data.models.School
import com.google.common.truth.Truth.assertThat
import com.kibu.notes.data.SupabaseRepository
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupabaseDataModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun schoolDeserialization() {
        val jsonText = """
            {
                "id": "e4a282f1-63bf-4d89-a2e6-7b47e5b602df",
                "name": "School of Computing and Informatics",
                "code": "SCI",
                "created_at": "2026-03-01T10:00:00Z"
            }
        """.trimIndent()

        val school = json.decodeFromString<School>(jsonText)
        assertThat(school.id).isEqualTo("e4a282f1-63bf-4d89-a2e6-7b47e5b602df")
        assertThat(school.name).isEqualTo("School of Computing and Informatics")
        assertThat(school.code).isEqualTo("SCI")
        assertThat(school.createdAt).isEqualTo("2026-03-01T10:00:00Z")
    }

    @Test
    fun programmeDeserialization() {
        val jsonText = """
            {
                "id": "11111111-2222-3333-4444-555555555555",
                "school_id": "e4a282f1-63bf-4d89-a2e6-7b47e5b602df",
                "title": "Bachelor of Science in Computer Science",
                "code": "BSc CS",
                "duration_years": 4,
                "created_at": "2026-03-01T10:00:00Z"
            }
        """.trimIndent()

        val programme = json.decodeFromString<Programme>(jsonText)
        assertThat(programme.id).isEqualTo("11111111-2222-3333-4444-555555555555")
        assertThat(programme.schoolId).isEqualTo("e4a282f1-63bf-4d89-a2e6-7b47e5b602df")
        assertThat(programme.title).isEqualTo("Bachelor of Science in Computer Science")
        assertThat(programme.code).isEqualTo("BSc CS")
        assertThat(programme.durationYears).isEqualTo(4)
    }

    @Test
    fun academicUnitDeserialization() {
        val jsonText = """
            {
                "id": "unit-123",
                "code": "CSC 110",
                "title": "Fundamentals of Computing",
                "created_at": "2026-03-01T10:00:00Z"
            }
        """.trimIndent()

        val unit = json.decodeFromString<AcademicUnit>(jsonText)
        assertThat(unit.id).isEqualTo("unit-123")
        assertThat(unit.code).isEqualTo("CSC 110")
        assertThat(unit.title).isEqualTo("Fundamentals of Computing")
    }

    @Test
    fun programmeUnitDeserialization() {
        val jsonText = """
            {
                "id": "pu-123",
                "programme_id": "prog-1",
                "unit_id": "unit-123",
                "academic_year": 1,
                "semester": 2
            }
        """.trimIndent()

        val pu = json.decodeFromString<ProgrammeUnit>(jsonText)
        assertThat(pu.id).isEqualTo("pu-123")
        assertThat(pu.programmeId).isEqualTo("prog-1")
        assertThat(pu.unitId).isEqualTo("unit-123")
        assertThat(pu.academicYear).isEqualTo(1)
        assertThat(pu.semester).isEqualTo(2)
    }

    @Test
    fun resourceDeserialization() {
        val jsonText = """
            {
                "id": "res-999",
                "unit_id": "unit-123",
                "topic_id": null,
                "uploaded_by": null,
                "title": "CSC 110 Past Paper 2023",
                "description": "End of semester exam",
                "resource_type": "past_paper",
                "file_name": "csc110_2023.pdf",
                "file_path": "resources/csc110_2023.pdf",
                "file_size": 204800,
                "mime_type": "application/pdf",
                "exam_year": 2023,
                "academic_year_offered": "2022/2023",
                "status": "approved",
                "visibility": "public",
                "downloads_count": 42,
                "created_at": "2026-03-01T10:00:00Z",
                "updated_at": "2026-03-01T10:00:00Z"
            }
        """.trimIndent()

        val resource = json.decodeFromString<Resource>(jsonText)
        assertThat(resource.id).isEqualTo("res-999")
        assertThat(resource.unitId).isEqualTo("unit-123")
        assertThat(resource.title).isEqualTo("CSC 110 Past Paper 2023")
        assertThat(resource.fileName).isEqualTo("csc110_2023.pdf")
        assertThat(resource.fileSize).isEqualTo(204800L)
        assertThat(resource.mimeType).isEqualTo("application/pdf")
        assertThat(resource.downloadsCount).isEqualTo(42)
    }

    @Test
    fun localResourceFileAndDownloadStatus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = SupabaseRepository()
        val resource = Resource(id = "res-test", fileName = "test_note.pdf")

        val localFile = repository.getLocalResourceFile(context, resource)
        assertThat(localFile.name).isEqualTo("res-test_test_note.pdf")
        assertThat(repository.isResourceDownloaded(context, resource)).isFalse()

        localFile.writeText("sample pdf content")
        assertThat(repository.isResourceDownloaded(context, resource)).isTrue()

        localFile.delete()
        assertThat(repository.isResourceDownloaded(context, resource)).isFalse()
    }
}
