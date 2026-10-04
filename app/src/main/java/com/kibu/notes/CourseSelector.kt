package com.kibu.notes

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kibu.notes.data.SupabaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object CourseSelector {

    data class CourseGroup(val school: String, val courses: List<String>)

    fun showDialog(
        context: Context,
        repository: SupabaseRepository = SupabaseRepository(),
        onSelected: (String) -> Unit
    ) {
        val rootLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(48, 24, 48, 32)
        }

        val loadingLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 48)
            addView(ProgressBar(context).apply {
                isIndeterminate = true
            })
            addView(TextView(context).apply {
                text = context.getString(R.string.loading_programs)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, 24, 0, 0)
            })
        }
        rootLayout.addView(loadingLayout)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.my_course_program)
            .setView(rootLayout)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        fun loadData() {
            rootLayout.removeAllViews()
            rootLayout.addView(loadingLayout)

            CoroutineScope(Dispatchers.Main).launch {
                val groups = runCatching {
                    withContext(Dispatchers.IO) {
                        val schools = repository.getSchools(context)
                        val programmes = repository.getProgrammes(context)
                        schools.map { school ->
                            CourseGroup(
                                school = school.name,
                                courses = programmes.filter { it.schoolId == school.id }.map { it.title }
                            )
                        }.filter { it.courses.isNotEmpty() }
                    }
                }.getOrNull()

                rootLayout.removeAllViews()
                if (!groups.isNullOrEmpty()) {
                    val scrollView = ScrollView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        isFillViewport = true
                    }
                    val container = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                    }
                    scrollView.addView(container)

                    populateContainer(context, container, groups) { course ->
                        dialog.dismiss()
                        onSelected(course)
                    }
                    rootLayout.addView(scrollView)
                } else {
                    val errorLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(0, 32, 0, 32)
                        addView(TextView(context).apply {
                            text = context.getString(R.string.failed_to_load_programs)
                            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorError))
                        })
                        addView(MaterialButton(context).apply {
                            text = context.getString(R.string.retry)
                            setOnClickListener { loadData() }
                        })
                    }
                    rootLayout.addView(errorLayout)
                }
            }
        }

        dialog.show()
        loadData()
    }

    private fun populateContainer(
        context: Context,
        container: ViewGroup,
        groups: List<CourseGroup>,
        onSelected: (String) -> Unit
    ) {
        container.removeAllViews()
        val selectedCourse = Settings.getSelectedCourse(context)
        groups.forEach { group ->
            val header = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = context.resources.getDimensionPixelSize(R.dimen.course_header_top_margin)
                    bottomMargin = context.resources.getDimensionPixelSize(R.dimen.course_header_bottom_margin)
                }
                text = group.school
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
                setTypeface(null, Typeface.BOLD)
            }
            container.addView(header)

            group.courses.forEach { course ->
                val isSelected = (selectedCourse == course)
                val button = MaterialButton(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = context.resources.getDimensionPixelSize(R.dimen.course_button_bottom_margin)
                    }
                    text = course
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    if (isSelected) {
                        setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
                        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary))
                        strokeWidth = 0
                    } else {
                        setBackgroundColor(Color.TRANSPARENT)
                        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
                        strokeColor = ColorStateList.valueOf(
                            MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary)
                        )
                        strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
                    }
                    setOnClickListener {
                        Settings.setSelectedCourse(context, course)
                        Toast.makeText(context, "Selected: $course", Toast.LENGTH_SHORT).show()
                        onSelected(course)
                    }
                }
                container.addView(button)
            }
        }
    }
}
