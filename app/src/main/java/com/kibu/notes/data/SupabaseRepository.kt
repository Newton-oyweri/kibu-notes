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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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

    open suspend fun getResourcesByUnit(unitId: String, context: Context? = null): List<Resource> {
        val all = runCatching { getAllResources(context) }.getOrNull()
            ?: context?.let { LocalCache.loadResources(it) }.orEmpty()
        val matching = all.filter { it.unitId == unitId }
        if (matching.isNotEmpty()) return matching

        return runCatching {
            client.from("resources")
                .select {
                    filter {
                        eq("unit_id", unitId)
                    }
                }
                .decodeList<Resource>()
        }.getOrDefault(matching)
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

    open suspend fun downloadResource(context: Context, resource: Resource): File {
        val file = getLocalResourceFile(context, resource)
        if (file.exists() && file.length() > 0) return file

        val bytes = if (resource.filePath.startsWith("http://") || resource.filePath.startsWith("https://")) {
            withContext(Dispatchers.IO) {
                val conn = URL(resource.filePath).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.inputStream.use { it.readBytes() }
            }
        } else {
            downloadResourceFile("resources", resource.filePath)
        }

        withContext(Dispatchers.IO) {
            file.writeBytes(bytes)
        }
        return file
    }
}
