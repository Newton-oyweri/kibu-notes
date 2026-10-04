package com.kibu.notes

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kibu.notes.data.SupabaseRepository
import com.kibu.notes.data.models.AcademicUnit
import com.kibu.notes.data.models.Programme
import com.kibu.notes.data.models.ProgrammeUnit
import com.kibu.notes.data.models.Resource
import com.kibu.notes.data.models.School
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MyUnitsTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Gander
    )

    private class FakeRepository : SupabaseRepository() {
        val schoolsList = listOf(
            School(id = "s1", name = "School of Computing", code = "SCI")
        )
        val programmesList = listOf(
            Programme(id = "p1", schoolId = "s1", title = "Computer Science", code = "BSc CS")
        )
        val unitsList = listOf(
            AcademicUnit(id = "u1", code = "CSC 110", title = "Programming I"),
            AcademicUnit(id = "u2", code = "CSC 120", title = "Data Structures"),
            AcademicUnit(id = "u3", code = "CSC 210", title = "Databases")
        )
        val programmeUnitsList = listOf(
            ProgrammeUnit(id = "pu1", programmeId = "p1", unitId = "u1", academicYear = 1, semester = 1),
            ProgrammeUnit(id = "pu2", programmeId = "p1", unitId = "u2", academicYear = 1, semester = 2),
            ProgrammeUnit(id = "pu3", programmeId = "p1", unitId = "u3", academicYear = 2, semester = 1)
        )
        val resourcesList = listOf(
            Resource(
                id = "r1",
                unitId = "u1",
                title = "CSC 110 Lecture Notes",
                fileName = "csc110_notes.pdf",
                filePath = "resources/csc110_notes.pdf",
                resourceType = "topic_note",
                fileSize = 1024L
            ),
            Resource(
                id = "r2",
                unitId = "u2",
                title = "CSC 120 Exam 2023",
                fileName = "csc120_2023.pdf",
                filePath = "resources/csc120_2023.pdf",
                resourceType = "past_paper",
                fileSize = 2048L
            )
        )

        override suspend fun getSchools(context: Context?) = schoolsList
        override suspend fun getProgrammes(context: Context?) = programmesList
        override suspend fun getUnits(context: Context?) = unitsList
        override suspend fun getProgrammeUnits(programmeId: String, context: Context?) =
            programmeUnitsList.filter { it.programmeId == programmeId }
        override suspend fun getResourcesByUnit(unitId: String, context: Context?) =
            resourcesList.filter { it.unitId == unitId }
        override suspend fun getAllResources(context: Context?) = resourcesList

        override suspend fun downloadResource(context: Context, resource: Resource): File {
            val file = getLocalResourceFile(context, resource)
            file.writeText("Dummy PDF data for testing")
            return file
        }
    }

    private lateinit var repository: FakeRepository

    @Before
    fun setUp() {
        repository = FakeRepository()
    }

    @Test
    fun programmeUnitsAreGroupedByYearAndSemester() {
        runBlocking {
            val programme = repository.getProgrammes().first { it.title == "Computer Science" }
            val programmeUnits = repository.getProgrammeUnits(programme.id)
            val allUnitsMap = repository.getUnits().associateBy { it.id }
            val allResourcesMap = repository.getAllResources().groupBy { it.unitId }

            val grouped = programmeUnits.groupBy { Pair(it.academicYear, it.semester) }
                .toSortedMap(compareBy({ it.first }, { it.second }))

            assertThat(grouped.keys.toList()).containsExactly(
                Pair(1, 1),
                Pair(1, 2),
                Pair(2, 1)
            ).inOrder()

            val year1Sem1Units = grouped[Pair(1, 1)]!!.map { allUnitsMap[it.unitId]!! }
            assertThat(year1Sem1Units.map { it.code }).containsExactly("CSC 110")

            val year1Sem1Resources = allResourcesMap[year1Sem1Units.first().id].orEmpty()
            assertThat(year1Sem1Resources.map { it.title }).containsExactly("CSC 110 Lecture Notes")
        }
    }

    @Test
    fun resourceDownloadFlowUpdatesDownloadedState() {
        runBlocking {
            val resource = repository.getAllResources().first()

            assertThat(repository.isResourceDownloaded(context, resource)).isFalse()

            val downloadedFile = repository.downloadResource(context, resource)

            assertThat(downloadedFile.exists()).isTrue()
            assertThat(repository.isResourceDownloaded(context, resource)).isTrue()

            downloadedFile.delete()
            assertThat(repository.isResourceDownloaded(context, resource)).isFalse()
        }
    }

    @Test
    fun getResourcesByUnitReturnsMatchingUnitResources() {
        runBlocking {
            val resourcesForU1 = repository.getResourcesByUnit("u1", context)
            assertThat(resourcesForU1).hasSize(1)
            assertThat(resourcesForU1.first().title).isEqualTo("CSC 110 Lecture Notes")

            val resourcesForU2 = repository.getResourcesByUnit("u2", context)
            assertThat(resourcesForU2).hasSize(1)
            assertThat(resourcesForU2.first().title).isEqualTo("CSC 120 Exam 2023")

            val resourcesForU3 = repository.getResourcesByUnit("u3", context)
            assertThat(resourcesForU3).isEmpty()
        }
    }

    @Test
    fun resourceTabFilteringTest() {
        val resources = listOf(
            Resource(id = "1", title = "Topic 1", category = "topics", resourceType = "topic_note"),
            Resource(id = "2", title = "Exam 2022", category = "past_papers", resourceType = "past_paper", examYear = 2022),
            Resource(id = "3", title = "General Doc", category = "shared", resourceType = "shared")
        )

        val topicsFiltered = resources.filter {
            it.category.equals("topics", ignoreCase = true) ||
                    it.resourceType.equals("topic_note", ignoreCase = true) ||
                    it.resourceType.equals("summary", ignoreCase = true) ||
                    it.resourceType.equals("syllabus", ignoreCase = true) ||
                    it.topicId != null
        }
        assertThat(topicsFiltered).hasSize(1)
        assertThat(topicsFiltered.first().title).isEqualTo("Topic 1")

        val pastPapersFiltered = resources.filter {
            it.category.equals("past_papers", ignoreCase = true) ||
                    it.resourceType.equals("past_paper", ignoreCase = true) ||
                    (it.examYear != null && it.examYear > 0)
        }
        assertThat(pastPapersFiltered).hasSize(1)
        assertThat(pastPapersFiltered.first().title).isEqualTo("Exam 2022")

        val sharedFiltered = resources.filter {
            it.category.equals("shared", ignoreCase = true) ||
                    (!it.category.equals("topics", ignoreCase = true) &&
                            !it.category.equals("past_papers", ignoreCase = true) &&
                            !it.resourceType.equals("topic_note", ignoreCase = true) &&
                            !it.resourceType.equals("past_paper", ignoreCase = true))
        }
        assertThat(sharedFiltered).hasSize(1)
        assertThat(sharedFiltered.first().title).isEqualTo("General Doc")
    }
}
