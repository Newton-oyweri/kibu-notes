package com.kibu.notes

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/**
 * The reader's own preferences, as opposed to [Recents], which is a record of what
 * they opened.
 *
 * There is one so far. Gander has never had a settings screen and does not want one;
 * night mode is here rather than reset on every open because a mode you turn on to
 * read in bed is not a mode you want to turn on again at the next chapter.
 *
 * Not backed up: `allowBackup="false"` in the manifest covers this file along with the
 * recents list, for the reason given there.
 */
object Settings {

    private const val PREFS = "viewer"
    private const val KEY_NIGHT = "night"
    private const val KEY_APP_THEME_NIGHT = "app_theme_night"
    private const val KEY_SELECTED_COURSE = "selected_course"
    private const val KEY_SELECTED_YEAR = "selected_year"
    private const val KEY_SELECTED_SEMESTER = "selected_semester"

    /**
     * Whether app-wide dark mode is active. Defaults to true (Dark Mode).
     */
    fun isAppDarkTheme(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_APP_THEME_NIGHT, true)

    fun setAppDarkTheme(context: Context, dark: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_APP_THEME_NIGHT, dark) }
    }

    fun applyAppTheme(context: Context) {
        val dark = isAppDarkTheme(context)
        val targetMode = if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }

    /** Whether PDF pages are drawn turned over. See issue #19 and `pdf.html`. */
    fun night(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NIGHT, false)

    fun setNight(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_NIGHT, on) }
    }

    fun getSelectedCourse(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_COURSE, null)

    fun setSelectedCourse(context: Context, course: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_SELECTED_COURSE, course)
                remove(KEY_SELECTED_YEAR)
                remove(KEY_SELECTED_SEMESTER)
            }
    }

    fun getSelectedYear(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_SELECTED_YEAR)) prefs.getInt(KEY_SELECTED_YEAR, 1) else null
    }

    fun getSelectedSemester(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_SELECTED_SEMESTER)) prefs.getInt(KEY_SELECTED_SEMESTER, 1) else null
    }

    fun setSelectedYearAndSemester(context: Context, year: Int?, semester: Int?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit {
                if (year != null) putInt(KEY_SELECTED_YEAR, year) else remove(KEY_SELECTED_YEAR)
                if (semester != null) putInt(KEY_SELECTED_SEMESTER, semester) else remove(KEY_SELECTED_SEMESTER)
            }
    }
}
