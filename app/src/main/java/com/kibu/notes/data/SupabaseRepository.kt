package com.kibu.notes.data

import android.content.Context
import com.kibu.notes.data.models.AcademicUnit
import com.kibu.notes.data.models.Programme
import com.kibu.notes.data.models.ProgrammeUnit
import com.kibu.notes.data.models.Resource
import com.kibu.notes.data.models.School
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Insert payload that matches public.resources EXACTLY.
 * Columns left out on purpose (DB fills them): created_at, updated_at.
 * uploaded_by is nullable (FK to auth.users, on delete set null).
 * There is NO "description" column in the table, so it must never be sent.
 */
@Serializable
private data class ResourceInsert(
    val id: String,
    @SerialName("unit_id") val unitId: String,
    @SerialName("uploaded_by") val uploadedBy: String? = null,
    val title: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_path") val filePath: String,
    @SerialName("file_size") val fileSize: Long,
    @SerialName("mime_type") val mimeType: String,
    val visibility: String,
    val category: String
)

@Serializable
private data class ResourceReportInsert(
    @SerialName("document_title") val documentTitle: String,
    @SerialName("document_id") val documentId: String,
    val reason: String
)

@Serializable
private data class ResourceCategoryUpdate(
    val category: String
)

open class SupabaseRepository {

    private val client by lazy { Supabase.client }

    open suspend fun getSchools(context: Context? = null): List<School> {
        val cached = context?.let { LocalCache.loadSchools(it) }
        return try {
            val remote = client.from("schools").select().decodeList<School>()
            context?.let { LocalCache.saveSchools(it, remote) }
            remote
        } catch (e: Exception) {
            cached ?: throw e
        }
    }

    open suspend fun getProgrammes(context: Context? = null): List<Programme> {
        val cached = context?.let { LocalCache.loadProgrammes(it) }
        return try {
            val remote = client.from("programmes").select().decodeList<Programme>()
            context?.let { LocalCache.saveProgrammes(it, remote) }
            remote
        } catch (e: Exception) {
            cached ?: throw e
        }
    }

    open suspend fun getProgrammesBySchool(schoolId: String, context: Context? = null): List<Programme> {
        return try {
            client.from("programmes")
                .select {
                    filter {
                        eq("school_id", schoolId)
                    }
                }
                .decodeList()
        } catch (e: Exception) {
            getProgrammes(context).filter { it.schoolId == schoolId }
        }
    }

    open suspend fun getUnits(context: Context? = null): List<AcademicUnit> {
        val cached = context?.let { LocalCache.loadUnits(it) }
        return try {
            val remote = client.from("units").select().decodeList<AcademicUnit>()
            context?.let { LocalCache.saveUnits(it, remote) }
            remote
        } catch (e: Exception) {
            cached ?: throw e
        }
    }

    open suspend fun getProgrammeUnits(programmeId: String, context: Context? = null): List<ProgrammeUnit> {
        val cached = context?.let { LocalCache.loadProgrammeUnits(it, programmeId) }
        return try {
            val remote = client.from("programme_units")
                .select {
                    filter {
                        eq("programme_id", programmeId)
                    }
                }
                .decodeList<ProgrammeUnit>()
            context?.let { LocalCache.saveProgrammeUnits(it, programmeId, remote) }
            remote
        } catch (e: Exception) {
            cached ?: throw e
        }
    }

    /**
     * Uses the same shape as idx_resources_lookup (unit_id, visibility),
     * so the query hits the index. Falls back to cached resources when offline.
     */
    open suspend fun getResourcesByUnit(unitId: String, context: Context? = null): List<Resource> {
        val cleanUnitId = unitId.trim()
        val remote = runCatching {
            client.from("resources")
                .select {
                    filter {
                        eq("unit_id", cleanUnitId)
                        eq("visibility", "public")
                    }
                }
                .decodeList<Resource>()
        }.getOrNull()

        if (remote != null) return remote

        val cached = context?.let { LocalCache.loadResources(it) }.orEmpty()
        return cached.filter {
            it.unitId.trim().equals(cleanUnitId, ignoreCase = true) &&
                    it.visibility == "public"
        }
    }

    open suspend fun getAllResources(context: Context? = null): List<Resource> {
        val cached = context?.let { LocalCache.loadResources(it) }
        return try {
            val remote = client.from("resources").select().decodeList<Resource>()
            context?.let { LocalCache.saveResources(it, remote) }
            remote
        } catch (e: Exception) {
            cached ?: throw e
        }
    }

    open suspend fun downloadResourceFile(bucket: String, filePath: String): ByteArray {
        return client.storage.from(bucket).downloadPublic(filePath)
    }

    open fun getLocalResourceFile(context: Context, resource: Resource): File {
        val dir = File(context.getExternalFilesDir("downloaded_notes") ?: context.filesDir, "notes")
        if (!dir.exists()) dir.mkdirs()
        val safeFileName = resource.fileName.ifEmpty { "resource_${resource.id}.pdf" }
        return File(dir, "${resource.id}_$safeFileName")
    }

    open fun isResourceDownloaded(context: Context, resource: Resource): Boolean {
        val file = getLocalResourceFile(context, resource)
        return file.exists() && file.length() > 0
    }

