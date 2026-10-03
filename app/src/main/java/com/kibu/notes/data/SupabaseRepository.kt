package com.kibu.notes.data

import com.kibu.notes.data.models.AcademicUnit
import com.kibu.notes.data.models.Programme
import com.kibu.notes.data.models.ProgrammeUnit
import com.kibu.notes.data.models.Resource
import com.kibu.notes.data.models.School
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage

class SupabaseRepository {

    private val client = Supabase.client

    suspend fun getSchools(): List<School> {
        return client.from("schools").select().decodeList()
    }

    suspend fun getProgrammes(): List<Programme> {
        return client.from("programmes").select().decodeList()
    }

    suspend fun getProgrammesBySchool(schoolId: String): List<Programme> {
        return client.from("programmes")
            .select {
                filter {
                    eq("school_id", schoolId)
                }
            }
            .decodeList()
    }

    suspend fun getUnits(): List<AcademicUnit> {
        return client.from("units").select().decodeList()
    }

    suspend fun getProgrammeUnits(programmeId: String): List<ProgrammeUnit> {
        return client.from("programme_units")
            .select {
                filter {
                    eq("programme_id", programmeId)
                }
            }
            .decodeList()
    }

    suspend fun getResourcesByUnit(unitId: String): List<Resource> {
        return client.from("resources")
            .select {
                filter {
                    eq("unit_id", unitId)
                }
            }
            .decodeList()
    }

    suspend fun getAllResources(): List<Resource> {
        return client.from("resources").select().decodeList()
    }

    suspend fun downloadResourceFile(bucket: String, filePath: String): ByteArray {
        return client.storage.from(bucket).downloadPublic(filePath)
    }
}
