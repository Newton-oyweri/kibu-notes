package com.kibu.notes.data

import com.kibu.notes.data.models.AcademicUnit
import com.kibu.notes.data.models.Programme
import com.kibu.notes.data.models.ProgrammeUnit
import com.kibu.notes.data.models.Resource
import com.kibu.notes.data.models.School
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage

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
}
