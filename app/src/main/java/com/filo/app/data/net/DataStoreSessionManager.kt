package com.filo.app.data.net

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.filo.app.core.prefs.PrefKeys
import com.filo.app.core.prefs.filoDataStore
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Persists the anonymous session in our own DataStore rather than the SDK default, for two
 * reasons: it is the file Android auto backup is told to preserve, and it keeps every piece
 * of app state in one place.
 *
 * If she clears app data the anonymous user is gone and she is unpaired, which is why the
 * invite code stays visible in settings.
 */
class DataStoreSessionManager(private val context: Context) : SessionManager {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveSession(session: UserSession) {
        context.filoDataStore.edit { it[PrefKeys.Session] = json.encodeToString(UserSession.serializer(), session) }
    }

    override suspend fun loadSession(): UserSession? {
        val raw = context.filoDataStore.data.first()[PrefKeys.Session] ?: return null
        return runCatching { json.decodeFromString(UserSession.serializer(), raw) }.getOrNull()
    }

    override suspend fun deleteSession() {
        context.filoDataStore.edit { it.remove(PrefKeys.Session) }
    }
}
