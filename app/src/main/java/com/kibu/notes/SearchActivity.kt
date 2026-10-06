package com.kibu.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.kibu.notes.data.SupabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {

    private lateinit var etSearchQuery: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var searchProgressBar: LinearProgressIndicator
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptySubtitle: TextView

    private lateinit var searchAdapter: SearchAdapter
    private var searchJob: Job? = null

    private val supabaseRepository = SupabaseRepository()
    private val treeLabels = mutableMapOf<String, String>()

    enum class FilterType { ALL, NOTES, FILES, RECENTS }
    private var currentFilter = FilterType.ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        val root = findViewById<View>(R.id.searchRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        initViews()
        setupListeners()
    }

    private fun initViews() {
        etSearchQuery = findViewById(R.id.etSearchQuery)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        btnBack = findViewById(R.id.btnBack)
        searchProgressBar = findViewById(R.id.searchProgressBar)
        chipGroupFilter = findViewById(R.id.chipGroupFilter)
        rvSearchResults = findViewById(R.id.rvSearchResults)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle)

        searchAdapter = SearchAdapter { item -> onSearchResultClicked(item) }
        rvSearchResults.layoutManager = LinearLayoutManager(this)
        rvSearchResults.adapter = searchAdapter

        etSearchQuery.requestFocus()
        etSearchQuery.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(etSearchQuery, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnClearSearch.setOnClickListener {
            etSearchQuery.setText("")
            btnClearSearch.visibility = View.GONE
            clearResults()
        }

        etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                performSearch(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when {
                checkedIds.contains(R.id.chipNotes) -> FilterType.NOTES
                checkedIds.contains(R.id.chipFiles) -> FilterType.FILES
                checkedIds.contains(R.id.chipRecents) -> FilterType.RECENTS
                else -> FilterType.ALL
            }
            val query = etSearchQuery.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
        }
    }

    private fun clearResults() {
        searchJob?.cancel()
        searchProgressBar.visibility = View.GONE
        searchAdapter.submitList(emptyList())
        emptyStateLayout.visibility = View.VISIBLE
        tvEmptyTitle.setText(R.string.search_empty_title)
        tvEmptySubtitle.setText(R.string.search_empty_subtitle)
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        if (query.isEmpty()) {
            clearResults()
            return
        }

        searchJob = lifecycleScope.launch {
            delay(200) // Debounce input
            searchProgressBar.visibility = View.VISIBLE

            val combinedResults = mutableListOf<SearchResultItem>()

            // 1. Search Academic Units & Notes if applicable
            if (currentFilter == FilterType.ALL || currentFilter == FilterType.NOTES) {
                val notesAndUnits = searchNotesAndUnits(query)
                if (notesAndUnits.isNotEmpty()) {
                    combinedResults.add(SearchResultItem.Header(getString(R.string.search_header_notes)))
                    combinedResults.addAll(notesAndUnits)
                }
            }

            // 2. Search Recents if applicable
            if (currentFilter == FilterType.ALL || currentFilter == FilterType.RECENTS) {
                val recents = searchRecents(query)
                if (recents.isNotEmpty()) {
                    combinedResults.add(SearchResultItem.Header(getString(R.string.search_header_recents)))
                    combinedResults.addAll(recents)
                }
            }

            // 3. Search Local Folders Recursively if applicable
            if (currentFilter == FilterType.ALL || currentFilter == FilterType.FILES) {
                val localFiles = mutableListOf<SearchResultItem.LocalFileItem>()
                searchLocalFoldersRecursively(query) { localItem ->
                    localFiles.add(localItem)
                }
                if (localFiles.isNotEmpty()) {
                    combinedResults.add(SearchResultItem.Header(getString(R.string.search_header_files)))
                    combinedResults.addAll(localFiles)
                }
            }

            searchProgressBar.visibility = View.GONE

            if (combinedResults.isEmpty()) {
                emptyStateLayout.visibility = View.VISIBLE
                tvEmptyTitle.setText(R.string.search_no_results_title)
                tvEmptySubtitle.text = getString(R.string.search_no_results_subtitle, query)
                searchAdapter.submitList(emptyList())
            } else {
                emptyStateLayout.visibility = View.GONE
                searchAdapter.submitList(combinedResults)
            }
        }
    }

    private suspend fun searchNotesAndUnits(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<SearchResultItem>()

        val units = runCatching { supabaseRepository.getUnits(this@SearchActivity) }.getOrDefault(emptyList())
        val resources = runCatching { supabaseRepository.getAllResources(this@SearchActivity) }.getOrDefault(emptyList())

        val matchingUnits = units.filter { unit ->
            unit.code.contains(query, ignoreCase = true) ||
                    unit.title.contains(query, ignoreCase = true)
        }

        matchingUnits.forEach { unit ->
            val codeStr = if (unit.code.isNotEmpty()) "• ${unit.code}" else ""
            list.add(
                SearchResultItem.AcademicUnitItem(
                    unit = unit,
                    subtitle = "Academic Unit $codeStr"
                )
            )
        }

        val unitMap = units.associateBy { it.id.trim().lowercase() }

        val matchingResources = resources.filter { res ->
            res.title.contains(query, ignoreCase = true) ||
                    res.fileName.contains(query, ignoreCase = true) ||
                    res.description?.contains(query, ignoreCase = true) == true
        }

        matchingResources.forEach { resource ->
            val parentUnit = unitMap[resource.unitId.trim().lowercase()]
            list.add(
                SearchResultItem.ResourceItem(
                    resource = resource,
                    unitCode = parentUnit?.code,
                    subtitle = parentUnit?.title ?: "Study Material Note"
                )
            )
        }

        list
    }

    private fun searchRecents(query: String): List<SearchResultItem.RecentItem> {
        val allRecents = Recents.all(this)
        return allRecents.filter { it.name.contains(query, ignoreCase = true) }
            .map { entry ->
                SearchResultItem.RecentItem(
                    name = entry.name,
                    uri = entry.uri.toUri(),
                    subtitle = "Recently Opened"
                )
            }
    }

    private suspend fun searchLocalFoldersRecursively(
        query: String,
        onResultFound: (SearchResultItem.LocalFileItem) -> Unit
    ) = withContext(Dispatchers.IO) {
        val persistedPerms = contentResolver.persistedUriPermissions
            .filter { it.isReadPermission && isTreeUri(it.uri) }

        for (perm in persistedPerms) {
            if (!coroutineContext.isActive) break
            val treeUri = perm.uri
            val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: continue
            val rootName = treeLabels.getOrPut(treeUri.toString()) { readTreeLabel(treeUri) }

            val queue = ArrayDeque<Pair<String, String>>()
            queue.add(Pair(rootDocId, rootName))

            var scannedCount = 0

            while (queue.isNotEmpty() && scannedCount < 400 && coroutineContext.isActive) {
                val (currentDocId, currentPath) = queue.removeFirst()
                scannedCount++

                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )

                runCatching {
                    contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                        while (cursor.moveToNext() && coroutineContext.isActive) {
                            val docId = if (idCol >= 0) cursor.getString(idCol) else continue
                            val name = if (nameCol >= 0) cursor.getString(nameCol) else continue
                            val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else ""
                            val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR

                            val itemPath = "$currentPath > $name"

                            if (name.contains(query, ignoreCase = true)) {
                                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                                onResultFound(
                                    SearchResultItem.LocalFileItem(
                                        name = name,
                                        uri = docUri,
                                        relativePath = currentPath,
                                        isDirectory = isDir,
                                        mimeType = mime
                                    )
                                )
                            }

                            if (isDir && queue.size < 80) {
                                queue.add(Pair(docId, itemPath))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isTreeUri(uri: Uri): Boolean =
        runCatching { DocumentsContract.getTreeDocumentId(uri) }.isSuccess &&
                uri.pathSegments.firstOrNull() == "tree"

    private fun readTreeLabel(uri: Uri): String {
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return "Folder"
        val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, id)
        return runCatching {
            contentResolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: "Folder"
    }

    private fun onSearchResultClicked(item: SearchResultItem) {
        when (item) {
            is SearchResultItem.AcademicUnitItem -> {
                UnitDetailActivity.start(this, item.unit)
            }
            is SearchResultItem.ResourceItem -> {
                lifecycleScope.launch {
                    val downloaded = supabaseRepository.isResourceDownloaded(this@SearchActivity, item.resource)
                    if (downloaded) {
                        val file = supabaseRepository.getLocalResourceFile(this@SearchActivity, item.resource)
                        openInViewer(Uri.fromFile(file), item.resource.mimeType)
                    } else {
                        Toast.makeText(this@SearchActivity, getString(R.string.search_toast_downloading, item.resource.title), Toast.LENGTH_SHORT).show()
                        val downloadedFile = runCatching {
                            supabaseRepository.downloadResource(this@SearchActivity, item.resource)
                        }.getOrNull()
                        if (downloadedFile != null) {
                            openInViewer(Uri.fromFile(downloadedFile), item.resource.mimeType)
                        } else {
                            Toast.makeText(this@SearchActivity, getString(R.string.search_toast_download_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            is SearchResultItem.LocalFileItem -> {
                if (!item.isDirectory) {
                    openInViewer(item.uri, item.mimeType)
                }
            }
            is SearchResultItem.RecentItem -> {
                openInViewer(item.uri, null)
            }
            else -> {}
        }
    }

    private fun openInViewer(uri: Uri, mimeType: String?) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW)
                .setClassName(this, ViewerActivity.INTERNAL_VIEWER)
                .setDataAndType(uri, mimeType ?: contentResolver.getType(uri))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        }.onFailure {
            Toast.makeText(this, getString(R.string.search_toast_unable_to_open), Toast.LENGTH_SHORT).show()
        }
    }
}
