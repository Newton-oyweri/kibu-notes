package com.kibu.notes

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.tabs.TabLayout
import com.kibu.notes.data.SupabaseRepository
import com.kibu.notes.data.models.AcademicUnit
import com.kibu.notes.data.models.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object UnitDetailView {

    private var job: Job? = null

    fun render(
        context: Context,
        container: ViewGroup,
        unit: AcademicUnit,
        repository: SupabaseRepository,
        initialResources: List<Resource>? = null,
        onOpenFile: (File) -> Unit
    ) {
        container.removeAllViews()

        val tabLayout = TabLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 16, 16, 16) }
            addTab(newTab().setText("Topics"))
            addTab(newTab().setText("Past papers"))
            addTab(newTab().setText("Shared"))
        }
        container.addView(tabLayout)

        val contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(contentContainer)

        if (!initialResources.isNullOrEmpty()) {
            setupTabsAndDisplay(context, contentContainer, initialResources, repository, onOpenFile, tabLayout)
            return
        }

        val loadingView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 64, 48, 64)
            addView(ProgressBar(context).apply { isIndeterminate = true })
        }
        contentContainer.addView(loadingView)

        job?.cancel()
        job = CoroutineScope(Dispatchers.Main).launch {
            val resources = runCatching {
                withContext(Dispatchers.IO) {
                    repository.getResourcesByUnit(unit.id, context)
                }
            }.getOrNull()

            contentContainer.removeView(loadingView)

            if (resources.isNullOrEmpty()) {
                val emptyView = TextView(context).apply {
                    text = context.getString(R.string.no_notes_for_unit)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    gravity = Gravity.CENTER
                    setPadding(48, 64, 48, 64)
                }
                contentContainer.addView(emptyView)
            } else {
                setupTabsAndDisplay(context, contentContainer, resources, repository, onOpenFile, tabLayout)
            }
        }
    }

    private fun setupTabsAndDisplay(
        context: Context,
        contentContainer: ViewGroup,
        resources: List<Resource>,
        repository: SupabaseRepository,
        onOpenFile: (File) -> Unit,
        tabLayout: TabLayout
    ) {
        fun showTab(tabIndex: Int) {
            contentContainer.removeAllViews()
            val filtered = when (tabIndex) {
                0 -> resources.filter { res ->
                    val cat = res.category.lowercase(Locale.getDefault()).replace("_", " ").trim()
                    cat.contains("topic")
                }
                1 -> resources.filter { res ->
                    val cat = res.category.lowercase(Locale.getDefault()).replace("_", " ").trim()
                    cat.contains("past") || cat.contains("paper")
                }
                else -> resources.filter { res ->
                    val cat = res.category.lowercase(Locale.getDefault()).replace("_", " ").trim()
                    cat.contains("shared") || (!cat.contains("topic") && !cat.contains("past") && !cat.contains("paper"))
                }
            }

            if (filtered.isEmpty()) {
                val emptyView = TextView(context).apply {
                    text = when (tabIndex) {
                        0 -> "No topics available"
                        1 -> "No past papers available"
                        else -> "No shared resources available"
                    }
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    gravity = Gravity.CENTER
                    setPadding(48, 64, 48, 64)
                }
                contentContainer.addView(emptyView)
            } else {
                displayResources(context, contentContainer, filtered, repository, onOpenFile)
            }
        }

        tabLayout.clearOnTabSelectedListeners()
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        showTab(tabLayout.selectedTabPosition)
    }

    private fun displayResources(
        context: Context,
        container: ViewGroup,
        resources: List<Resource>,
        repository: SupabaseRepository,
        onOpenFile: (File) -> Unit
    ) {
        val resCard = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(16, 0, 16, 24) }
            cardElevation = 0f
            strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
            setStrokeColor(
                ColorStateList.valueOf(
                    MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
                )
            )
        }

        val resLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        resources.forEachIndexed { index, res ->
            val resRow = createResourceRowView(context, res, repository, onOpenFile)
            resLayout.addView(resRow)

            if (index < resources.size - 1) {
                val divider = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * context.resources.displayMetrics.density).toInt()
                    ).apply { setMargins(0, 8, 0, 8) }
                    setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant))
                }
                resLayout.addView(divider)
            }
        }

        resCard.addView(resLayout)
        container.addView(resCard)
    }

    private fun createResourceRowView(
        context: Context,
        res: Resource,
        repository: SupabaseRepository,
        onOpenFile: (File) -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 12)
        }

        val iconView = TextView(context).apply {
            text = when (res.resourceType.lowercase(Locale.getDefault())) {
                "past_paper" -> "📝"
                "topic_note" -> "📄"
                "summary" -> "📌"
                "syllabus" -> "📋"
                "assignment" -> "✏️"
                else -> "📄"
            }
            textSize = 20f
            setPadding(0, 0, 16, 0)
        }
        row.addView(iconView)

        val infoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(context).apply {
            text = res.title.ifEmpty { res.fileName }
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            setTypeface(null, Typeface.BOLD)
        }
        infoLayout.addView(titleView)

        val subtitleParts = mutableListOf<String>()
        if (res.resourceType.isNotEmpty()) {
            subtitleParts += res.resourceType.replace("_", " ").replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }
        if (res.fileSize > 0) {
            subtitleParts += Formatter.formatShortFileSize(context, res.fileSize)
        }
        if (res.examYear != null && res.examYear > 0) {
            subtitleParts += "Exam: ${res.examYear}"
        }

        if (subtitleParts.isNotEmpty()) {
            val subView = TextView(context).apply {
                text = subtitleParts.joinToString(" • ")
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            }
            infoLayout.addView(subView)
        }

        row.addView(infoLayout)

        val actionFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        fun updateActionState() {
            actionFrame.removeAllViews()

            val downloaded = repository.isResourceDownloaded(context, res)

            if (downloaded) {
                val openBtn = MaterialButton(context).apply {
                    text = context.getString(R.string.open_file_action)
                    setIconResource(R.drawable.ic_check)
                    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer))
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer))
                    iconTint = ColorStateList.valueOf(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSecondaryContainer))
                    strokeWidth = 0
                    setOnClickListener {
                        val file = repository.getLocalResourceFile(context, res)
                        onOpenFile(file)
                    }
                }
                actionFrame.addView(openBtn)
            } else {
                val downloadBtn = MaterialButton(context).apply {
                    text = context.getString(R.string.download)
                    setIconResource(R.drawable.ic_down)
                    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    setOnClickListener {
                        actionFrame.removeAllViews()
                        val progressView = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER
                            setPadding(16, 8, 16, 8)
                            addView(ProgressBar(context).apply {
                                isIndeterminate = true
                                layoutParams = LinearLayout.LayoutParams(48, 48)
                            })
                        }
                        actionFrame.addView(progressView)

                        CoroutineScope(Dispatchers.Main).launch {
                            val downloadedFile = runCatching {
                                repository.downloadResource(context, res)
                            }.getOrNull()

                            if (downloadedFile != null && downloadedFile.exists()) {
                                Toast.makeText(context, "Downloaded ${res.title}", Toast.LENGTH_SHORT).show()
                                updateActionState()
                                onOpenFile(downloadedFile)
                            } else {
                                Toast.makeText(context, context.getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
                                updateActionState()
                            }
                        }
                    }
                }
                actionFrame.addView(downloadBtn)
            }
        }

        updateActionState()
        row.addView(actionFrame)

        return row
    }

    fun cancelJob() {
        job?.cancel()
    }
}