    companion object {
        const val R2_PUBLIC_BASE_URL = CloudflareR2.PUBLIC_URL
    }

    fun resolvePublicUrl(filePath: String): String {
        val cleanPath = filePath.trim()
        if (cleanPath.startsWith("http://") || cleanPath.startsWith("https://")) {
            return cleanPath
        }
        val relative = cleanPath.removePrefix("/")
        return "$R2_PUBLIC_BASE_URL/$relative"
    }

    open suspend fun downloadResource(context: Context, resource: Resource): File {
        val file = getLocalResourceFile(context, resource)
        if (file.exists() && file.length() > 0) return file

        val targetUrl = resolvePublicUrl(resource.filePath)

        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(targetUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.use { it.readBytes() }
                } else {
                    null
                }
            }.getOrNull() ?: run {
                val relative = resource.filePath.removePrefix("http://").removePrefix("https://").removePrefix("/")
                val fallbackUrl = "${Supabase.SUPABASE_URL}/storage/v1/object/public/resources/$relative"
                val conn = URL(fallbackUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("HTTP download failed with response code ${conn.responseCode}")
                }
                conn.inputStream.use { it.readBytes() }
            }
        }

        withContext(Dispatchers.IO) {
            file.writeBytes(bytes)
        }
        return file
    }

    private fun utcNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    open suspend fun uploadResource(
        context: Context,
        unitId: String,
        title: String,
        category: String,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String
    ): Resource {
        val id = UUID.randomUUID().toString()

        // Must match the resource_category enum exactly: topics | past_papers | cats
        val safeCategory = when (category.lowercase(Locale.US).trim()) {
            "past papers", "past paper", "past_paper", "past_papers" -> "past_papers"
            "cats", "cat", "cat papers" -> "cats"
            else -> "topics"
        }
        val safeFileName = fileName.ifEmpty { "document_$id.pdf" }
        val cleanUnitId = unitId.trim()
        val objectKey = "uploads/${cleanUnitId}_${id}_$safeFileName"

        // Step 1: upload file bytes to Cloudflare R2 (fallback: Supabase Storage)
        val publicUrl = try {
            CloudflareR2.uploadFile(objectKey, fileBytes, mimeType)
        } catch (e: Exception) {
            try {
                client.storage.from("resources").upload(objectKey, fileBytes) {
                    upsert = true
                }
                // Point file_path at where the file actually landed (Supabase, not R2)
                client.storage.from("resources").publicUrl(objectKey)
            } catch (e2: Exception) {
                throw IOException("Cloudflare R2 upload failed: ${e.message}", e)
            }
        }

        val finalTitle = title.ifEmpty { safeFileName }

        // Step 2: insert row into public.resources (only real columns)
        try {
            client.from("resources").insert(
                ResourceInsert(
                    id = id,
                    unitId = cleanUnitId,
                    uploadedBy = null,
                    title = finalTitle,
                    fileName = safeFileName,
                    filePath = publicUrl,
                    fileSize = fileBytes.size.toLong(),
                    mimeType = mimeType,
                    visibility = "public",
                    category = safeCategory
                )
            )
        } catch (e: Exception) {
            throw IOException("Database record creation failed: ${e.message}", e)
        }

        val resource = Resource(
            id = id,
            unitId = cleanUnitId,
            title = finalTitle,
            category = safeCategory,
            fileName = safeFileName,
            filePath = publicUrl,
            fileSize = fileBytes.size.toLong(),
            mimeType = mimeType,
            visibility = "public",
            createdAt = utcNow()
        )

        // Step 3: local cache + disk for instant offline access
        val current = runCatching { getAllResources(context) }.getOrDefault(emptyList()).toMutableList()
        current.removeAll { it.id == resource.id }
        current.add(0, resource)
        LocalCache.saveResources(context, current)

        val file = getLocalResourceFile(context, resource)
        withContext(Dispatchers.IO) {
            file.writeBytes(fileBytes)
        }

        return resource
    }

    open suspend fun reportResource(
        documentTitle: String,
        documentId: String,
        reason: String
    ) {
        client.from("resource_reports").insert(
            ResourceReportInsert(
                documentTitle = documentTitle,
                documentId = documentId,
                reason = reason
            )
        )
    }

    open suspend fun updateResourceCategory(
        context: Context? = null,
        resourceId: String,
        newCategory: String
    ) {
        val safeCategory = when (newCategory.lowercase(Locale.US).trim()) {
            "past papers", "past paper", "past_paper", "past_papers" -> "past_papers"
            "cats", "cat", "cat papers" -> "cats"
            "shared" -> "shared"
            else -> "topics"
        }
        client.from("resources").update(
            ResourceCategoryUpdate(category = safeCategory)
        ) {
            filter {
                eq("id", resourceId)
            }
        }
        context?.let { ctx ->
            val cached = LocalCache.loadResources(ctx)?.toMutableList()
            if (cached != null) {
                val idx = cached.indexOfFirst { it.id == resourceId }
                if (idx != -1) {
                    cached[idx] = cached[idx].copy(category = safeCategory)
                    LocalCache.saveResources(ctx, cached)
                }
            }
        }
    }

}