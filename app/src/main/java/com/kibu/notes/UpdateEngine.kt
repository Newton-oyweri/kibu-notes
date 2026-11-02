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
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kibu.notes.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L,
    val digest: String? = null
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAsset> = emptyList()
)

object UpdateEngine {

    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/Newton-oyweri/kibu-notes/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    fun parseVersionNumber(versionStr: String): Double {
        val clean = versionStr.trim().removePrefix("V").removePrefix("v")
        val parts = clean.split(".")
        if (parts.size >= 2) {
            val major = parts[0].filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
            val minor = parts[1].filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
            return major + (minor / 10.0)
        }
        return clean.filter { it.isDigit() }.toDoubleOrNull() ?: 0.0
    }

    fun parseVersionCode(tagName: String): Int {
        val clean = tagName.trim().removePrefix("V").removePrefix("v")
        val digitsOnly = clean.filter { it.isDigit() }
        return digitsOnly.toIntOrNull() ?: 0
    }

    suspend fun fetchLatestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(GITHUB_LATEST_RELEASE_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "KibuNotes-AndroidApp")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val bodyText = conn.inputStream.use { it.bufferedReader().readText() }
                json.decodeFromString<GitHubRelease>(bodyText)
            } else {
                null
            }
        }.getOrNull()
    }

    fun checkForUpdates(context: Context, quiet: Boolean = true) {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) return

        CoroutineScope(Dispatchers.Main).launch {
            if (!quiet) {
                Toast.makeText(context, context.getString(R.string.checking_for_updates), Toast.LENGTH_SHORT).show()
            }

            val release = fetchLatestRelease()
            if (release == null) {
                if (!quiet) {
                    Toast.makeText(context, context.getString(R.string.update_download_failed), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: release.assets.firstOrNull { it.browserDownloadUrl.endsWith(".apk", ignoreCase = true) }

            if (apkAsset == null) {
                if (!quiet) {
                    Toast.makeText(context, context.getString(R.string.no_update_available, BuildConfig.VERSION_NAME), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val latestNum = parseVersionNumber(release.tagName)
            val installedNum = parseVersionNumber(BuildConfig.VERSION_NAME)

            val isNewer = latestNum > installedNum

            val displayInstalled = "V" + installedNum.toInt()
            val displayLatest = if (release.tagName.startsWith("V", ignoreCase = true)) {
                release.tagName.uppercase()
            } else {
                "V" + release.tagName
            }

            Log.d("UpdateEngine", "1. Raw tag_name received from GitHub: '${release.tagName}', parsed: $latestNum")
            Log.d("UpdateEngine", "2. Installed versionName: '${BuildConfig.VERSION_NAME}', parsed: $installedNum")
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
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        layout.addView(versionText)

        val releaseNotes = release.body?.trim().orEmpty()
        if (releaseNotes.isNotEmpty()) {
            val notesHeader = TextView(context).apply {
                text = context.getString(R.string.release_notes_header)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
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
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
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
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
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
        runCatching {
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
        }.onFailure { e ->
            Log.e("UpdateEngine", "downloadApk failed: ${e.message}", e)
        }.getOrNull()
    }

    fun installApk(context: Context, apkFile: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            showInstallPermissionDialog(context, apkFile)
            return
        }

        runCatching {
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { e ->
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
