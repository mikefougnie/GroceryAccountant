package com.godskingdomnow.groceryaccountant.sync

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The family-app sign-in. Kept out of backups (see res/xml/backup_rules.xml). */
private val Context.sessionStore by preferencesDataStore(name = "session")

data class Session(
    val baseUrl: String = DEFAULT_BASE_URL,
    val token: String = "",
    val username: String = "",
    val displayName: String = "",
    val role: String = "",
    val familyName: String = "",
    val serverBase: String = "",      // website time of the last sync
    val lastSyncAt: Long = 0,
    val lastError: String = "",
    val lastConflicts: Long = 0,
) {
    val signedIn get() = token.isNotEmpty()
    val isParent get() = role == "parent"

    companion object {
        const val DEFAULT_BASE_URL = "https://www.godskingdomnow.com/family/"
    }
}

class SessionStore(private val context: Context) {
    private object K {
        val baseUrl = stringPreferencesKey("base_url")
        val token = stringPreferencesKey("token")
        val username = stringPreferencesKey("username")
        val displayName = stringPreferencesKey("display_name")
        val role = stringPreferencesKey("role")
        val familyName = stringPreferencesKey("family_name")
        val serverBase = stringPreferencesKey("server_base")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastError = stringPreferencesKey("last_error")
        val lastConflicts = longPreferencesKey("last_conflicts")
    }

    val session: Flow<Session> = context.sessionStore.data.map { it.toSession() }

    suspend fun get(): Session = session.first()

    suspend fun update(change: (Session) -> Session) {
        context.sessionStore.edit { p ->
            val s = change(p.toSession())
            p[K.baseUrl] = s.baseUrl
            p[K.token] = s.token
            p[K.username] = s.username
            p[K.displayName] = s.displayName
            p[K.role] = s.role
            p[K.familyName] = s.familyName
            p[K.serverBase] = s.serverBase
            p[K.lastSyncAt] = s.lastSyncAt
            p[K.lastError] = s.lastError
            p[K.lastConflicts] = s.lastConflicts
        }
    }

    private fun Preferences.toSession() = Session(
        baseUrl = this[K.baseUrl] ?: Session.DEFAULT_BASE_URL,
        token = this[K.token] ?: "",
        username = this[K.username] ?: "",
        displayName = this[K.displayName] ?: "",
        role = this[K.role] ?: "",
        familyName = this[K.familyName] ?: "",
        serverBase = this[K.serverBase] ?: "",
        lastSyncAt = this[K.lastSyncAt] ?: 0,
        lastError = this[K.lastError] ?: "",
        lastConflicts = this[K.lastConflicts] ?: 0,
    )
}
