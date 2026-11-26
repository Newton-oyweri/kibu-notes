package com.kibu.notes.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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
    }

    var onStudySelected: (() -> Unit)? = null
    var onFilesSelected: (() -> Unit)? = null
    var onTrendsSelected: (() -> Unit)? = null

    private var currentTab = TAB_STUDY

    private val wavePath = Path()
    private val topBorderPath = Path()

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private lateinit var studyContainer: LinearLayout
    private lateinit var studyIcon: ImageView
    private lateinit var studyLabel: TextView
    private lateinit var studyIndicator: View

    private lateinit var trendsContainer: LinearLayout
    private lateinit var trendsIcon: ImageView
    private lateinit var trendsLabel: TextView
    private lateinit var trendsIndicator: View

    private lateinit var filesCardBtn: MaterialCardView

    private val density = resources.displayMetrics.density

    init {
        setWillNotDraw(false)
        setupViews()
    }

    private fun dpToPx(dp: Float): Float = dp * density

    private fun setupViews() {
        val primaryColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, "#003366".toColorInt())
        val surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val onSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, "#44474F".toColorInt())

        backgroundPaint.color = surfaceColor

        // Translucent shadow behind wave container
        val shadowColor = ContextCompat.getColor(context, R.color.wave_shadow_color)
        shadowPaint.color = shadowColor

        // Crisp Kibu-blue stroke along the upper mountain boundary
        strokePaint.color = primaryColor
        strokePaint.strokeWidth = dpToPx(2.5f)
        strokePaint.strokeCap = Paint.Cap.ROUND

        // 1. Study Container (Left)
        studyIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((22 * density).toInt(), (22 * density).toInt())
            setImageResource(R.drawable.ic_study)
            setColorFilter(primaryColor)
        }
        studyLabel = TextView(context).apply {
            text = context.getString(R.string.nav_study)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(primaryColor)
            setSingleLine(true)
            maxLines = 1
            ellipsize = null
            setPadding(0, (2 * density).toInt(), 0, 0)
        }
        studyIndicator = View(context).apply {
            layoutParams = LinearLayout.LayoutParams((18 * density).toInt(), (3 * density).toInt()).apply {
                topMargin = (3 * density).toInt()
            }
            setBackgroundResource(R.drawable.shape_active_indicator)
            visibility = VISIBLE
        }

        studyContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setPadding((16 * density).toInt(), (6 * density).toInt(), (16 * density).toInt(), (6 * density).toInt())

            addView(studyIcon)
            addView(studyLabel)
            addView(studyIndicator)

            setOnClickListener {
                if (currentTab != TAB_STUDY) {
                    setSelectedTab(TAB_STUDY)
                    onStudySelected?.invoke()
                }
            }
        }

        // 2. Trends Container (Right)
        trendsIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((22 * density).toInt(), (22 * density).toInt())
            setImageResource(R.drawable.ic_trends)
            setColorFilter(onSurfaceVariant)
        }
        trendsLabel = TextView(context).apply {
            text = context.getString(R.string.nav_trends)
            textSize = 12f
            setTypeface(null, Typeface.NORMAL)
            setTextColor(onSurfaceVariant)
            setSingleLine(true)
            maxLines = 1
            ellipsize = null
            setPadding(0, (2 * density).toInt(), 0, 0)
        }
        trendsIndicator = View(context).apply {
            layoutParams = LinearLayout.LayoutParams((18 * density).toInt(), (3 * density).toInt()).apply {
                topMargin = (3 * density).toInt()
            }
            setBackgroundResource(R.drawable.shape_active_indicator)
            visibility = INVISIBLE
        }

        trendsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setPadding((16 * density).toInt(), (6 * density).toInt(), (16 * density).toInt(), (6 * density).toInt())

            addView(trendsIcon)
            addView(trendsLabel)
            addView(trendsIndicator)

            setOnClickListener {
                if (currentTab != TAB_TRENDS) {
                    setSelectedTab(TAB_TRENDS)
                    onTrendsSelected?.invoke()
                }
            }
        }

        // 3. Centre Files Button
        val filesIcon = ImageView(context).apply {
            layoutParams = LayoutParams((28 * density).toInt(), (28 * density).toInt(), Gravity.CENTER)
            setImageResource(R.drawable.ic_files)
        }

        filesCardBtn = MaterialCardView(context).apply {
            val size = (58 * density).toInt()
            layoutParams = LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            }
            radius = size / 2f
            cardElevation = (8 * density)
            setCardBackgroundColor(ColorStateList.valueOf(primaryColor))
            strokeWidth = (2.5f * density).toInt()
            setStrokeColor(ColorStateList.valueOf(surfaceColor))
            isClickable = true
            isFocusable = true
            addView(filesIcon)

            setOnClickListener {
                onFilesSelected?.invoke()
            }
        }

        addView(studyContainer)
        addView(trendsContainer)
        addView(filesCardBtn)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val w = (right - left).toFloat()
        val h = (bottom - top).toFloat()

        if (w <= 0 || h <= 0) return

        val baselineY = dpToPx(38f)

        // Center files button at top peak
        val filesW = filesCardBtn.measuredWidth
        val filesH = filesCardBtn.measuredHeight
        val filesLeft = ((w - filesW) / 2f).toInt()
        val filesTop = dpToPx(2f).toInt()
        filesCardBtn.layout(filesLeft, filesTop, filesLeft + filesW, filesTop + filesH)

        // Position Study Container on Left Half
        val studyW = studyContainer.measuredWidth
        val studyH = studyContainer.measuredHeight
        val studyLeft = ((w * 0.22f) - (studyW / 2f)).coerceAtLeast(dpToPx(8f)).toInt()
        val studyTop = (baselineY + (h - baselineY - studyH) / 2f).toInt()
        studyContainer.layout(studyLeft, studyTop, studyLeft + studyW, studyTop + studyH)

        // Position Trends Container on Right Half
        val trendsW = trendsContainer.measuredWidth
        val trendsH = trendsContainer.measuredHeight
        val trendsLeft = ((w * 0.78f) - (trendsW / 2f)).coerceAtMost(w - trendsW - dpToPx(8f)).toInt()
        val trendsTop = (baselineY + (h - baselineY - trendsH) / 2f).toInt()
        trendsContainer.layout(trendsLeft, trendsTop, trendsLeft + trendsW, trendsTop + trendsH)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildWavePaths(w.toFloat(), h.toFloat())
    }

    private fun buildWavePaths(w: Float, h: Float) {
        val baselineY = dpToPx(38f)
        val peakY = dpToPx(4f)
        val waveWidth = dpToPx(140f)
        val cornerRadius = dpToPx(22f)

        val centerX = w / 2f
        val startWaveX = centerX - waveWidth / 2f
        val endWaveX = centerX + waveWidth / 2f

        wavePath.reset()
        topBorderPath.reset()

        // 1. Top-left start
        wavePath.moveTo(cornerRadius, baselineY)
        topBorderPath.moveTo(cornerRadius, baselineY)

        // 2. Line to start of wave
        wavePath.lineTo(startWaveX, baselineY)
        topBorderPath.lineTo(startWaveX, baselineY)

        // 3. Wave curve UP to prominent mountain peak
        val c1x = startWaveX + waveWidth * 0.25f
        val c1y = baselineY + dpToPx(4f)
        val c2x = centerX - waveWidth * 0.22f
        val c2y = peakY
        wavePath.cubicTo(c1x, c1y, c2x, c2y, centerX, peakY)
        topBorderPath.cubicTo(c1x, c1y, c2x, c2y, centerX, peakY)

        // 4. Wave curve DOWN from mountain peak to end of wave
        val c3x = centerX + waveWidth * 0.22f
        val c3y = peakY
        val c4x = endWaveX - waveWidth * 0.25f
        val c4y = baselineY + dpToPx(4f)
        wavePath.cubicTo(c3x, c3y, c4x, c4y, endWaveX, baselineY)
        topBorderPath.cubicTo(c3x, c3y, c4x, c4y, endWaveX, baselineY)

        // 5. Line to top-right
        wavePath.lineTo(w - cornerRadius, baselineY)
        topBorderPath.lineTo(w - cornerRadius, baselineY)

        // 6. Complete closed path for background fill
        wavePath.quadTo(w, baselineY, w, baselineY + cornerRadius)
        wavePath.lineTo(w, h - cornerRadius)
        wavePath.quadTo(w, h, w - cornerRadius, h)
        wavePath.lineTo(cornerRadius, h)
        wavePath.quadTo(0f, h, 0f, h - cornerRadius)
        wavePath.lineTo(0f, baselineY + cornerRadius)
        wavePath.quadTo(0f, baselineY, cornerRadius, baselineY)
        wavePath.close()
    }

    override fun onDraw(canvas: Canvas) {
        // Draw soft drop shadow behind wave background
        canvas.withTranslation(y = dpToPx(3f)) {
            drawPath(wavePath, shadowPaint)
        }

        // Draw main wave background
        canvas.drawPath(wavePath, backgroundPaint)

        // Draw crisp Kibu-blue upper mountain boundary outline
        canvas.drawPath(topBorderPath, strokePaint)

        super.onDraw(canvas)
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
