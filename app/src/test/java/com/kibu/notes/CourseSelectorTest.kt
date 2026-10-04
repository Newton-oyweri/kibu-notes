package com.kibu.notes

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kibu.notes.data.SupabaseRepository
import com.kibu.notes.data.models.Programme
import com.kibu.notes.data.models.School
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
class CourseSelectorTest {

    private val context: Context = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext(),
        R.style.Theme_Gander
    )

    private class FakeSupabaseRepository : SupabaseRepository() {
        var throwError = false

        override suspend fun getSchools(context: Context?): List<School> {
            if (throwError) throw RuntimeException("Network error")
            return listOf(
                School(id = "s1", name = "School of Computing", code = "SCI"),
                School(id = "s2", name = "School of Education", code = "SOE")
            )
        }

        override suspend fun getProgrammes(context: Context?): List<Programme> {
            if (throwError) throw RuntimeException("Network error")
            return listOf(
                Programme(id = "p1", schoolId = "s1", title = "Computer Science", code = "CS"),
                Programme(id = "p2", schoolId = "s1", title = "Information Technology", code = "IT"),
                Programme(id = "p3", schoolId = "s2", title = "Bachelor of Education (Arts)", code = "BED")
            )
        }
    }

    @Test
    fun showDialogLoadsDataFromSupabaseRepository() {
        val fakeRepo = FakeSupabaseRepository()
        var selected: String? = null

        CourseSelector.showDialog(context, fakeRepo) {
            selected = it
        }

        ShadowLooper.idleMainLooper()

        val dialog = ShadowDialog.getLatestDialog()
        assertThat(dialog).isNotNull()
        assertThat(dialog.isShowing).isTrue()
    }
}
