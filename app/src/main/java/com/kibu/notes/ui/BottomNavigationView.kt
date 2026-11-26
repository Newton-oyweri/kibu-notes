package com.kibu.notes.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.google.android.material.color.MaterialColors
import com.kibu.notes.R

class BottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        const val TAB_STUDY = 0
        const val TAB_TRENDS = 1
    }

    var onStudySelected: (() -> Unit)? = null
    var onFilesSelected: (() -> Unit)? = null
    var onTrendsSelected: (() -> Unit)? = null

    private var currentTab = TAB_STUDY

    private lateinit var studyContainer: LinearLayout
    private lateinit var studyIcon: ImageView
    private lateinit var studyLabel: TextView
    private lateinit var studyIndicator: View

    private lateinit var trendsContainer: LinearLayout
    private lateinit var trendsIcon: ImageView
    private lateinit var trendsLabel: TextView
    private lateinit var trendsIndicator: View

    private lateinit var filesContainer: FrameLayout
    private lateinit var filesIcon: ImageView

    private val density = resources.displayMetrics.density

    init {
        orientation = VERTICAL
        val surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        setBackgroundColor(surfaceColor)
        setupViews()
    }

    private fun dpToPxInt(dp: Float): Int = (dp * density).toInt()

    private fun setupViews() {
        val primaryColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, "#003366".toColorInt())
        val onSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, "#44474F".toColorInt())
        val outlineVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant, "#E0E0E0".toColorInt())

        val rippleOut = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, rippleOut, true)

        // Thin top divider to separate bottom bar from content
        val topDivider = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPxInt(1f))
            setBackgroundColor(outlineVariant)
        }
        addView(topDivider)

        val navRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPxInt(56f))
        }

        // 1. Study Slot (Left)
        studyIndicator = View(context).apply {
            layoutParams = LayoutParams(dpToPxInt(16f), dpToPxInt(3f)).apply {
                bottomMargin = dpToPxInt(2f)
            }
            setBackgroundResource(R.drawable.shape_active_indicator)
            visibility = VISIBLE
        }
        studyIcon = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPxInt(24f), dpToPxInt(24f))
            setImageResource(R.drawable.ic_study)
            setColorFilter(primaryColor)
        }
        studyLabel = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPxInt(2f)
            }
            text = context.getString(R.string.nav_study)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(primaryColor)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            includeFontPadding = false
        }
        studyContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.nav_study)
            setBackgroundResource(rippleOut.resourceId)
            addView(studyIndicator)
            addView(studyIcon)
            addView(studyLabel)
            setOnClickListener {
                if (currentTab != TAB_STUDY) {
                    setSelectedTab(TAB_STUDY)
                    onStudySelected?.invoke()
                }
            }
        }

        // 2. Files Slot (Center - action only, no label, no active tab state)
        filesIcon = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dpToPxInt(24f), dpToPxInt(24f), Gravity.CENTER)
            setImageResource(R.drawable.ic_files)
            setColorFilter(primaryColor)
        }
        filesContainer = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.open_a_file)
            setBackgroundResource(rippleOut.resourceId)
            addView(filesIcon)
            setOnClickListener {
                onFilesSelected?.invoke()
            }
        }

        // 3. Trends Slot (Right)
        trendsIndicator = View(context).apply {
            layoutParams = LayoutParams(dpToPxInt(16f), dpToPxInt(3f)).apply {
                bottomMargin = dpToPxInt(2f)
            }
            setBackgroundResource(R.drawable.shape_active_indicator)
            visibility = INVISIBLE
        }
        trendsIcon = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPxInt(24f), dpToPxInt(24f))
            setImageResource(R.drawable.ic_trends)
            setColorFilter(onSurfaceVariant)
        }
        trendsLabel = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPxInt(2f)
            }
            text = context.getString(R.string.nav_trends)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(onSurfaceVariant)
            setTypeface(null, Typeface.NORMAL)
            maxLines = 1
            includeFontPadding = false
        }
        trendsContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.nav_trends)
            setBackgroundResource(rippleOut.resourceId)
            addView(trendsIndicator)
            addView(trendsIcon)
            addView(trendsLabel)
            setOnClickListener {
                if (currentTab != TAB_TRENDS) {
                    setSelectedTab(TAB_TRENDS)
                    onTrendsSelected?.invoke()
                }
            }
        }

        navRow.addView(studyContainer, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        navRow.addView(filesContainer, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        navRow.addView(trendsContainer, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        addView(navRow)
    }

    fun setSelectedTab(tab: Int) {
        currentTab = tab

        val primaryColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, "#003366".toColorInt())
        val onSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, "#44474F".toColorInt())

        if (tab == TAB_STUDY) {
            studyIcon.setColorFilter(primaryColor)
            studyLabel.setTextColor(primaryColor)
            studyLabel.setTypeface(null, Typeface.BOLD)
            studyIndicator.visibility = VISIBLE

            trendsIcon.setColorFilter(onSurfaceVariant)
            trendsLabel.setTextColor(onSurfaceVariant)
            trendsLabel.setTypeface(null, Typeface.NORMAL)
            trendsIndicator.visibility = INVISIBLE
        } else {
            trendsIcon.setColorFilter(primaryColor)
            trendsLabel.setTextColor(primaryColor)
            trendsLabel.setTypeface(null, Typeface.BOLD)
            trendsIndicator.visibility = VISIBLE

            studyIcon.setColorFilter(onSurfaceVariant)
            studyLabel.setTextColor(onSurfaceVariant)
            studyLabel.setTypeface(null, Typeface.NORMAL)
            studyIndicator.visibility = INVISIBLE
        }
    }
}
