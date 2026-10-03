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

    open suspend fun getSchools(): List<School> {
        return client.from("schools").select().decodeList()
    }

    open suspend fun getProgrammes(): List<Programme> {
        return client.from("programmes").select().decodeList()
    }

    open suspend fun getProgrammesBySchool(schoolId: String): List<Programme> {
        return client.from("programmes")
            .select {
                filter {
                    eq("school_id", schoolId)
                }
            }
            .decodeList()
    }

    open suspend fun getUnits(): List<AcademicUnit> {
        return client.from("units").select().decodeList()
    }

    open suspend fun getProgrammeUnits(programmeId: String): List<ProgrammeUnit> {
        return client.from("programme_units")
            .select {
                filter {
                    eq("programme_id", programmeId)
                }
            }
            .decodeList()
    }

    open suspend fun getResourcesByUnit(unitId: String): List<Resource> {
        return client.from("resources")
            .select {
                filter {
                    eq("unit_id", unitId)
                }
            }
            .decodeList()
    }

    open suspend fun getAllResources(): List<Resource> {
        return client.from("resources").select().decodeList()
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
