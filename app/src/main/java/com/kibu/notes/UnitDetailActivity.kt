package com.kibu.notes

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.kibu.notes.data.SupabaseRepository
import com.kibu.notes.data.models.AcademicUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UnitDetailActivity : AppCompatActivity() {

    @VisibleForTesting
    internal var supabaseRepository: SupabaseRepository = SupabaseRepository()

    private lateinit var currentUnit: AcademicUnit
    private lateinit var detailContainer: LinearLayout

    private val pickDocuments = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (!uris.isNullOrEmpty()) {
            if (uris.size == 1) {
                showUploadBottomSheet(uris.first())
            } else {
                showBatchUploadBottomSheet(uris)
            }
        }
    }

    /**
     * Category picker. Returns values that match the resource_category enum
     * exactly: topics | past_papers | cats.
     */
    private class CategoryPicker(
        val group: RadioGroup,
        private val pastPapers: RadioButton,
        private val cats: RadioButton
    ) {
        fun selected(): String = when (group.checkedRadioButtonId) {
            pastPapers.id -> "past_papers"
            cats.id -> "cats"
            else -> "topics"
        }
    }

    private fun buildCategoryPicker(): CategoryPicker {
        val group = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 32) }
        }

        val radioTopics = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.category_topics)
            isChecked = true
        }
        val radioPastPapers = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.category_past_papers)
            setPadding(24, 0, 0, 0)
        }
        val radioCats = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.category_cats)
            setPadding(24, 0, 0, 0)
        }

        group.addView(radioTopics)
        group.addView(radioPastPapers)
        group.addView(radioCats)
        return CategoryPicker(group, radioPastPapers, radioCats)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val unitId = intent.getStringExtra(EXTRA_UNIT_ID).orEmpty()
        val unitCode = intent.getStringExtra(EXTRA_UNIT_CODE).orEmpty()
        val unitTitle = intent.getStringExtra(EXTRA_UNIT_TITLE).orEmpty()

        currentUnit = AcademicUnit(id = unitId, code = unitCode, title = unitTitle)

        val root = FrameLayout(this).apply {
            id = R.id.root
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val toolbar = MaterialToolbar(this).apply {
            title = if (unitCode.isNotEmpty()) "$unitCode - $unitTitle" else unitTitle
            navigationIcon = AppCompatResources.getDrawable(this@UnitDetailActivity, R.drawable.ic_back)
            navigationContentDescription = getString(R.string.back)
            setNavigationOnClickListener { finish() }
        }
        mainLayout.addView(toolbar)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }

        detailContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, 64)
        }
        scrollView.addView(detailContainer)
        mainLayout.addView(scrollView)

        root.addView(mainLayout)

        val fab = ExtendedFloatingActionButton(this).apply {
            text = getString(R.string.share_more_notes)
            icon = AppCompatResources.getDrawable(this@UnitDetailActivity, R.drawable.ic_up)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                val marginPx = (20 * resources.displayMetrics.density).toInt()
                setMargins(0, 0, marginPx, marginPx)
            }
            setOnClickListener {
                pickDocuments.launch("*/*")
            }
        }
        root.addView(fab)
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        renderView()
    }

    private fun renderView() {
        UnitDetailView.render(
            context = this,
            container = detailContainer,
            unit = currentUnit,
            repository = supabaseRepository,
            onOpenFile = { file -> openInViewer(Uri.fromFile(file)) }
        )
    }

    private fun showUploadBottomSheet(uri: Uri) {
        val fileName = getFileNameFromUri(uri)
        val fileSize = getFileSizeFromUri(uri)
        val mimeType = contentResolver.getType(uri) ?: "application/pdf"

        val dialog = BottomSheetDialog(this)

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Title Header
        val header = TextView(this).apply {
            text = getString(R.string.upload_document_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(header)

        // File info card
        val fileCard = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
            cardElevation = 0f
            strokeWidth = (1 * resources.displayMetrics.density).toInt()
            setStrokeColor(
                ColorStateList.valueOf(
                    MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
                )
            )
        }
        val fileInfoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 20, 24, 20)

            addView(TextView(this@UnitDetailActivity).apply {
                text = "📄"
                textSize = 24f
                setPadding(0, 0, 20, 0)
            })

            val textCol = LinearLayout(this@UnitDetailActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this@UnitDetailActivity).apply {
                text = fileName
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTypeface(null, Typeface.BOLD)
                maxLines = 2
            })
            textCol.addView(TextView(this@UnitDetailActivity).apply {
                text = Formatter.formatShortFileSize(this@UnitDetailActivity, fileSize)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
            addView(textCol)
        }
        fileCard.addView(fileInfoLayout)
        dialogLayout.addView(fileCard)

        // Title Input Field
        val inputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.document_title_hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 24) }
        }
        val inputEdit = TextInputEditText(this).apply {
            val cleanName = fileName.substringBeforeLast(".")
            setText(cleanName)
        }
        inputLayout.addView(inputEdit)
        dialogLayout.addView(inputLayout)

        // Category selection label
        val catLabel = TextView(this).apply {
            text = getString(R.string.select_category)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        dialogLayout.addView(catLabel)

        val picker = buildCategoryPicker()
        dialogLayout.addView(picker.group)

        // Action Buttons Row
        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val cancelBtn = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = getString(android.R.string.cancel)
            setOnClickListener { dialog.dismiss() }
        }

        val confirmBtn = MaterialButton(this).apply {
            text = getString(R.string.confirm_upload)
            setIconResource(R.drawable.ic_up)
            setOnClickListener {
                val chosenTitle = inputEdit.text?.toString().orEmpty().ifEmpty { fileName }
                val selectedCategory = picker.selected()

                isEnabled = false
                text = getString(R.string.uploading)

                lifecycleScope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        runCatching {
                            contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        }.getOrNull()
                    }

                    if (bytes != null) {
                        val uploadResult = withContext(Dispatchers.IO) {
                            runCatching {
                                supabaseRepository.uploadResource(
                                    context = this@UnitDetailActivity,
                                    unitId = currentUnit.id,
                                    title = chosenTitle,
                                    category = selectedCategory,
                                    fileName = fileName,
                                    fileBytes = bytes,
                                    mimeType = mimeType
                                )
                            }
                        }

                        if (uploadResult.isSuccess) {
                            Toast.makeText(this@UnitDetailActivity, getString(R.string.upload_success), Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            renderView()
                        } else {
                            val err = uploadResult.exceptionOrNull()?.message ?: getString(R.string.upload_failed)
                            Toast.makeText(this@UnitDetailActivity, err, Toast.LENGTH_LONG).show()
                            isEnabled = true
                            text = getString(R.string.confirm_upload)
                        }
                    } else {
                        Toast.makeText(this@UnitDetailActivity, getString(R.string.upload_failed), Toast.LENGTH_SHORT).show()
                        isEnabled = true
                        text = getString(R.string.confirm_upload)
                    }
                }
            }
        }

        buttonsRow.addView(cancelBtn)
        buttonsRow.addView(confirmBtn)
        dialogLayout.addView(buttonsRow)

        dialog.setContentView(dialogLayout)
        dialog.show()
    }

    private fun showBatchUploadBottomSheet(uris: List<Uri>) {
        val dialog = BottomSheetDialog(this)
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val header = TextView(this).apply {
            text = getString(R.string.upload_batch_title, uris.size)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        dialogLayout.addView(header)

        val filesScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (180 * resources.displayMetrics.density).toInt()
            ).apply { setMargins(0, 0, 0, 24) }
        }

        val filesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        uris.forEach { uri ->
            val fileName = getFileNameFromUri(uri)
            val fileSize = getFileSizeFromUri(uri)

            val fileRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16, 12, 16, 12)

                addView(TextView(this@UnitDetailActivity).apply {
                    text = "📄"
                    textSize = 18f
                    setPadding(0, 0, 16, 0)
                })

                val textCol = LinearLayout(this@UnitDetailActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                textCol.addView(TextView(this@UnitDetailActivity).apply {
                    text = fileName
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    setTypeface(null, Typeface.BOLD)
                    maxLines = 1
                })
                textCol.addView(TextView(this@UnitDetailActivity).apply {
                    text = Formatter.formatShortFileSize(this@UnitDetailActivity, fileSize)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                })
                addView(textCol)
            }
            filesContainer.addView(fileRow)
        }

        filesScrollView.addView(filesContainer)
        dialogLayout.addView(filesScrollView)

        val catLabel = TextView(this).apply {
            text = getString(R.string.select_category)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        dialogLayout.addView(catLabel)

        val picker = buildCategoryPicker()
        dialogLayout.addView(picker.group)

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val cancelBtn = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = getString(android.R.string.cancel)
            setOnClickListener { dialog.dismiss() }
        }

        val confirmBtn = MaterialButton(this).apply {
            text = getString(R.string.upload_all, uris.size)
            setIconResource(R.drawable.ic_up)
            setOnClickListener {
                val selectedCategory = picker.selected()

                isEnabled = false
                var uploadedCount = 0

                lifecycleScope.launch {
                    uris.forEachIndexed { index, uri ->
                        text = getString(R.string.uploading_progress, index + 1, uris.size)
                        val fileName = getFileNameFromUri(uri)
                        val mimeType = contentResolver.getType(uri) ?: "application/pdf"
                        val bytes = withContext(Dispatchers.IO) {
                            runCatching {
                                contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            }.getOrNull()
                        }

                        if (bytes != null) {
                            val uploadResult = withContext(Dispatchers.IO) {
                                runCatching {
                                    supabaseRepository.uploadResource(
                                        context = this@UnitDetailActivity,
                                        unitId = currentUnit.id,
                                        title = fileName.substringBeforeLast("."),
                                        category = selectedCategory,
                                        fileName = fileName,
                                        fileBytes = bytes,
                                        mimeType = mimeType
                                    )
                                }
                            }
                            if (uploadResult.isSuccess) {
                                uploadedCount++
                            } else {
                                val err = uploadResult.exceptionOrNull()?.message.orEmpty()
                                if (err.isNotEmpty()) {
                                    Toast.makeText(this@UnitDetailActivity, err, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }

                    if (uploadedCount > 0) {
                        Toast.makeText(this@UnitDetailActivity, getString(R.string.batch_upload_success, uploadedCount), Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        renderView()
                    } else {
                        Toast.makeText(this@UnitDetailActivity, getString(R.string.upload_failed), Toast.LENGTH_SHORT).show()
                        isEnabled = true
                        text = getString(R.string.upload_all, uris.size)
                    }
                }
            }
        }

        buttonsRow.addView(cancelBtn)
        buttonsRow.addView(confirmBtn)
        dialogLayout.addView(buttonsRow)

        dialog.setContentView(dialogLayout)
        dialog.show()
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "document.pdf"
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
        }
        return name
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        var size = 0L
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        }
        return size
    }

    private fun openInViewer(uri: Uri) {
        startActivity(
            Intent()
                .setClassName(this, ViewerActivity.INTERNAL_VIEWER)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    companion object {
        const val EXTRA_UNIT_ID = "extra_unit_id"
        const val EXTRA_UNIT_CODE = "extra_unit_code"
        const val EXTRA_UNIT_TITLE = "extra_unit_title"

        fun start(context: Context, unit: AcademicUnit) {
            val intent = Intent(context, UnitDetailActivity::class.java).apply {
                putExtra(EXTRA_UNIT_ID, unit.id)
                putExtra(EXTRA_UNIT_CODE, unit.code)
                putExtra(EXTRA_UNIT_TITLE, unit.title)
            }
            context.startActivity(intent)
        }
    }
}