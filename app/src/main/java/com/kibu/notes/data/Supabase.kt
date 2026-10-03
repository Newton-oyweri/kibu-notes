package com.kibu.notes.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object Supabase {
    const val SUPABASE_URL = "https://wsogxpswyaxthpziboez.supabase.co"
    const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_QGsnqL5QmClUpdNkYYHy5Q_0jtnDCM0"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Postgrest)
            install(Storage)
        }
    }
}
