package com.kibu.notes

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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

        val onRefresh = {
            render(context, container, unit, repository, null, onOpenFile)
        }

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
            setupTabsAndDisplay(context, contentContainer, initialResources, repository, onOpenFile, tabLayout, onRefresh)
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
                setupTabsAndDisplay(context, contentContainer, resources, repository, onOpenFile, tabLayout, onRefresh)
            }
        }
    }

    private fun setupTabsAndDisplay(
        context: Context,
        contentContainer: ViewGroup,
        resources: List<Resource>,
        repository: SupabaseRepository,
        onOpenFile: (File) -> Unit,
        tabLayout: TabLayout,
        onRefresh: () -> Unit
    ) {
        fun showTab(tabIndex: Int) {
            contentContainer.removeAllViews()
            val filtered = when (tabIndex) {
                0 -> resources.filter { res ->
                    val cat = res.category.lowercase(Locale.getDefault()).replace("_", " ").trim()
                    val type = res.resourceType.lowercase(Locale.getDefault()).replace("_", " ").trim()
                    cat.contains("topic") || type.contains("topic")
                }
                1 -> resources.filter { res ->
                    val cat = res.category.lowercase(Locale.getDefault()).replace("_", " ").trim()
                    val type = res.resourceType.lowercase(Locale.getDefault()).replace("_", " ").trim()
                    cat.contains("past") || cat.contains("paper") || type.contains("past") || type.contains("paper")
                }
                else -> resources.filter { res ->
                    val cat = res.category.lowercase(Locale.getDefault()).replace("_", " ").trim()
                    val type = res.resourceType.lowercase(Locale.getDefault()).replace("_", " ").trim()
                    val isTopic = cat.contains("topic") || type.contains("topic")
                    val isPast = cat.contains("past") || cat.contains("paper") || type.contains("past") || type.contains("paper")
                    !isTopic && !isPast
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
                displayResources(context, contentContainer, filtered, repository, onOpenFile, onRefresh)
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
        onOpenFile: (File) -> Unit,
        onRefresh: () -> Unit
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
            val resRow = createResourceRowView(context, res, repository, onOpenFile, onRefresh)
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
        onOpenFile: (File) -> Unit,
        onRefresh: () -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 12)
            isClickable = true
            isFocusable = true
            setOnLongClickListener {
                showReportBottomSheet(context, res, repository, onRefresh)
                true
            }
        }

        val nameForBadge = res.fileName.ifEmpty { res.title }
        val ext = nameForBadge.substringAfterLast('.', "").lowercase(Locale.getDefault())
        val (badgeText, badgeColor) = badgeFor(nameForBadge, null)

        val badgeFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (40 * context.resources.displayMetrics.density).toInt(),
                (40 * context.resources.displayMetrics.density).toInt()
            ).apply {
                marginEnd = (14 * context.resources.displayMetrics.density).toInt()
            }
        }

        val badgeView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            text = badgeText
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            val bgDrawable = ContextCompat.getDrawable(context, R.drawable.badge_bg)?.mutate()
            bgDrawable?.setTint(badgeColor)
            background = bgDrawable
        }
        badgeFrame.addView(badgeView)

        val thumbView = ShapeableImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(8 * context.resources.displayMetrics.density)
                .build()
        }
        badgeFrame.addView(thumbView)

        fun checkAndLoadThumb() {
            if (repository.isResourceDownloaded(context, res)) {
                val localFile = repository.getLocalResourceFile(context, res)
                if (localFile.exists()) {
                    val fileKind = FileKind.detect(ext, null)
                    if (Thumbs.supported(fileKind, ext)) {
                        Thumbs.load(context, Uri.fromFile(localFile), ext, thumbView, badgeView)
                    }
                }
            }
        }

        checkAndLoadThumb()
        row.addView(badgeFrame)

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

        val uploaderName = res.description?.trim().orEmpty()
        if (uploaderName.isNotEmpty()) {
            val uploaderView = TextView(context).apply {
                text = if (uploaderName.lowercase(Locale.getDefault()).startsWith("shared by")) {
                    uploaderName
                } else {
                    context.getString(R.string.shared_by, uploaderName)
                }
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
            }
            infoLayout.addView(uploaderView)
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
                                checkAndLoadThumb()
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

    private fun showReportBottomSheet(
        context: Context,
        res: Resource,
        repository: SupabaseRepository,
        onResourceUpdated: () -> Unit
    ) {
        val dialog = BottomSheetDialog(context)

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val handleBar = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (40 * context.resources.displayMetrics.density).toInt(),
                (4 * context.resources.displayMetrics.density).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 8, 0, 24)
            }
            val bgDrawable = ContextCompat.getDrawable(context, R.drawable.badge_bg)?.mutate()
            bgDrawable?.setTint(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant))
            background = bgDrawable
        }
        dialogLayout.addView(handleBar)

        val header = TextView(context).apply {
            text = context.getString(R.string.report_document_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        dialogLayout.addView(header)

        val reliableTitle = TextView(context).apply {
            text = context.getString(R.string.keep_reliable_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTypeface(null, Typeface.BOLD)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
            setPadding(0, 0, 0, 8)
        }
        dialogLayout.addView(reliableTitle)

        val reliableMsg = TextView(context).apply {
            text = context.getString(R.string.keep_reliable_message)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, 0, 0, 20)
        }
        dialogLayout.addView(reliableMsg)

        val fileCard = MaterialCardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
            cardElevation = 0f
            strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
            setStrokeColor(
                ColorStateList.valueOf(
                    MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
                )
            )
        }
        val fileInfoLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 20, 24, 20)

            addView(TextView(context).apply {
                text = "📄"
                textSize = 24f
                setPadding(0, 0, 20, 0)
            })

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val docTitle = res.title.ifEmpty { res.fileName }
            textCol.addView(TextView(context).apply {
                text = docTitle
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTypeface(null, Typeface.BOLD)
                maxLines = 2
            })
            val categoryDisplay = when (res.category.lowercase(Locale.getDefault()).trim()) {
                "past_papers", "past papers" -> "Past papers"
                "shared" -> "Shared"
                else -> "Topics"
            }
            textCol.addView(TextView(context).apply {
                text = context.getString(R.string.current_category, categoryDisplay)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
            addView(textCol)
        }
        fileCard.addView(fileInfoLayout)
        dialogLayout.addView(fileCard)

        val questionTitle = TextView(context).apply {
            text = context.getString(R.string.wrong_category_question)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
        dialogLayout.addView(questionTitle)

        val questionSub = TextView(context).apply {
            text = context.getString(R.string.move_category_instruction)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, 0, 0, 16)
        }
        dialogLayout.addView(questionSub)

        val radioGroup = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }

        val currentCatClean = res.category.lowercase(Locale.getDefault()).replace("_", " ").trim()

        val radioTopics = RadioButton(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.category_topics)
            isChecked = currentCatClean.contains("topic")
        }
        val radioShared = RadioButton(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.category_shared)
            isChecked = !currentCatClean.contains("topic") && !currentCatClean.contains("past") && !currentCatClean.contains("paper")
        }
        val radioPastPapers = RadioButton(context).apply {
            id = View.generateViewId()
            text = context.getString(R.string.category_past_papers)
            isChecked = currentCatClean.contains("past") || currentCatClean.contains("paper")
        }

        radioGroup.addView(radioTopics)
        radioGroup.addView(radioShared)
        radioGroup.addView(radioPastPapers)
        dialogLayout.addView(radioGroup)

        val inputLayout = TextInputLayout(context).apply {
            hint = context.getString(R.string.report_reason_label)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }
        val inputEdit = TextInputEditText(context).apply {
            minLines = 2
            maxLines = 4
        }
        inputLayout.addView(inputEdit)
        dialogLayout.addView(inputLayout)

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val cancelBtn = MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = context.getString(android.R.string.cancel)
            setOnClickListener { dialog.dismiss() }
        }

        val submitBtn = MaterialButton(context).apply {
            text = context.getString(R.string.submit_report)
            setOnClickListener {
                val selectedCategory = when (radioGroup.checkedRadioButtonId) {
                    radioPastPapers.id -> "past_papers"
                    radioShared.id -> "shared"
                    else -> "topics"
                }

                val userReason = inputEdit.text?.toString()?.trim().orEmpty()
                val categoryChanged = selectedCategory != res.category

                val finalReason = when {
                    userReason.isNotEmpty() -> userReason
                    categoryChanged -> "Moved category from ${res.category} to $selectedCategory"
                    else -> "Reported document: ${res.title.ifEmpty { res.fileName }}"
                }

                isEnabled = false
                text = context.getString(R.string.submitting)

                CoroutineScope(Dispatchers.Main).launch {
                    val reportResult = runCatching {
                        withContext(Dispatchers.IO) {
                            repository.reportResource(
                                documentTitle = res.title.ifEmpty { res.fileName },
                                documentId = res.id,
                                reason = finalReason
                            )
                            if (categoryChanged) {
                                repository.updateResourceCategory(
                                    context = context,
                                    resourceId = res.id,
                                    newCategory = selectedCategory
                                )
                            }
                        }
                    }

                    if (reportResult.isSuccess) {
                        Toast.makeText(context, context.getString(R.string.thank_you_feedback), Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        onResourceUpdated()
                    } else {
                        val err = reportResult.exceptionOrNull()?.message ?: "Failed to submit report"
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        isEnabled = true
                        text = context.getString(R.string.submit_report)
                    }
                }
            }
        }

        buttonsRow.addView(cancelBtn)
        buttonsRow.addView(submitBtn)
        dialogLayout.addView(buttonsRow)

        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }
        scrollView.addView(dialogLayout)

        dialog.setContentView(scrollView)

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as? FrameLayout
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                val displayMetrics = context.resources.displayMetrics
                val targetHeight = (displayMetrics.heightPixels * 0.75).toInt()
                sheet.layoutParams.height = targetHeight
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        dialog.show()
    }

    fun cancelJob() {
        job?.cancel()
    }
}
