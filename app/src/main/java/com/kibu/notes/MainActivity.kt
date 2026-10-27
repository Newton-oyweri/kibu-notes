package com.kibu.notes

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.kibu.notes.data.SupabaseRepository
import com.kibu.notes.data.models.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.button.MaterialButton
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.R as MaterialR
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.kibu.notes.data.models.AcademicUnit
import java.io.File
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    /**
     * What one pass over a location produced: the rows to draw, and whether this is a
     * first run and so wants the welcome block in place of the list.
     *
     * The flag is carried rather than inferred from an empty list. "This folder is empty"
     * and "nothing has ever been opened" both produce no rows and only the second one
     * replaces the screen.
     */
    private data class Crumb(val treeUri: Uri, val docId: String, val label: String)

    private val stack = ArrayDeque<Crumb>()
    private val adapter = RowAdapter()
    private lateinit var toolbar: MaterialToolbar
    private lateinit var lockup: View
    private lateinit var progress: LinearProgressIndicator
    private lateinit var list: RecyclerView
    internal lateinit var myUnitsView: View
    private lateinit var myUnitsContainer: LinearLayout
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var tabLayout: TabLayout
    lateinit var viewPager: ViewPager2
    internal val recentsAdapter = RowAdapter()
    internal val foldersAdapter = RowAdapter()
    lateinit var recentsList: RecyclerView
    lateinit var foldersList: RecyclerView
    private lateinit var selectedProgramCard: View
    private lateinit var selectedProgramText: TextView
    private lateinit var selectedPeriodText: TextView
    private lateinit var themeToggleBtn: ImageButton

    @VisibleForTesting
    internal var supabaseRepository: SupabaseRepository = SupabaseRepository()
    private var myUnitsJob: Job? = null
    private var loadedCourseKey: String? = null
    private var courseSelectorShowing = false

    /**
     * The last "Removed" toast, kept only so the next one can cancel it.
     *
     * The framework queues toasts rather than replacing them, and each one is shown for
     * its full duration. Clearing a dozen recents in a couple of seconds therefore left
     * a dozen badges to play out one after another, still appearing half a minute after
     * the last thing was removed. Cancelling the one in flight collapses a burst to a
     * single badge that goes away shortly after the reader stops.
     */
    private var removedToast: Toast? = null

    /**
     * Where the rows are built.
     *
     * Reading a granted folder is a query to another app's DocumentsProvider, and so is
     * asking a tree for its display name. Both were done inline in render(), which runs
     * on every resume and every tap, so a folder holding a few thousand files, or a
     * provider on an SD card, a USB stick or a cloud account, froze the home screen and
     * would eventually have shown up as an ANR. Play tracks ANR rate and a bad one
     * suppresses the listing, which makes this the one item on the pre-launch list that
     * could quietly cost reach.
     *
     * Single threaded on purpose: one folder is being looked at at a time, and it keeps
     * treeLabels below confined to one thread without a lock.
     */
    @androidx.annotation.VisibleForTesting
    internal var loader: java.util.concurrent.ExecutorService =
        Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /**
     * Bumped by every render. A load that finishes after another has been asked for is
     * dropped rather than drawn: tapping through three folders quickly used to be three
     * blocking reads in order, and now it is three racing ones, only the last of which
     * describes where the reader actually is.
     */
    private var renderToken = 0

    /**
     * Display names for granted trees, which cost a query each and never change while
     * the grant lasts. Read and written only on [loader], so it needs no synchronising.
     */
    private val treeLabels = mutableMapOf<String, String>()

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (stack.isNotEmpty()) {
                stack.removeLast()
                render()
            }
        }
    }

    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) openInViewer(uri)
        }

    private val openTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                render()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        toolbar = findViewById(R.id.toolbar)
        lockup = findViewById(R.id.lockup)
        themeToggleBtn = findViewById(R.id.themeToggleBtn)
        updateThemeToggleIcon()
        themeToggleBtn.setOnClickListener {
            val isDark = Settings.isAppDarkTheme(this)
            val newDark = !isDark
            Settings.setAppDarkTheme(this, newDark)
            Settings.applyAppTheme(this)
            updateThemeToggleIcon()
        }
        toolbar.setNavigationOnClickListener { backCallback.handleOnBackPressed() }
        // render() rewrites the title and navigation icon on every resume and on
        // every folder change, but never touches the menu, so inflating once here
        // survives all of it.
        toolbar.inflateMenu(R.menu.main_menu)
        // Set once, like the inflate: the installer cannot change while this process
        // lives, because any reinstall or update kills the process first.
        toolbar.menu.findItem(R.id.action_rate).isVisible = installedFromPlay()
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search -> {
                    startActivity(Intent(this, SearchActivity::class.java))
                    true
                }
                R.id.action_rate -> { openPlayListing(); true }
                R.id.action_share_app -> { shareGander(); true }
                R.id.action_select_course -> {
                    showCourseSelectorIfNeeded()
                    true
                }
                R.id.action_about -> { showAbout(); true }
                else -> false
            }
        }
        progress = findViewById(R.id.loadProgress)
        list = findViewById(R.id.list)
        // One column on a phone, which is exactly what a LinearLayoutManager did, and two
        // on a tablet, where a single column of filenames left about nine tenths of the
        // screen empty. Headers and hints label the whole list rather than a cell, so they
        // take every span.
        val columns = resources.getInteger(R.integer.home_list_columns)
        list.layoutManager = GridLayoutManager(this, columns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (adapter.isFullSpan(position)) columns else 1
            }.apply {
                // Uncached, getSpanIndex walks getSpanSize from zero for every item, so
                // laying out a large folder is quadratic in its length. Free on a phone,
                // where one column returns immediately, and not on the tablet this grid
                // exists for. submit's notifyDataSetChanged already clears the cache.
                isSpanIndexCacheEnabled = true
                isSpanGroupIndexCacheEnabled = true
            }
        }
        list.adapter = adapter

        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)

        myUnitsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            setPadding(0, 0, 0, (88 * resources.displayMetrics.density).toInt())
        }
        myUnitsView = ScrollView(this).apply {
            id = R.id.myUnitsView
            isFillViewport = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(
                myUnitsContainer,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        recentsList = RecyclerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            clipToPadding = false
            setPadding(0, 0, 0, 200)
            layoutManager = GridLayoutManager(this@MainActivity, columns).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int) =
                        if (recentsAdapter.isFullSpan(position)) columns else 1
                }.apply {
                    isSpanIndexCacheEnabled = true
                    isSpanGroupIndexCacheEnabled = true
                }
            }
            adapter = recentsAdapter
        }

        foldersList = RecyclerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            clipToPadding = false
            setPadding(0, 0, 0, 200)
            layoutManager = GridLayoutManager(this@MainActivity, columns).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int) =
                        if (foldersAdapter.isFullSpan(position)) columns else 1
                }.apply {
                    isSpanIndexCacheEnabled = true
                    isSpanGroupIndexCacheEnabled = true
                }
            }
            adapter = foldersAdapter
        }

        viewPager.adapter = HomePagerAdapter(listOf(myUnitsView, recentsList, foldersList))
        viewPager.offscreenPageLimit = 2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_my_units)
                1 -> getString(R.string.tab_recent_files)
                else -> getString(R.string.tab_folders)
            }
        }.attach()

        fab = findViewById(R.id.openFab)
        val openFile = View.OnClickListener { openDocument.launch(arrayOf("*/*")) }
        fab.setOnClickListener(openFile)

        selectedProgramCard = findViewById(R.id.selectedProgramCard)
        selectedProgramText = findViewById(R.id.selectedProgramText)
        selectedPeriodText = findViewById(R.id.selectedPeriodText)
        selectedProgramCard.isClickable = true
        selectedProgramCard.isFocusable = true
        selectedProgramCard.setOnClickListener {
            showPeriodSelectorOrCourseSelector()
        }

        restoreStack(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, backCallback)
    }





    /**
     * Keeps the reader where they were browsing across a configuration change.
     *
     * This activity is recreated on a rotation, a font size change, a theme change and a
     * multi-window resize, and [stack] is an ordinary field, so all of them used to drop
     * whoever was three folders deep straight back to the root with no way to tell why.
     * A phone is rarely rotated mid-browse and a tablet is rotated constantly, which is
     * where this was found.
     *
     * Three parallel lists rather than a Parcelable Crumb: a crumb is a URI and two
     * strings, and this needs no new type, no @Parcelize plugin, and none of the
     * getParcelableArrayList deprecation dance.
     *
     * The rows are not saved with it. They come from a provider that may have changed
     * while the activity was gone, so onResume re-reads the folder rather than restoring
     * a stale listing of it.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_TREE_URIS, ArrayList(stack.map { it.treeUri.toString() }))
        outState.putStringArrayList(STATE_DOC_IDS, ArrayList(stack.map { it.docId }))
        outState.putStringArrayList(STATE_LABELS, ArrayList(stack.map { it.label }))
    }

    private fun restoreStack(state: Bundle?) {
        val uris = state?.getStringArrayList(STATE_TREE_URIS) ?: return
        val docIds = state.getStringArrayList(STATE_DOC_IDS) ?: return
        val labels = state.getStringArrayList(STATE_LABELS) ?: return
        // Defensive: a truncated Bundle would otherwise index out of bounds, and landing
        // at the root is the same place a failure here would land anyway.
        if (uris.size != docIds.size || uris.size != labels.size) return
        uris.indices.forEach { i ->
            stack.addLast(Crumb(Uri.parse(uris[i]), docIds[i], labels[i]))
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    /**
     * Takes any tap highlight off before this screen leaves.
     *
     * A tap that opens a document starts a ripple, and the viewer covers this screen while it
     * is still fading. A back swipe then shows this screen as it was last drawn on the way out,
     * so the row that was tapped came back lit for a moment. The back arrow never showed it,
     * because it waits for this screen to draw again first.
     *
     * Here rather than on the way back in: clearing it in onStart was tried on a phone and
     * changed nothing, because the swipe shows frames drawn before onStart runs. Both lines are
     * needed. Unpressing starts the ripple's fade, and the fade is what would be drawn; the jump
     * ends it on the spot.
     *
     * The cost is that a tap which leaves the screen shows its highlight for a frame or two
     * rather than through the transition. The next screen sliding in is feedback enough.
     */
    override fun onPause() {
        super.onPause()
        window.decorView.isPressed = false
        window.decorView.jumpDrawablesToCurrentState()
    }

    private fun openInViewer(uri: Uri) {
        // By the viewer's internal name, which is what lets it trust the URI and file it
        // in Recents. See ViewerActivity.INTERNAL_VIEWER.
        startActivity(
            Intent()
                .setClassName(this, ViewerActivity.INTERNAL_VIEWER)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    /**
     * The app's only About surface. Carries the version, who made it, and the
     * permission list read back out of Android, plus the way in to the licence
     * text the bundled libraries require to travel with the binary.
     */
    private fun showAbout() {
        val view = layoutInflater.inflate(R.layout.dialog_about, null)

        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull().orEmpty()
        view.findViewById<TextView>(R.id.aboutVersion).text =
            getString(R.string.about_version, version)

        val permissions = requestedPermissions()
        val field = view.findViewById<TextView>(R.id.aboutPermissions)
        when {
            // Only when the package manager refused to answer. Printing "none"
            // for a question we could not ask would be the one dishonest thing
            // this dialog could do, so it says nothing at all instead.
            permissions == null ->
                view.findViewById<View>(R.id.aboutPermissionsCard).visibility = View.GONE
            permissions.isEmpty() -> field.setText(R.string.about_permissions_none)
            // Never expected: assembleRelease fails before a build can get here.
            // Shown rather than swallowed, because a broken promise is the thing
            // a reader of this dialog most needs to know.
            else -> {
                field.text = permissions.joinToString("\n")
                field.setTextColor(
                    MaterialColors.getColor(field, com.google.android.material.R.attr.colorError)
                )
            }
        }

        view.findViewById<View>(R.id.aboutAuthor)
            .setOnClickListener { openUrl(getString(R.string.url_author)) }
        view.findViewById<View>(R.id.aboutSource)
            .setOnClickListener { openUrl(getString(R.string.url_source)) }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_gander)
            .setView(view)
            .setPositiveButton(R.string.about_close, null)
            .show()

        view.findViewById<View>(R.id.aboutLicences).setOnClickListener {
            dialog.dismiss()
            openLicences()
        }
    }

    /**
     * What Android says this install asks for, or null if it would not say.
     *
     * androidx.core declares DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION under our
     * own package name so libraries can registerReceiver safely. It is signature
     * level, self-granted and never shown to a user, which is why the permission
     * check in build.gradle.kts allowlists it as well. Anything else carrying our
     * package prefix is ours on the same reasoning, so drop those and report what
     * is left, which is the list Android would actually confront someone with.
     */
    private fun requestedPermissions(): List<String>? = runCatching {
        packageManager
            .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .filterNot { it.startsWith("$packageName.") }
    }.getOrNull()

    /**
     * Gander shows its own licences. The asset is copied into the cache and
     * handed to the viewer as a plain path, so the bundled Markdown renderer
     * draws it and there is no second document surface to keep alive.
     *
     * Copied on every open rather than once: the cache outlives an app update,
     * and an update is exactly when the text changes. The viewer only records
     * content:// URIs in Recents, so this cannot turn up there.
     */
    private fun openLicences() {
        val file = File(cacheDir, getString(R.string.licences_file_name))
        val opened = runCatching {
            assets.open(LICENCES_ASSET).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            startActivity(
                Intent()
                    .setClassName(this, ViewerActivity.INTERNAL_VIEWER)
                    .putExtra(ViewerActivity.EXTRA_PATH, file.absolutePath)
            )
        }.isSuccess
        if (!opened) Toast.makeText(this, R.string.licences_failed, Toast.LENGTH_SHORT).show()
    }

    /**
     * Hands a URL to whichever browser the user has. Gander never fetches
     * anything itself, and without the INTERNET permission it could not.
     */
    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show() }
    }

    /**
     * Whether Google Play is this install's installer of record, the only case where Rate
     * can go anywhere: Play takes ratings from nobody else, so on a copy from GitHub or
     * F-Droid it would open a listing that cannot be rated. Asking about our own package
     * needs no permission and no <queries> entry.
     */
    private fun installedFromPlay(): Boolean = runCatching {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(packageName)
        }
        installer == PLAY_STORE
    }.getOrDefault(false)

    /**
     * Gander's listing, sent to the Play Store app by name so another store that also
     * answers market:// links cannot catch it, and to the browser if Play will not take it.
     */
    private fun openPlayListing() {
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
            .setPackage(PLAY_STORE)
        runCatching { startActivity(market) }
            .onFailure { openUrl(getString(R.string.url_play_listing, packageName)) }
    }

    /**
     * A line and a link through the system share sheet, as the viewer shares a file.
     * Gander leaves itself out of the sheet: it accepts shared text, and opening its own
     * link as a document helps nobody.
     */
    private fun shareGander() {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_text, getString(R.string.url_site)))
        val chooser = Intent.createChooser(send, getString(R.string.share_app))
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this, ViewerActivity::class.java)))
        runCatching { startActivity(chooser) }
    }

    /**
     * Redraws the screen for wherever the reader is now.
     *
     * The toolbar changes at once and the list follows, because the title is known here
     * and the rows are not: they come from a provider that may be slow. Nothing is
     * cleared in the meantime, so returning from a document leaves the old list on
     * screen until the new one is ready rather than blinking through empty.
     */
    private fun showCourseSelectorIfNeeded() {
        if (courseSelectorShowing || isFinishing || isDestroyed) return
        courseSelectorShowing = true
        CourseSelector.showDialog(this, supabaseRepository, onDismiss = {
            courseSelectorShowing = false
        }) { _ ->
            courseSelectorShowing = false
            loadedCourseKey = null
            render()
        }
    }

    private fun showPeriodSelectorOrCourseSelector() {
        val selectedCourse = Settings.getSelectedCourse(this)
        if (selectedCourse.isNullOrEmpty()) {
            showCourseSelectorIfNeeded()
        } else {
            CourseSelector.showYearSemesterDialog(
                context = this,
                repository = supabaseRepository,
                onChangeProgramRequested = { showCourseSelectorIfNeeded() }
            ) { _, _ ->
                loadedCourseKey = null
                render()
            }
        }
    }

    private fun updateThemeToggleIcon() {
        val isDark = Settings.isAppDarkTheme(this)
        themeToggleBtn.setImageResource(
            if (isDark) R.drawable.ic_theme_light else R.drawable.ic_theme_dark
        )
        themeToggleBtn.contentDescription = getString(
            if (isDark) R.string.switch_to_light_mode else R.string.switch_to_dark_mode
        )
    }

    private fun render() {
        val here = stack.lastOrNull()
        backCallback.isEnabled = here != null

        toolbar.title = here?.label.orEmpty()
        lockup.visibility = if (here == null) View.VISIBLE else View.GONE
        themeToggleBtn.visibility = if (here == null) View.VISIBLE else View.GONE
        toolbar.navigationIcon =
            if (here == null) null
            else AppCompatResources.getDrawable(this, R.drawable.ic_back)
        toolbar.navigationContentDescription = getString(R.string.back)

        val selectedCourse = Settings.getSelectedCourse(this)
        val selectedYear = Settings.getSelectedYear(this)
        val selectedSemester = Settings.getSelectedSemester(this)
        val hasCourse = selectedCourse != null

        if (here == null && !hasCourse) {
            selectedProgramCard.visibility = View.GONE
            tabLayout.visibility = View.GONE
            viewPager.visibility = View.GONE
            list.visibility = View.GONE
            fab.hide()
            showCourseSelectorIfNeeded()
            return
        }

        if (here == null) {
            selectedProgramCard.visibility = View.VISIBLE
            selectedProgramText.text = selectedCourse
            if (selectedYear != null && selectedSemester != null) {
                selectedPeriodText.text = getString(R.string.year_semester_header, selectedYear, selectedSemester)
            } else {
                selectedPeriodText.text = getString(R.string.tap_to_select_year)
            }
            tabLayout.visibility = View.VISIBLE
            viewPager.visibility = View.VISIBLE
            list.visibility = View.GONE
            fab.show()
            loadMyUnits()
        } else {
            selectedProgramCard.visibility = View.GONE
            tabLayout.visibility = View.GONE
            viewPager.visibility = View.GONE
            list.visibility = View.VISIBLE
            fab.show()
        }

        val token = ++renderToken
        // Delayed rather than shown at once. Most folders come back in a few
        // milliseconds, and a bar that appears and vanishes inside one frame reads as a
        // flicker rather than as progress.
        val announce = Runnable {
            if (token == renderToken && !isDestroyed) progress.visibility = View.VISIBLE
        }
        main.postDelayed(announce, RENDER_PROGRESS_DELAY_MS)

        loader.execute {
            // Checked here as well as after, because loader is a single thread: without
            // this, tapping into a folder and straight back out makes the second read
            // wait for the whole of the first, which on the slow provider this exists
            // for is the wait it was meant to remove.
            if (token != renderToken) return@execute
            val folderRowsList = if (here != null) folderRows(here) else null
            val recents = if (here == null) Recents.all(this) else emptyList()
            val roots = if (here == null) {
                contentResolver.persistedUriPermissions
                    .filter { it.isReadPermission && isTreeUri(it.uri) }
                    .map { it to treeLabel(it.uri) }
                    .sortedBy { (_, label) -> label.lowercase() }
            } else emptyList()

            main.post {
                main.removeCallbacks(announce)
                if (token != renderToken || isDestroyed) return@post
                progress.visibility = View.GONE
                if (here == null) {
                    val recentsRows = mutableListOf<Row>()
                    if (recents.isEmpty()) {
                        recentsRows += Row.Hint(getString(R.string.no_recents_hint))
                    } else {
                        recents.forEach { r ->
                            val (badge, color) = badgeFor(r.name, null)
                            val ext = r.name.substringAfterLast('.', "").lowercase()
                            val uri = Uri.parse(r.uri)
                            recentsRows += Row.Item(
                                badge, color, r.name,
                                DateUtils.getRelativeTimeSpanString(r.time).toString(),
                                onClick = { openInViewer(uri) },
                                onLongClick = {
                                    Recents.remove(this, r.uri)
                                    Thumbs.evict(this, r.uri)
                                    toastRemoved()
                                    render()
                                },
                                thumbUri = uri.takeIf { Thumbs.supported(FileKind.detect(ext, null), ext) },
                                thumbExt = ext
                            )
                        }
                    }
                    recentsAdapter.submit(recentsRows)

                    val foldersRows = mutableListOf<Row>()
                    if (roots.isEmpty()) foldersRows += Row.Hint(getString(R.string.no_folders_hint))
                    roots.forEach { (perm, label) ->
                        foldersRows += Row.Item(
                            "DIR", DIR_COLOR, label, null,
                            onClick = {
                                stack.addLast(
                                    Crumb(perm.uri, DocumentsContract.getTreeDocumentId(perm.uri), label)
                                )
                                render()
                            },
                            onLongClick = {
                                val dialog = MaterialAlertDialogBuilder(this)
                                    .setTitle(R.string.remove_folder_title)
                                    .setMessage(getString(R.string.remove_folder_message, label))
                                    .setPositiveButton(R.string.remove) { _, _ ->
                                        runCatching {
                                            contentResolver.releasePersistableUriPermission(
                                                perm.uri,
                                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                            )
                                        }
                                        toastRemoved()
                                        render()
                                    }
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .create()
                                dialog.show()
                                listOf<Pair<Int, Int>>(
                                    AlertDialog.BUTTON_POSITIVE to
                                        com.google.android.material.R.attr.colorError,
                                    AlertDialog.BUTTON_NEGATIVE to
                                        com.google.android.material.R.attr.colorOnSurfaceVariant
                                ).forEach { (which, attr) ->
                                    dialog.getButton(which).let {
                                        it.setTextColor(MaterialColors.getColor(it, attr))
                                    }
                                }
                            }
                        )
                    }
                    foldersRows += Row.Item("+", ADD_COLOR, getString(R.string.add_folder), null,
                        onClick = { openTree.launch(null) })
                    foldersAdapter.submit(foldersRows)
                } else {
                    adapter.submit(folderRowsList.orEmpty())
                }
            }
        }
    }

    /** Shows the removal badge, replacing any still on screen rather than queueing behind it. */
    private fun toastRemoved() {
        removedToast?.cancel()
        removedToast = Toast.makeText(this, R.string.removed, Toast.LENGTH_SHORT)
            .also { it.show() }
    }

    private fun folderRows(crumb: Crumb): List<Row> {
        val children = mutableListOf<ChildDoc>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            crumb.treeUri, crumb.docId
        )
        runCatching {
            contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    children += ChildDoc(
                        c.getString(0), c.getString(1) ?: "?", c.getString(2) ?: "",
                        c.getLong(3), c.getLong(4)
                    )
                }
            }
        }

        val (dirs, files) = orderChildren(children)

        val rows = mutableListOf<Row>()
        dirs.forEach { d ->
            rows += Row.Item("DIR", DIR_COLOR, d.name, null, onClick = {
                stack.addLast(Crumb(crumb.treeUri, d.docId, d.name))
                render()
            })
        }
        files.forEach { f ->
            val (badge, color) = badgeFor(f.name, f.mime)
            val ext = f.name.substringAfterLast('.', "").lowercase()
            val fileUri = DocumentsContract.buildDocumentUriUsingTree(crumb.treeUri, f.docId)
            val subtitle = listOfNotNull(
                Formatter.formatShortFileSize(this, f.size).takeIf { f.size > 0 },
                DateUtils.getRelativeTimeSpanString(f.modified).toString()
                    .takeIf { f.modified > 0 }
            ).joinToString(" · ").ifEmpty { null }
            rows += Row.Item(
                badge, color, f.name, subtitle,
                onClick = { openInViewer(fileUri) },
                thumbUri = fileUri.takeIf {
                    Thumbs.supported(FileKind.detect(ext, f.mime), ext)
                },
                thumbExt = ext
            )
        }
        if (rows.isEmpty()) rows += Row.Hint(getString(R.string.empty_folder))
        return rows
    }

    private fun isTreeUri(uri: Uri): Boolean =
        runCatching { DocumentsContract.getTreeDocumentId(uri) }.isSuccess &&
            uri.pathSegments.firstOrNull() == "tree"

    /** Cached: the name of a granted tree costs a query and does not change. */
    private fun treeLabel(uri: Uri): String =
        treeLabels.getOrPut(uri.toString()) { readTreeLabel(uri) }

    private fun readTreeLabel(uri: Uri): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return "Folder"
        val name = runCatching {
            contentResolver.query(
                DocumentsContract.buildDocumentUriUsingTree(uri, id),
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return name ?: id.substringAfterLast(':').ifEmpty { id }
    }

    private class HomePagerAdapter(private val views: List<View>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = views.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = views[viewType]
            (view.parent as? ViewGroup)?.removeView(view)
            view.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            return object : RecyclerView.ViewHolder(view) {}
        }
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            holder.itemView.requestLayout()
        }
        override fun getItemViewType(position: Int): Int = position
    }

    private fun loadMyUnits(forceReload: Boolean = false) {
        val container = if (::myUnitsContainer.isInitialized) myUnitsContainer else (findViewById<ViewGroup>(R.id.myUnitsContainer) ?: return)
        val selectedCourseName = Settings.getSelectedCourse(this)

        if (selectedCourseName.isNullOrEmpty()) {
            loadedCourseKey = null
            container.removeAllViews()
            val emptyLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(48, 64, 48, 64)
                addView(TextView(context).apply {
                    text = getString(R.string.no_course_selected)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                    gravity = Gravity.CENTER
                })
                addView(MaterialButton(context).apply {
                    text = getString(R.string.select_a_course)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 24 }
                    setOnClickListener {
                        showCourseSelectorIfNeeded()
                    }
                })
            }
            container.addView(emptyLayout)
            return
        }

        val selectedYear = Settings.getSelectedYear(this)
        val selectedSemester = Settings.getSelectedSemester(this)

        if (selectedYear == null || selectedSemester == null) {
            loadedCourseKey = null
            container.removeAllViews()
            val emptyLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(48, 64, 48, 64)
                addView(TextView(context).apply {
                    text = getString(R.string.no_year_selected)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                    gravity = Gravity.CENTER
                })
                addView(MaterialButton(context).apply {
                    text = getString(R.string.select_year_semester)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 24 }
                    setOnClickListener {
                        showPeriodSelectorOrCourseSelector()
                    }
                })
            }
            container.addView(emptyLayout)
            return
        }

        val currentKey = "$selectedCourseName-$selectedYear-$selectedSemester"
        if (!forceReload && loadedCourseKey == currentKey && container.childCount > 0) {
            return
        }

        progress.visibility = View.VISIBLE
        loadedCourseKey = currentKey
        container.removeAllViews()

        val loadingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 64, 0, 64)
            }
            addView(ProgressBar(context).apply { isIndeterminate = true })
            addView(TextView(context).apply {
                text = getString(R.string.loading_units_and_notes)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setPadding(0, 24, 0, 0)
            })
        }
        container.addView(loadingView)

        myUnitsJob?.cancel()
        myUnitsJob = CoroutineScope(Dispatchers.Main).launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val programmes = supabaseRepository.getProgrammes(this@MainActivity)
                    val prog = programmes.find {
                        it.title.equals(selectedCourseName, ignoreCase = true) ||
                        it.code.equals(selectedCourseName, ignoreCase = true)
                    } ?: programmes.find { it.title.contains(selectedCourseName, ignoreCase = true) }

                    if (prog == null) return@withContext null

                    val programmeUnits = supabaseRepository.getProgrammeUnits(prog.id, this@MainActivity)
                        .filter { it.academicYear == selectedYear && it.semester == selectedSemester }
                    val allUnitsMap = supabaseRepository.getUnits(this@MainActivity).associateBy { it.id.trim().lowercase(Locale.getDefault()) }
                    val allResourcesMap = supabaseRepository.getAllResources(this@MainActivity).groupBy { it.unitId.trim().lowercase(Locale.getDefault()) }

                    val grouped = programmeUnits.groupBy { Pair(it.academicYear, it.semester) }
                        .toSortedMap(compareBy({ it.first }, { it.second }))

                    val items = grouped.mapValues { (_, pus) ->
                        pus.mapNotNull { pu ->
                            val unitKey = pu.unitId.trim().lowercase(Locale.getDefault())
                            val unit = allUnitsMap[unitKey]
                            if (unit != null) {
                                val resources = allResourcesMap[unitKey].orEmpty()
                                Pair(unit, resources)
                            } else null
                        }
                    }
                    Pair(prog, items)
                }
            }.getOrNull()

            progress.visibility = View.GONE
            container.removeAllViews()

            if (result == null) {
                val errorLayout = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(48, 64, 48, 64)
                    addView(TextView(context).apply {
                        text = getString(R.string.failed_to_load_units)
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorError))
                    })
                    addView(MaterialButton(context).apply {
                        text = getString(R.string.retry)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 24 }
                        setOnClickListener { loadMyUnits(forceReload = true) }
                    })
                }
                container.addView(errorLayout)
                return@launch
            }

            val (prog, itemsGrouped) = result

            if (itemsGrouped.isEmpty()) {
                val emptyView = TextView(this@MainActivity).apply {
                    text = getString(R.string.no_units_found, prog.title)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                    gravity = Gravity.CENTER
                    setPadding(48, 64, 48, 64)
                }
                container.addView(emptyView)
                return@launch
            }

            itemsGrouped.forEach { (yearSem, unitList) ->
                val (year, sem) = yearSem

                val headerText = TextView(this@MainActivity).apply {
                    text = getString(R.string.year_semester_header, year, sem)
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge)
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary))
                    setTypeface(null, Typeface.BOLD)
                    setPadding(16, 24, 16, 12)
                }
                container.addView(headerText)

                unitList.forEach { (unit, resList) ->
                    val unitCard = MaterialCardView(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(16, 8, 16, 16)
                        }
                        cardElevation = 0f
                        strokeWidth = (1 * resources.displayMetrics.density).toInt()
                        setStrokeColor(ColorStateList.valueOf(
                            MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
                        ))
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            UnitDetailActivity.start(this@MainActivity, unit)
                        }
                    }

                    val unitLayout = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(20, 20, 20, 20)
                    }

                    val titleRow = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    val unitTitle = TextView(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        text = if (unit.code.isNotEmpty()) "${unit.code} - ${unit.title}" else unit.title
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
                    }
                    titleRow.addView(unitTitle)

                    val arrowView = TextView(this@MainActivity).apply {
                        text = "›"
                        textSize = 24f
                        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                        setPadding(12, 0, 0, 0)
                    }
                    titleRow.addView(arrowView)

                    unitLayout.addView(titleRow)

                    val countText = when (resList.size) {
                        0 -> getString(R.string.no_notes_for_unit)
                        1 -> getString(R.string.study_materials_one)
                        else -> getString(R.string.study_materials_count, resList.size)
                    }

                    val countView = TextView(this@MainActivity).apply {
                        text = countText
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                        setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                        setPadding(0, 6, 0, 0)
                    }
                    unitLayout.addView(countView)

                    unitCard.addView(unitLayout)
                    container.addView(unitCard)
                }
            }
            container.requestLayout()
            myUnitsView.requestLayout()
        }
    }

    override fun onDestroy() {
        // Anything already queued still runs to completion and the thread ends with it,
        // rather than outliving the activity it was drawing.
        loader.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val LICENCES_ASSET = "licences.md"

        /** Google Play's package: the installer Rate depends on, and the app it opens. */
        const val PLAY_STORE = "com.android.vending"

        /** How long a folder may take to read before the screen says anything about it. */
        const val RENDER_PROGRESS_DELAY_MS = 150L

        /** Where the reader had browsed to, kept across a configuration change. */
        const val STATE_TREE_URIS = "stack.treeUris"
        const val STATE_DOC_IDS = "stack.docIds"
        const val STATE_LABELS = "stack.labels"
    }
}
