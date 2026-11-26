package com.kibu.notes.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withTranslation
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.kibu.notes.R

class WaveNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        const val TAB_STUDY = 0
        const val TAB_TRENDS = 1

        // Wave geometry (dp)
        private const val EDGE_Y = 24f      // raised left/right edges
        private const val VALLEY_Y = 36f    // dips either side of the peak
        private const val PEAK_Y = 2f       // top of the mountain
        private const val CORNER_R = 22f
        private const val BUTTON_SIZE = 58f
        private const val BUTTON_TOP = 6f   // 4dp gap under the peak
        private const val ROW_TOP = 40f     // tabs sit below the lowest part of the curve
        private const val BUTTON_GAP = 76f  // empty slot under the Files button
    }

    var onStudySelected: (() -> Unit)? = null
    var onFilesSelected: (() -> Unit)? = null
    var onTrendsSelected: (() -> Unit)? = null

    private var currentTab = TAB_STUDY

    private val wavePath = Path()
    private val topBorderPath = Path()

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private lateinit var studyContainer: LinearLayout
    private lateinit var studyIcon: ImageView
    private lateinit var studyIndicator: View

    private lateinit var trendsContainer: LinearLayout
    private lateinit var trendsIcon: ImageView
    private lateinit var trendsIndicator: View

    private lateinit var filesCardBtn: MaterialCardView

    private val density = resources.displayMetrics.density

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        setupViews()
    }

    private fun dpToPx(dp: Float): Float = dp * density
    private fun dpToPxInt(dp: Float): Int = (dp * density).toInt()

    private fun setupViews() {
        val primaryColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, "#003366".toColorInt())
        val surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val onSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, "#44474F".toColorInt())

        backgroundPaint.color = surfaceColor
        shadowPaint.color = ContextCompat.getColor(context, R.color.wave_shadow_color)

        strokePaint.color = primaryColor
        strokePaint.strokeWidth = dpToPx(2.5f)
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeJoin = Paint.Join.ROUND

        val rippleOut = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, rippleOut, true)

        // 1. Study (left)
        studyIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPxInt(24f), dpToPxInt(24f))
            setImageResource(R.drawable.ic_study)
            setColorFilter(primaryColor)
        }
        studyIndicator = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPxInt(18f), dpToPxInt(3f)).apply {
                topMargin = dpToPxInt(4f)
            }
            setBackgroundResource(R.drawable.shape_active_indicator)
            visibility = VISIBLE
        }
        studyContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.nav_study)
            setBackgroundResource(rippleOut.resourceId)
            addView(studyIcon)
            addView(studyIndicator)
            setOnClickListener {
                if (currentTab != TAB_STUDY) {
                    setSelectedTab(TAB_STUDY)
                    onStudySelected?.invoke()
                }
            }
        }

        // 2. Trends (right)
        trendsIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPxInt(24f), dpToPxInt(24f))
            setImageResource(R.drawable.ic_trends)
            setColorFilter(onSurfaceVariant)
        }
        trendsIndicator = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPxInt(18f), dpToPxInt(3f)).apply {
                topMargin = dpToPxInt(4f)
            }
            setBackgroundResource(R.drawable.shape_active_indicator)
            visibility = INVISIBLE
        }
        trendsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.nav_trends)
            setBackgroundResource(rippleOut.resourceId)
            addView(trendsIcon)
            addView(trendsIndicator)
            setOnClickListener {
                if (currentTab != TAB_TRENDS) {
                    setSelectedTab(TAB_TRENDS)
                    onTrendsSelected?.invoke()
                }
            }
        }

        // Row: [ Study (weight 1) ][ gap under Files ][ Trends (weight 1) ]
        val navRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                topMargin = dpToPxInt(ROW_TOP)
            }
            addView(studyContainer, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(Space(context), LinearLayout.LayoutParams(dpToPxInt(BUTTON_GAP), 1))
            addView(trendsContainer, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }

        // 3. Centre Files button
        val filesIcon = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dpToPxInt(28f), dpToPxInt(28f), Gravity.CENTER)
            setImageResource(R.drawable.ic_files)
        }
        filesCardBtn = MaterialCardView(context).apply {
            val size = dpToPxInt(BUTTON_SIZE)
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                topMargin = dpToPxInt(BUTTON_TOP)
            }
            radius = size / 2f
            cardElevation = 8 * density
            setCardBackgroundColor(ColorStateList.valueOf(primaryColor))
            strokeWidth = (2.5f * density).toInt()
            setStrokeColor(ColorStateList.valueOf(surfaceColor))
            isClickable = true
            isFocusable = true
            addView(filesIcon)
            setOnClickListener { onFilesSelected?.invoke() }
        }

        addView(navRow)
        addView(filesCardBtn) // last = drawn on top
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildWavePaths(w.toFloat(), h.toFloat())
    }

    private fun buildWavePaths(w: Float, h: Float) {
        val edgeY = dpToPx(EDGE_Y)
        val valleyY = dpToPx(VALLEY_Y)
        val peakY = dpToPx(PEAK_Y)
        val cr = dpToPx(CORNER_R)

        val cx = w / 2f
        val d = w * 0.28f              // horizontal distance from centre to each valley
        val leftValleyX = cx - d
        val rightValleyX = cx + d
        val k = 0.47f                  // hill steepness

        topBorderPath.reset()
        wavePath.reset()

        // Left raised edge -> dip
        topBorderPath.moveTo(cr, edgeY)
        val lMid = (leftValleyX - cr) * 0.5f
        topBorderPath.cubicTo(cr + lMid, edgeY, leftValleyX - lMid, valleyY, leftValleyX, valleyY)

        // Dip -> peak (over the Files button)
        topBorderPath.cubicTo(leftValleyX + d * k, valleyY, cx - d * k, peakY, cx, peakY)

        // Peak -> dip
        topBorderPath.cubicTo(cx + d * k, peakY, rightValleyX - d * k, valleyY, rightValleyX, valleyY)

        // Dip -> raised right edge
        val rMid = ((w - cr) - rightValleyX) * 0.5f
        topBorderPath.cubicTo(rightValleyX + rMid, valleyY, (w - cr) - rMid, edgeY, w - cr, edgeY)

        // Filled background = same contour + rounded body
        wavePath.addPath(topBorderPath)
        wavePath.quadTo(w, edgeY, w, edgeY + cr)
        wavePath.lineTo(w, h - cr)
        wavePath.quadTo(w, h, w - cr, h)
        wavePath.lineTo(cr, h)
        wavePath.quadTo(0f, h, 0f, h - cr)
        wavePath.lineTo(0f, edgeY + cr)
        wavePath.quadTo(0f, edgeY, cr, edgeY)
        wavePath.close()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.withTranslation(y = dpToPx(3f)) {
            drawPath(wavePath, shadowPaint)
        }
        canvas.drawPath(wavePath, backgroundPaint)
        canvas.drawPath(topBorderPath, strokePaint)
        super.onDraw(canvas)
    }

    fun setSelectedTab(tab: Int) {
        currentTab = tab

        val primaryColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, "#003366".toColorInt())
        val onSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, "#44474F".toColorInt())

        if (tab == TAB_STUDY) {
            studyIcon.setColorFilter(primaryColor)
            studyIndicator.visibility = VISIBLE

            trendsIcon.setColorFilter(onSurfaceVariant)
            trendsIndicator.visibility = INVISIBLE
        } else {
            trendsIcon.setColorFilter(primaryColor)
            trendsIndicator.visibility = VISIBLE

            studyIcon.setColorFilter(onSurfaceVariant)
            studyIndicator.visibility = INVISIBLE
        }
    }
}