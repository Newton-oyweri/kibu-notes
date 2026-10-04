package com.kibu.notes.data

import android.content.Context
import androidx.core.content.edit
import com.kibu.notes.data.models.AcademicUnit
import com.kibu.notes.data.models.Programme
import com.kibu.notes.data.models.ProgrammeUnit
import com.kibu.notes.data.models.Resource
import com.kibu.notes.data.models.School
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object LocalCache {
    private const val PREFS = "supabase_local_cache"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun saveSchools(context: Context, list: List<School>) {
        save(context, "schools", list, School.serializer())
    }

    fun loadSchools(context: Context): List<School>? {
        return load(context, "schools", School.serializer())
    }

    fun saveProgrammes(context: Context, list: List<Programme>) {
        save(context, "programmes", list, Programme.serializer())
    }

    fun loadProgrammes(context: Context): List<Programme>? {
        return load(context, "programmes", Programme.serializer())
    }

    fun saveUnits(context: Context, list: List<AcademicUnit>) {
        save(context, "units", list, AcademicUnit.serializer())
    }

    fun loadUnits(context: Context): List<AcademicUnit>? {
        return load(context, "units", AcademicUnit.serializer())
    }

    fun saveProgrammeUnits(context: Context, programmeId: String, list: List<ProgrammeUnit>) {
        save(context, "programme_units_$programmeId", list, ProgrammeUnit.serializer())
    }

    fun loadProgrammeUnits(context: Context, programmeId: String): List<ProgrammeUnit>? {
        return load(context, "programme_units_$programmeId", ProgrammeUnit.serializer())
    }

    fun saveResources(context: Context, list: List<Resource>) {
        save(context, "resources", list, Resource.serializer())
    }

    fun loadResources(context: Context): List<Resource>? {
        return load(context, "resources", Resource.serializer())
    }

    private fun <T> save(context: Context, key: String, list: List<T>, serializer: KSerializer<T>) {
        runCatching {
            val str = json.encodeToString(ListSerializer(serializer), list)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString(key, str)
            }
        }
    }

    private fun <T> load(context: Context, key: String, serializer: KSerializer<T>): List<T>? {
        return runCatching {
            val str = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(key, null) ?: return null
            json.decodeFromString(ListSerializer(serializer), str)
        }.getOrNull()
    }
}
