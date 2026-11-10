package com.kibu.notes

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.material.R as MaterialR
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

@Keep
@Serializable
data class GitHubAsset(
    @Keep val name: String = "",
    @Keep @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @Keep val size: Long = 0L,
    @Keep val digest: String? = null
)

@Keep
@Serializable
data class GitHubRelease(
    @Keep @SerialName("tag_name") val tagName: String = "",
    @Keep val name: String = "",
    @Keep val body: String? = null,
    @Keep @SerialName("published_at") val publishedAt: String? = null,
    @Keep val assets: List<GitHubAsset> = emptyList()
)

object UpdateEngine {

    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/Newton-oyweri/kibu-notes/releases/latest"
    private const val PREFS_NAME = "update_engine_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_time_ms"
    private const val THROTTLE_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours

    private val json = Json { ignoreUnknownKeys = true }

    fun parseVersionParts(versionStr: String): List<Int> {
        val clean = versionStr.trim().removePrefix("V").removePrefix("v")
        return clean.split(".")
            .map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }
    }

    fun isVersionNewer(installedStr: String, latestStr: String): Boolean {
        val installedParts = parseVersionParts(installedStr)
        val latestParts = parseVersionParts(latestStr)
        val maxLen = maxOf(installedParts.size, latestParts.size)
        for (i in 0 until maxLen) {
            val inst = installedParts.getOrElse(i) { 0 }
            val lat = latestParts.getOrElse(i) { 0 }
            if (lat > inst) return true
            if (lat < inst) return false
        }
        return false
    }

    suspend fun fetchLatestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        // Attempt 1: GitHub REST API
        try {
            val url = URL(GITHUB_LATEST_RELEASE_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "KibuNotes-AndroidApp")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val bodyText = conn.inputStream.use { it.bufferedReader().readText() }
                return@withContext json.decodeFromString<GitHubRelease>(bodyText)
            } else {
                val errText = conn.errorStream?.use { it.bufferedReader().readText() } ?: ""
                Log.e("UpdateEngine", "API fetch failed HTTP ${conn.responseCode}: $errText")
            }
        } catch (e: Exception) {
            Log.e("UpdateEngine", "API fetch exception: ${e.message}", e)
        }

        // Attempt 2: Fallback non-API HEAD redirect & HTML asset scraping
        try {
            Log.d("UpdateEngine", "Attempting fallback release check via GitHub HTML redirect...")
            val headUrl = URL("https://github.com/Newton-oyweri/kibu-notes/releases/latest")
            val headConn = (headUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                setRequestProperty("User-Agent", "KibuNotes-AndroidApp")
                instanceFollowRedirects = false
                connectTimeout = 10000
                readTimeout = 15000
            }

            val location = headConn.getHeaderField("Location").orEmpty()
            val tag = location.substringAfterLast("/tag/").takeIf { it.isNotEmpty() && it != location }

            if (tag.isNullOrEmpty()) {
                Log.e("UpdateEngine", "Fallback failed: Location header '$location' does not contain a tag")
                return@withContext null
            }

            val expandedUrl = "https://github.com/Newton-oyweri/kibu-notes/releases/expanded_assets/$tag"
            val expandedConn = (URL(expandedUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "KibuNotes-AndroidApp")
                connectTimeout = 10000
                readTimeout = 15000
            }

            if (expandedConn.responseCode != HttpURLConnection.HTTP_OK) {
                val errText = expandedConn.errorStream?.use { it.bufferedReader().readText() } ?: ""
                Log.e("UpdateEngine", "Fallback assets fetch failed HTTP ${expandedConn.responseCode}: $errText")
                return@withContext null
            }

            val htmlBody = expandedConn.inputStream.use { it.bufferedReader().readText() }
            val apkHrefMatch = Regex("""href="([^"]+\.apk)"""", RegexOption.IGNORE_CASE).find(htmlBody)
            val rawHref = apkHrefMatch?.groupValues?.get(1)

            if (rawHref.isNullOrEmpty()) {
                Log.e("UpdateEngine", "Fallback failed: No .apk href found in expanded assets HTML for tag $tag")
                return@withContext null
            }

            val absoluteUrl = if (rawHref.startsWith("/")) "https://github.com$rawHref" else rawHref
            val filename = absoluteUrl.substringAfterLast('/')

            Log.d("UpdateEngine", "Fallback release check succeeded: tag=$tag, apkUrl=$absoluteUrl")
            return@withContext GitHubRelease(
                tagName = tag,
                name = tag,
                assets = listOf(GitHubAsset(name = filename, browserDownloadUrl = absoluteUrl, digest = null))
            )
        } catch (e: Exception) {
            Log.e("UpdateEngine", "Fallback release fetch exception: ${e.message}", e)
            return@withContext null
        }
    }

    fun checkForUpdates(context: Context, quiet: Boolean = true) {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()

        if (quiet && (now - lastCheck < THROTTLE_INTERVAL_MS)) {
            Log.d("UpdateEngine", "Skipping automatic update check: throttled (last check was < 6h ago)")
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            if (!quiet) {
                Toast.makeText(context, context.getString(R.string.checking_for_updates), Toast.LENGTH_SHORT).show()
            }

            val release = fetchLatestRelease()
            if (release == null) {
                Log.e("UpdateEngine", "Update check failed: release is null")
                if (!quiet) {
                    Toast.makeText(context, context.getString(R.string.update_download_failed), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Save last successful check timestamp
            prefs.edit { putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()) }

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: release.assets.firstOrNull { it.browserDownloadUrl.endsWith(".apk", ignoreCase = true) }

            if (apkAsset == null) {
                Log.e("UpdateEngine", "Update check failed: no APK asset found in release ${release.tagName}")
                if (!quiet) {
                    Toast.makeText(context, context.getString(R.string.no_update_available, BuildConfig.VERSION_NAME), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val isNewer = isVersionNewer(BuildConfig.VERSION_NAME, release.tagName)

            val displayInstalled = if (BuildConfig.VERSION_NAME.startsWith("V", ignoreCase = true)) {
                BuildConfig.VERSION_NAME.uppercase()
            } else {
                "V" + BuildConfig.VERSION_NAME
            }
            val displayLatest = if (release.tagName.startsWith("V", ignoreCase = true)) {
                release.tagName.uppercase()
            } else {
                "V" + release.tagName
            }

            Log.d("UpdateEngine", "1. Tag name received from GitHub: '${release.tagName}'")
            Log.d("UpdateEngine", "2. Installed versionName: '${BuildConfig.VERSION_NAME}'")
            Log.d("UpdateEngine", "3. Is newer version available? -> $isNewer")

            if (isNewer) {
                showUpdateDialog(context, release, apkAsset, displayInstalled, displayLatest)
            } else if (!quiet) {
                Toast.makeText(context, context.getString(R.string.no_update_available, displayInstalled), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUpdateDialog(
        context: Context,
        release: GitHubRelease,
        apkAsset: GitHubAsset,
        currentVersionStr: String,
        latestVersionStr: String
    ) {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) return

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val versionText = TextView(context).apply {
            text = context.getString(R.string.version_comparison, currentVersionStr, latestVersionStr)
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyMedium)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        layout.addView(versionText)

        val releaseNotes = release.body?.trim().orEmpty()
        if (releaseNotes.isNotEmpty()) {
            val notesHeader = TextView(context).apply {
                text = context.getString(R.string.release_notes_header)
                setTextAppearance(MaterialR.style.TextAppearance_Material3_LabelLarge)
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 8)
            }
            layout.addView(notesHeader)

            val notesScrollView = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (120 * context.resources.displayMetrics.density).toInt()
                ).apply { setMargins(0, 0, 0, 16) }
            }
            val notesTextView = TextView(context).apply {
                text = releaseNotes
                setTextAppearance(MaterialR.style.TextAppearance_Material3_BodySmall)
            }
            notesScrollView.addView(notesTextView)
            layout.addView(notesScrollView)
        }

        val progressLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 16, 0, 8)
        }
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
        }
        val progressText = TextView(context).apply {
            text = context.getString(R.string.downloading_update, 0)
            setTextAppearance(MaterialR.style.TextAppearance_Material3_BodySmall)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 8, 0, 0)
        }
        progressLayout.addView(progressBar)
        progressLayout.addView(progressText)
        layout.addView(progressLayout)

        var dialog: AlertDialog? = null

        val dialogBuilder = MaterialAlertDialogBuilder(context)
            .setTitle(release.name.ifEmpty { context.getString(R.string.update_available_title) })
            .setView(layout)
            .setCancelable(true)
            .setNegativeButton(R.string.later) { d, _ -> d.dismiss() }
            .setPositiveButton(R.string.download_and_install, null)

        Log.d("UpdateEngine", "showUpdateDialog showing dialog for latest version: $latestVersionStr")
        dialog = dialogBuilder.create()
        dialog.show()

        val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveBtn.setOnClickListener {
            positiveBtn.isEnabled = false
            dialog.setCancelable(false)
            progressLayout.visibility = View.VISIBLE

            CoroutineScope(Dispatchers.Main).launch {
                val cleanDigest = apkAsset.digest?.trim()?.removePrefix("sha256:")?.takeIf {
                    it.length == 64 && it.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' }
                }

                val apkFile = downloadApk(
                    context = context,
                    downloadUrl = apkAsset.browserDownloadUrl,
                    expectedChecksum = cleanDigest,
                    onProgress = { percent ->
                        progressBar.progress = percent
                        progressText.text = context.getString(R.string.downloading_update, percent)
                    }
                )

                if (apkFile != null && apkFile.exists()) {
                    progressText.text = context.getString(R.string.installing_update)
                    dialog.dismiss()
                    installApk(context, apkFile)
                } else {
                    Toast.makeText(context, context.getString(R.string.update_download_failed), Toast.LENGTH_LONG).show()
                    positiveBtn.isEnabled = true
                    dialog.setCancelable(true)
                    progressLayout.visibility = View.GONE
                }
            }
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        expectedChecksum: String? = null,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
            val apkFile = File(updatesDir, "KibuNotes.apk")
            if (apkFile.exists()) apkFile.delete()

            var currentUrl = downloadUrl
            var conn: HttpURLConnection? = null
            var redirectCount = 0
            val maxRedirects = 5

            while (redirectCount < maxRedirects) {
                val url = URL(currentUrl)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "KibuNotes-AndroidApp")
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    connect()
                }

                val status = conn.responseCode
                Log.d("UpdateEngine", "Download connection status: $status for URL: $currentUrl")

                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308) {

                    val newUrl = conn.getHeaderField("Location")
                    if (!newUrl.isNullOrBlank()) {
                        currentUrl = newUrl
                        redirectCount++
                        conn.disconnect()
                        continue
                    }
                }
                break
            }

            val finalConn = conn ?: return@withContext null
            if (finalConn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("UpdateEngine", "Download failed with HTTP response code: ${finalConn.responseCode}")
                return@withContext null
            }

            val totalSize = finalConn.contentLength.toLong()
            val digest = MessageDigest.getInstance("SHA-256")

            finalConn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        if (totalSize > 0) {
                            val percent = ((totalRead * 100) / totalSize).toInt()
                            withContext(Dispatchers.Main) { onProgress(percent) }
                        }
                    }
                }
            }

            val cleanChecksum = expectedChecksum?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            if (cleanChecksum != null) {
                val computedHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (!computedHash.equals(cleanChecksum, ignoreCase = true)) {
                    apkFile.delete()
                    Log.e("UpdateEngine", "Checksum mismatch: expected $cleanChecksum, computed $computedHash")
                    throw SecurityException("Checksum mismatch: expected $cleanChecksum, computed $computedHash")
                }
            }

            apkFile
        } catch (e: Exception) {
            Log.e("UpdateEngine", "downloadApk failed: ${e.message}", e)
            null
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            showInstallPermissionDialog(context, apkFile)
            return
        }

        try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateEngine", "installApk failed: ${e.message}", e)
            Toast.makeText(context, "Failed to start installation: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showInstallPermissionDialog(context: Context, apkFile: File) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.allow_install_permission_title)
            .setMessage(R.string.allow_install_permission_message)
            .setPositiveButton(R.string.open_settings) { dialog, _ ->
                dialog.dismiss()
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = "package:${context.packageName}".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
            }
            .setNegativeButton(R.string.later) { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
