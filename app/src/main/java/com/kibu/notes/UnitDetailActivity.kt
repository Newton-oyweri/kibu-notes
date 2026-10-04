package com.kibu.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.kibu.notes.data.SupabaseRepository
import com.kibu.notes.data.models.AcademicUnit

class UnitDetailActivity : AppCompatActivity() {

    @VisibleForTesting
    internal var supabaseRepository: SupabaseRepository = SupabaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val unitId = intent.getStringExtra(EXTRA_UNIT_ID).orEmpty()
        val unitCode = intent.getStringExtra(EXTRA_UNIT_CODE).orEmpty()
        val unitTitle = intent.getStringExtra(EXTRA_UNIT_TITLE).orEmpty()

        val unit = AcademicUnit(id = unitId, code = unitCode, title = unitTitle)

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

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 0, 0, 64)
        }
        scrollView.addView(container)
        mainLayout.addView(scrollView)

        root.addView(mainLayout)
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        UnitDetailView.render(
            context = this,
            container = container,
            unit = unit,
            repository = supabaseRepository,
            onOpenFile = { file -> openInViewer(Uri.fromFile(file)) }
        )
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
