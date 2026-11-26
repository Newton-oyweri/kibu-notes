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
import androidx.core.graphics.withTranslation
import com.google.android.material.R as MaterialR
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
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
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
        val primaryColor = MaterialColors.getColor(this, MaterialR.attr.colorPrimary, Color.BLUE)
        val surfaceColor = MaterialColors.getColor(this, MaterialR.attr.colorSurface, Color.WHITE)
        val onSurfaceVariant = MaterialColors.getColor(this, MaterialR.attr.colorOnSurfaceVariant, Color.GRAY)

        backgroundPaint.color = surfaceColor

        // Subtle translucent shadow color
        val shadowColor = ContextCompat.getColor(context, R.color.wave_shadow_color)
        shadowPaint.color = shadowColor

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
            setPadding(0, (2 * density).toInt(), 0, 0)
        }
        studyIndicator = View(context).apply {
            layoutParams = LinearLayout.LayoutParams((16 * density).toInt(), (3 * density).toInt()).apply {
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
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())

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
            setPadding(0, (2 * density).toInt(), 0, 0)
        }
        trendsIndicator = View(context).apply {
            layoutParams = LinearLayout.LayoutParams((16 * density).toInt(), (3 * density).toInt()).apply {
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
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())

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
            layoutParams = LayoutParams((26 * density).toInt(), (26 * density).toInt(), Gravity.CENTER)
            setImageResource(R.drawable.ic_files)
        }

        filesCardBtn = MaterialCardView(context).apply {
            val size = (54 * density).toInt()
            layoutParams = LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            }
            radius = size / 2f
            cardElevation = (6 * density)
            setCardBackgroundColor(ColorStateList.valueOf(primaryColor))
            strokeWidth = (2 * density).toInt()
            setStrokeColor(ColorStateList.valueOf(surfaceColor))
            isClickable = true
            isFocusable = true
            addView(filesIcon)

            setOnClickListener {
                onFilesSelected?.invoke()
            }
        }

        // Position items in FrameLayout
        val leftLayoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.START or Gravity.BOTTOM
            leftMargin = (28 * density).toInt()
            bottomMargin = (8 * density).toInt()
        }

        val rightLayoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            rightMargin = (28 * density).toInt()
            bottomMargin = (8 * density).toInt()
        }

        addView(studyContainer, leftLayoutParams)
        addView(trendsContainer, rightLayoutParams)
        addView(filesCardBtn)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildWavePath(w.toFloat(), h.toFloat())

        // Adjust filesCardBtn top position relative to wave peak
        val peakY = dpToPx(2f)
        val filesLp = filesCardBtn.layoutParams as LayoutParams
        filesLp.topMargin = peakY.toInt()
        filesCardBtn.layoutParams = filesLp
    }

    private fun buildWavePath(w: Float, h: Float) {
        val baselineY = dpToPx(20f)
        val peakY = dpToPx(2f)
        val waveWidth = dpToPx(130f)
        val cornerRadius = dpToPx(20f)

        val centerX = w / 2f
        val startWaveX = centerX - waveWidth / 2f
        val endWaveX = centerX + waveWidth / 2f

        wavePath.reset()

        // Top-left start
        wavePath.moveTo(cornerRadius, baselineY)

        // Line to wave start
        wavePath.lineTo(startWaveX, baselineY)

        // Wave curve UP to peak
        val c1x = startWaveX + waveWidth * 0.28f
        val c1y = baselineY
        val c2x = centerX - waveWidth * 0.22f
        val c2y = peakY
        wavePath.cubicTo(c1x, c1y, c2x, c2y, centerX, peakY)

        // Wave curve DOWN to wave end
        val c3x = centerX + waveWidth * 0.22f
        val c3y = peakY
        val c4x = endWaveX - waveWidth * 0.28f
        val c4y = baselineY
        wavePath.cubicTo(c3x, c3y, c4x, c4y, endWaveX, baselineY)

        // Line to top-right
        wavePath.lineTo(w - cornerRadius, baselineY)

        // Top-right corner
        wavePath.quadTo(w, baselineY, w, baselineY + cornerRadius)

        // Right edge down
        wavePath.lineTo(w, h - cornerRadius)

        // Bottom-right corner
        wavePath.quadTo(w, h, w - cornerRadius, h)

        // Bottom edge left
        wavePath.lineTo(cornerRadius, h)

        // Bottom-left corner
        wavePath.quadTo(0f, h, 0f, h - cornerRadius)

        // Left edge up
        wavePath.lineTo(0f, baselineY + cornerRadius)

        // Top-left corner
        wavePath.quadTo(0f, baselineY, cornerRadius, baselineY)

        wavePath.close()
    }

    override fun onDraw(canvas: Canvas) {
        // Draw soft drop shadow behind wave background
        canvas.withTranslation(y = dpToPx(2f)) {
            drawPath(wavePath, shadowPaint)
        }

        // Draw main wave background
        canvas.drawPath(wavePath, backgroundPaint)
        super.onDraw(canvas)
    }

    fun setSelectedTab(tab: Int) {
        currentTab = tab

        val primaryColor = MaterialColors.getColor(this, MaterialR.attr.colorPrimary, Color.BLUE)
        val onSurfaceVariant = MaterialColors.getColor(this, MaterialR.attr.colorOnSurfaceVariant, Color.GRAY)

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
