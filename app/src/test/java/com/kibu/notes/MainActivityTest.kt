package com.kibu.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.appbar.MaterialToolbar
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.Callable

/**
 * The home screen: recents, granted folders, and the first-run explainer.
 *
 * The folder read normally happens on a background thread, which would make
 * every assertion here a race. [directExecutor] runs it inline instead, so a
 * test asserts on a screen that has finished drawing.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FixtureProvider.install()
        Thumbs.resetForTests()
        context.getSharedPreferences("recents", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.contentResolver.persistedUriPermissions.forEach {
            context.contentResolver.releasePersistableUriPermission(
                it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    /** Runs everything submitted to it on the calling thread. */
    private fun directExecutor(): ExecutorService {
        val direct = Executor { it.run() }
        return object : ExecutorService, Executor by direct {
            override fun shutdown() = Unit
            override fun shutdownNow() = emptyList<Runnable>()
            override fun isShutdown() = false
            override fun isTerminated() = false
            override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
            override fun <T : Any?> submit(task: Callable<T>) =
                throw UnsupportedOperationException()
            override fun <T : Any?> submit(task: Runnable, result: T) =
                throw UnsupportedOperationException()
            override fun submit(task: Runnable) = throw UnsupportedOperationException()
            override fun <T : Any?> invokeAll(
                tasks: MutableCollection<out Callable<T>>
            ) = throw UnsupportedOperationException()
            override fun <T : Any?> invokeAll(
                tasks: MutableCollection<out Callable<T>>,
                timeout: Long,
                unit: TimeUnit
            ) = throw UnsupportedOperationException()
            override fun <T : Any?> invokeAny(
                tasks: MutableCollection<out Callable<T>>
            ) = throw UnsupportedOperationException()
            override fun <T : Any?> invokeAny(
                tasks: MutableCollection<out Callable<T>>,
                timeout: Long,
                unit: TimeUnit
            ) = throw UnsupportedOperationException()
        }
    }

    private fun home(state: Bundle? = null, selectCourse: Boolean = true): ActivityController<MainActivity> {
        if (selectCourse && Settings.getSelectedCourse(context) == null) {
            Settings.setSelectedCourse(context, "Bachelor of Science in Computer Science")
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.get().loader = directExecutor()
        return controller.create(state).start().resume().visible().also {
            shadowOf(context.mainLooper).idle()
        }
    }

    private fun ActivityController<MainActivity>.list(): RecyclerView =
        get().findViewById(R.id.list)

    private fun ActivityController<MainActivity>.welcome(): View =
        get().findViewById(R.id.myUnitsView)

    private fun ActivityController<MainActivity>.recentsTitles(): List<String> {
        val rv = get().recentsList
        val adapter = rv.adapter!!
        return (0 until adapter.itemCount).mapNotNull { position ->
            val holder = adapter.createViewHolder(rv, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            holder.itemView.findViewById<TextView>(R.id.title)?.text?.toString()
                ?: holder.itemView.findViewById<TextView>(R.id.hintText)?.text?.toString()
        }
    }

    private fun ActivityController<MainActivity>.foldersTitles(): List<String> {
        val rv = get().foldersList
        val adapter = rv.adapter!!
        return (0 until adapter.itemCount).mapNotNull { position ->
            val holder = adapter.createViewHolder(rv, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            holder.itemView.findViewById<TextView>(R.id.title)?.text?.toString()
                ?: holder.itemView.findViewById<TextView>(R.id.hintText)?.text?.toString()
        }
    }

    private fun ActivityController<MainActivity>.longPressFolder(title: String) {
        val rv = get().foldersList
        val adapter = rv.adapter!!
        val position = (0 until adapter.itemCount).first { position ->
            val holder = adapter.createViewHolder(rv, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            holder.itemView.findViewById<TextView>(R.id.title)?.text?.toString() == title
        }
        val holder = adapter.createViewHolder(rv, adapter.getItemViewType(position))
        adapter.bindViewHolder(holder, position)
        holder.itemView.performLongClick()
        shadowOf(context.mainLooper).idle()
    }

    private fun ActivityController<MainActivity>.longPressRecent(title: String) {
        val rv = get().recentsList
        val adapter = rv.adapter!!
        val position = (0 until adapter.itemCount).first { position ->
            val holder = adapter.createViewHolder(rv, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            holder.itemView.findViewById<TextView>(R.id.title)?.text?.toString() == title
        }
        val holder = adapter.createViewHolder(rv, adapter.getItemViewType(position))
        adapter.bindViewHolder(holder, position)
        holder.itemView.performLongClick()
        shadowOf(context.mainLooper).idle()
    }

    private fun ActivityController<MainActivity>.folderRowView(title: String): View {
        val rv = get().foldersList
        val adapter = rv.adapter!!
        val position = (0 until adapter.itemCount).first { position ->
            val holder = adapter.createViewHolder(rv, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            holder.itemView.findViewById<TextView>(R.id.title)?.text?.toString() == title
        }
        val holder = adapter.createViewHolder(rv, adapter.getItemViewType(position))
        adapter.bindViewHolder(holder, position)
        return holder.itemView
    }

    /** Every row's title, read off the adapter through a bound holder. */
    private fun ActivityController<MainActivity>.rowTitles(): List<String> {
        val rv = list()
        val adapter = rv.adapter!!
        return (0 until adapter.itemCount).mapNotNull { position ->
            val type = adapter.getItemViewType(position)
            val holder = adapter.createViewHolder(rv, type)
            adapter.bindViewHolder(holder, position)
            holder.itemView.findViewById<TextView>(R.id.title)?.text?.toString()
                ?: holder.itemView.findViewById<TextView>(R.id.headerText)?.text?.toString()
        }
    }

    private fun granted(fixture: String, name: String) {
        val uri = FixtureProvider.uriFor(fixture)
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        Recents.add(context, uri, name)
    }

    // ---------------------------------------------------------------
    // First run
    // ---------------------------------------------------------------

    @Test
    fun aFirstRunShowsTheExplainerInsteadOfAnEmptyList() {
        val controller = home(selectCourse = false)
        assertThat(controller.welcome().visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun welcomeStaysVisibleInTabWhenSelected() {
        val controller = home(selectCourse = false)
        assertThat(controller.welcome().visibility).isEqualTo(View.VISIBLE)
        Settings.setSelectedCourse(context, "Bachelor of Science in Computer Science")
        controller.resume()
        shadowOf(context.mainLooper).idle()
        assertThat(controller.welcome().visibility).isEqualTo(View.VISIBLE)
    }

    // ---------------------------------------------------------------
    // Recents
    // ---------------------------------------------------------------

    @Test
    fun recentsAreListedNewestFirst() {
        granted("six-pages.pdf", "Alder Court.pdf")
        granted("budget.xlsx", "Q3 Budget.xlsx")

        val titles = home().recentsTitles()

        assertThat(titles).containsExactly("Q3 Budget.xlsx", "Alder Court.pdf").inOrder()
    }

    @Test
    fun aRecentWhoseGrantIsGoneDropsOffTheScreen() {
        granted("six-pages.pdf", "Alder Court.pdf")
        granted("budget.xlsx", "Q3 Budget.xlsx")
        context.contentResolver.releasePersistableUriPermission(
            FixtureProvider.uriFor("budget.xlsx"), Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val titles = home().recentsTitles()

        assertThat(titles).contains("Alder Court.pdf")
        assertThat(titles).doesNotContain("Q3 Budget.xlsx")
    }

    /** Tapping a recent opens it in the viewer, with a read grant attached. */
    @Test
    fun tappingARecentOpensItInTheViewer() {
        granted("six-pages.pdf", "Alder Court.pdf")
        val controller = home()
        val rv = controller.get().recentsList
        val adapter = rv.adapter!!

        val position = (0 until adapter.itemCount).first { position ->
            val holder = adapter.createViewHolder(rv, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            holder.itemView.findViewById<TextView>(R.id.title)?.text?.toString() ==
                "Alder Court.pdf"
        }
        val holder = adapter.createViewHolder(rv, adapter.getItemViewType(position))
        adapter.bindViewHolder(holder, position)
        holder.itemView.performClick()

        val started = shadowOf(controller.get()).nextStartedActivity
        assertThat(started.component!!.className).isEqualTo(ViewerActivity.INTERNAL_VIEWER)
        assertThat(started.data.toString()).contains("six-pages.pdf")
        assertThat(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
    }

    /**
     * A back swipe shows this screen as it was last drawn on its way out, so a tap still
     * fading when a document opened came back lit. The press has to be gone by the pause;
     * clearing it on the way back in was tried on a phone and changed nothing.
     */
    @Test
    fun aTapHighlightIsGoneBeforeTheScreenLeaves() {
        granted("six-pages.pdf", "Alder Court.pdf")
        val controller = home()
        controller.get().viewPager.currentItem = 1
        shadowOf(context.mainLooper).idle()
        val rv = controller.get().recentsList
        val rows = (0 until rv.childCount).map { rv.getChildAt(it) }.filter { it.isClickable }
        assertThat(rows).isNotEmpty()
        rows.forEach { it.isPressed = true }

        controller.pause()

        assertThat(rows.filter { it.isPressed }).isEmpty()
    }

    // ---------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------

    @Test
    fun aPhoneListsInOneColumn() {
        val manager = home().get().recentsList.layoutManager as GridLayoutManager
        assertThat(manager.spanCount).isEqualTo(1)
    }

    /**
     * A tablet gets two columns of files, which is the whole of the tablet
     * layout: one integer overridden in values-sw600dp.
     */
    @Test
    @Config(qualifiers = "sw600dp")
    fun aTabletListsInTwoColumns() {
        val manager = home().get().recentsList.layoutManager as GridLayoutManager
        assertThat(manager.spanCount).isEqualTo(2)
    }

    // ---------------------------------------------------------------
    // State across a rotation
    // ---------------------------------------------------------------

    /** Grants a folder holding one subfolder and two files, and answers its tree URI. */
    private fun grantedFolder(write: Boolean = false): Uri {
        FakeDocumentsProvider.install()
            .folder(
                "root", "Documents",
                ChildDoc("sub", "Leases", MIME_DIR, 0, 0),
                ChildDoc("f1", "zeta.pdf", "application/pdf", 2048, 0),
                ChildDoc("f2", "alpha.pdf", "application/pdf", 1024, 0),
                ChildDoc("f3", ".hidden.pdf", "application/pdf", 512, 0),
            )
        val tree = FakeDocumentsProvider.treeUri()
        context.contentResolver.takePersistableUriPermission(
            tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        )
        return tree
    }

    /** Clicks the first row whose title is [title]. */
    private fun ActivityController<MainActivity>.clickRow(title: String) {
        val foldersRv = get().foldersList
        val foldersAdapter = foldersRv.adapter!!
        val foundInFolders = (0 until foldersAdapter.itemCount).any { pos ->
            val holder = foldersAdapter.createViewHolder(foldersRv, foldersAdapter.getItemViewType(pos))
            foldersAdapter.bindViewHolder(holder, pos)
            holder.itemView.findViewById<TextView>(R.id.title)?.text?.toString() == title
        }

        val rv = if (foundInFolders) foldersRv else list()
        val adapter = rv.adapter!!
        val position = (0 until adapter.itemCount).first { position ->
            val holder = adapter.createViewHolder(rv, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            holder.itemView.findViewById<TextView>(R.id.title)?.text?.toString() == title
        }
        val holder = adapter.createViewHolder(rv, adapter.getItemViewType(position))
        adapter.bindViewHolder(holder, position)
        holder.itemView.performClick()
        shadowOf(context.mainLooper).idle()
    }

    @Test
    fun aGrantedFolderIsListedUnderItsProviderGivenName() {
        grantedFolder()
        val titles = home().foldersTitles()
        assertThat(titles).contains("Documents")
    }

    /** Directories first, then files by name, and dotfiles nowhere. */
    @Test
    fun openingAFolderListsItInOrderWithDotfilesHidden() {
        grantedFolder()
        val controller = home()
        controller.clickRow("Documents")

        val titles = controller.rowTitles()

        assertThat(titles).containsAtLeast("Leases", "alpha.pdf", "zeta.pdf").inOrder()
        assertThat(titles).doesNotContain(".hidden.pdf")
    }

    /** Inside a folder the toolbar names it, and the wordmark stands down. */
    @Test
    fun theToolbarNamesTheFolderYouAreIn() {
        grantedFolder()
        val controller = home()
        val toolbar = controller.get()
            .findViewById<MaterialToolbar>(R.id.toolbar)
        assertThat(toolbar.title.isNullOrEmpty()).isTrue()

        controller.clickRow("Documents")

        assertThat(toolbar.title.toString()).isEqualTo("Documents")
        assertThat(controller.get().findViewById<View>(R.id.lockup).visibility)
            .isEqualTo(View.GONE)
    }

    @Test
    fun backComesOutOfAFolder() {
        grantedFolder()
        val controller = home()
        controller.clickRow("Documents")

        controller.get().onBackPressedDispatcher.onBackPressed()
        shadowOf(context.mainLooper).idle()

        assertThat(controller.foldersTitles()).contains("Documents")
    }

    /**
     * A tablet is rotated constantly, and losing your place three folders deep
     * on every turn is where this was found.
     */
    @Test
    fun theFolderYouAreInSurvivesARotation() {
        grantedFolder()
        val first = home()
        first.clickRow("Documents")

        val state = Bundle()
        first.saveInstanceState(state)
        assertThat(state.getStringArrayList("stack.treeUris")).hasSize(1)
        assertThat(state.getStringArrayList("stack.labels")).containsExactly("Documents")

        val second = home(state)

        val toolbar = second.get()
            .findViewById<MaterialToolbar>(R.id.toolbar)
        assertThat(toolbar.title.toString()).isEqualTo("Documents")
        assertThat(second.rowTitles()).contains("alpha.pdf")
    }

    /**
     * A truncated bundle would index out of bounds while restoring three
     * parallel lists. Landing at the root is where a failure would land anyway.
     */
    @Test
    fun aTruncatedBundleLandsAtTheRootRatherThanCrashing() {
        val damaged = Bundle().apply {
            putStringArrayList("stack.treeUris", arrayListOf("content://a/tree/1", "content://a/tree/2"))
            putStringArrayList("stack.docIds", arrayListOf("1"))
            putStringArrayList("stack.labels", arrayListOf("One", "Two"))
        }
        val controller = home(damaged)
        assertThat(controller.welcome().visibility).isEqualTo(View.VISIBLE)
    }

    // ---------------------------------------------------------------
    // Removal
    // ---------------------------------------------------------------

    /** Binds the first row whose title is [title] and answers its view. */
    private fun ActivityController<MainActivity>.rowView(title: String): View {
        val rv = list()
        val adapter = rv.adapter!!
        val position = (0 until adapter.itemCount).first { position ->
            val holder = adapter.createViewHolder(rv, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            holder.itemView.findViewById<TextView>(R.id.title)?.text?.toString() == title
        }
        val holder = adapter.createViewHolder(rv, adapter.getItemViewType(position))
        adapter.bindViewHolder(holder, position)
        return holder.itemView
    }

    private fun ActivityController<MainActivity>.longPressRow(title: String) {
        rowView(title).performLongClick()
        shadowOf(context.mainLooper).idle()
    }

    /**
     * A folder added on a build that could rename files was granted write access as well as read.
     * Removing it gave back the read half only, and the write half stayed, out of sight: the list
     * shows read grants alone, so nothing on screen could reach it again.
     */
    @Test
    fun removingAFolderGivesBackWriteAccessToo() {
        val tree = grantedFolder(write = true)
        val controller = home()
        controller.longPressFolder("Documents")

        latestDialog()!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(context.mainLooper).idle()

        assertThat(persistedUris()).doesNotContain(tree.toString())
    }

    private fun latestDialog(): AlertDialog? =
        ShadowDialog.getLatestDialog() as? AlertDialog

    private fun persistedUris(): List<String> =
        context.contentResolver.persistedUriPermissions.map { it.uri.toString() }

    /**
     * Releasing a folder grant is the one thing on this screen Android cannot undo,
     * so it is the one thing that asks first. The grant itself is what the assertion
     * is about: leaving the row drawn would prove nothing if the permission had gone.
     */
    @Test
    fun cancellingTheRemoveDialogKeepsTheFolderGrant() {
        val tree = grantedFolder()
        val controller = home()
        controller.longPressFolder("Documents")

        val dialog = latestDialog()
        assertThat(dialog).isNotNull()
        assertThat(dialog!!.isShowing).isTrue()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(context.mainLooper).idle()

        assertThat(persistedUris()).contains(tree.toString())
        assertThat(controller.foldersTitles()).contains("Documents")
    }

    @Test
    fun confirmingTheRemoveDialogReleasesTheFolderGrant() {
        val tree = grantedFolder()
        val controller = home()
        controller.longPressFolder("Documents")

        latestDialog()!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(context.mainLooper).idle()

        assertThat(persistedUris()).doesNotContain(tree.toString())
        assertThat(controller.foldersTitles()).doesNotContain("Documents")
    }

    /**
     * A recent costs one tap to open again and the list prunes itself at 25, so it
     * goes on the press alone. The asymmetry with a folder is deliberate, and this
     * pins it so nobody later tidies the two into agreeing.
     */
    @Test
    fun removingARecentAsksNothing() {
        granted("six-pages.pdf", "Alder Court.pdf")
        val controller = home()
        controller.longPressRecent("Alder Court.pdf")

        assertThat(ShadowDialog.getLatestDialog()).isNull()
        assertThat(controller.recentsTitles()).doesNotContain("Alder Court.pdf")
    }

    /**
     * Long-press is the only way to remove a row and nothing on screen says so, which
     * makes the TalkBack label the one place it is announced. A row that cannot be
     * removed must not claim the gesture: binding a listener at all sets
     * isLongClickable, which used to leave "Add a folder" offering a press that did
     * nothing.
     */
    @Test
    fun onlyRowsThatCanBeRemovedAnnounceTheGesture() {
        grantedFolder()
        val controller = home()

        val folder = controller.folderRowView("Documents")
        assertThat(folder.isLongClickable).isTrue()
        val longClick = folder.createAccessibilityNodeInfo()!!.actionList
            .first { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK.id }
        assertThat(longClick.label.toString()).isEqualTo(context.getString(R.string.remove))

        val add = controller.folderRowView(context.getString(R.string.add_folder))
        assertThat(add.isLongClickable).isFalse()
    }

    /**
     * Gander's own primary is a burnt red, so tinting only the destructive button left
     * the two within dE 4.6 of each other on the light palette: near enough to the 2.3 a
     * person can notice that the colour marked nothing and made Cancel look dangerous
     * too. Both roles are asserted, because the bug was the pair being alike rather than
     * either one being wrong.
     */
    @Test
    fun theDestructiveButtonDoesNotLookLikeTheDismissiveOne() {
        grantedFolder()
        val controller = home()
        controller.longPressFolder("Documents")
        val dialog = latestDialog()!!

        val remove = dialog.getButton(AlertDialog.BUTTON_POSITIVE).currentTextColor
        val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE).currentTextColor
        assertThat(remove).isEqualTo(ContextCompat.getColor(context, R.color.gander_error))
        assertThat(cancel)
            .isEqualTo(ContextCompat.getColor(context, R.color.gander_on_surface_variant))
        assertThat(remove).isNotEqualTo(cancel)
    }

    // ---------------------------------------------------------------
    // About
    // ---------------------------------------------------------------

    /**
     * The About screen asks Android what the app requests and prints the
     * answer, rather than printing a claim. This is the assertion that the
     * answer is still "none".
     */
    @Test
    fun aboutReportsRequestedPermissions() {
        val controller = home()
        controller.get().findViewById<MaterialToolbar>(R.id.toolbar)
            .menu.performIdentifierAction(R.id.action_about, 0)
        shadowOf(context.mainLooper).idle()

        val dialog = shadowOf(ShadowDialog.getLatestDialog()).let {
            ShadowDialog.getLatestDialog()
        }
        assertThat(dialog).isNotNull()
        val text = dialog.findViewById<TextView>(R.id.aboutPermissions).text.toString()
        assertThat(text).contains("android.permission.INTERNET")
    }
}
