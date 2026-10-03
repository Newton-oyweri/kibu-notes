package com.kibu.notes

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object CourseSelector {

    data class CourseGroup(val school: String, val courses: List<String>)

    val COURSE_GROUPS = listOf(
        CourseGroup(
            "School of Education",
            listOf(
                "Bachelor of Education (Arts)",
                "Bachelor of Education (Science)",
                "Bachelor of Education in Early Childhood Education"
            )
        ),
        CourseGroup(
            "Faculty of Arts and Social Sciences",
            listOf(
                "Bachelor of Social Work",
                "Bachelor of Criminology and Criminal Justice",
                "Bachelor of Journalism and Mass Communication"
            )
        ),
        CourseGroup(
            "Faculty of Science",
            listOf(
                "Bachelor of Science (Mathematics)",
                "Bachelor of Science in Chemistry",
                "Bachelor of Science in Physics",
                "Bachelor of Science in Biology",
                "Bachelor of Science (Agricultural Education and Extension)",
                "Bachelor of Science in Agriculture and Biotechnology",
                "Bachelor of Science in Agriculture Economics and Resource Management",
                "Bachelor of Science (Bio-resource Conservation and Management)"
            )
        ),
        CourseGroup(
            "School of Computing and Informatics",
            listOf(
                "Bachelor of Science in Computer Science",
                "Bachelor of Science in Information Technology"
            )
        ),
        CourseGroup(
            "School of Business and Economics",
            listOf(
                "Bachelor of Commerce",
                "Bachelor of Science in Cooperative and Entrepreneurship Management",
                "Bachelor of Business Management"
            )
        ),
        CourseGroup(
            "School of Nursing",
            listOf(
                "Bachelor of Science in Nursing"
            )
        )
    )

    fun populateContainer(context: Context, container: ViewGroup, onSelected: (String) -> Unit) {
        container.removeAllViews()
        val selectedCourse = Settings.getSelectedCourse(context)
        COURSE_GROUPS.forEach { group ->
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

    fun showDialog(context: Context, onSelected: (String) -> Unit) {
        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isFillViewport = true
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 32)
        }
        scrollView.addView(container)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.my_course_program)
            .setView(scrollView)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        populateContainer(context, container) { course ->
            dialog.dismiss()
            onSelected(course)
        }
        dialog.show()
    }
}
