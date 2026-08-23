package com.filo.app.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.filo.app.BuildConfig
import com.filo.app.core.prefs.filoDataStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom

private const val TAG = "SpotifyAuth"

/**
 * Authorization Code with PKCE. There is no client secret anywhere: a secret shipped inside a
 * sideloaded APK is not a secret, and Spotify's PKCE flow does not want one. The implicit
 * grant that used to be the easy option was removed in November 2025.
 */
object SpotifyAuth {

    private const val AUTHORIZE = "https://accounts.spotify.com/authorize"
    private const val TOKEN = "https://accounts.spotify.com/api/token"

    /**
     * Only what is needed to read what is playing. Deliberately not
     * user-modify-playback-state: tapping a track opens it in Spotify by deep link, so asking
     * to control her playback would put a scarier line in her consent screen for no benefit.
     */
    private const val SCOPES = "user-read-currently-playing"

    val isConfigured: Boolean get() = BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy { HttpClient(OkHttp) }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long = 3600,
    )

    /** Opens the consent screen in the browser and remembers the verifier for the callback. */
    suspend fun beginAuthorisation(context: Context): Intent? {
        if (!isConfigured) return null
        val verifier = randomVerifier()
        val state = randomVerifier().take(24)
        SpotifyTokenStore.savePending(context, verifier, state)

        val url = Uri.parse(AUTHORIZE).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challengeFor(verifier))
            .appendQueryParameter("state", state)
            .build()
        return Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Called by the redirect activity. Returns true when we now hold a usable token. */
    suspend fun completeAuthorisation(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val error = uri.getQueryParameter("error")
        if (error != null) {
            Log.w(TAG, "authorisation refused: $error")
            SpotifyTokenStore.clearPending(context)
            return@withContext false
        }
        val code = uri.getQueryParameter("code") ?: return@withContext false
        val state = uri.getQueryParameter("state")
        val pending = SpotifyTokenStore.pending(context)
        if (pending == null || state == null || state != pending.state) {
            Log.w(TAG, "state mismatch, refusing the callback")
            SpotifyTokenStore.clearPending(context)
            return@withContext false
        }

        val response = runCatching {
            http.submitForm(
                url = TOKEN,
                formParameters = Parameters.build {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
                    append("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                    append("code_verifier", pending.verifier)
                },
            )
        }.getOrElse {
            Log.w(TAG, "token exchange failed", it)
            return@withContext false
        }

        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        if (!response.status.isSuccess()) {
            Log.w(TAG, "token exchange rejected: ${response.status} $body")
            return@withContext false
        }
        val token = runCatching { json.decodeFromString(TokenResponse.serializer(), body) }
            .getOrElse {
                Log.w(TAG, "token response unreadable", it)
                return@withContext false
            }

        // Only now is the pending verifier spent.
        SpotifyTokenStore.saveTokens(context, token.accessToken, token.refreshToken, token.expiresIn)
        SpotifyTokenStore.clearPending(context)
        true
    }

    /**
     * A valid access token, refreshing if needed. Only a genuine invalid_grant clears the
     * stored refresh token; every other failure is transient and gets retried next poll.
     */
    suspend fun accessToken(context: Context): String? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val stored = SpotifyTokenStore.tokens(context) ?: return@withContext null
        if (System.currentTimeMillis() < stored.expiresAt - 60_000) return@withContext stored.accessToken
        val refresh = stored.refreshToken ?: return@withContext null

        val response = runCatching {
            http.submitForm(
                url = TOKEN,
                formParameters = Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refresh)
                    append("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
                },
            )
        }.getOrElse {
            Log.w(TAG, "refresh failed, keeping the token for the next try", it)
            return@withContext null
        }

        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        if (!response.status.isSuccess()) {
            if (response.status.value == 400 && "invalid_grant" in body) {
                Log.w(TAG, "refresh token revoked, disconnecting")
                SpotifyTokenStore.clear(context)
            } else {
                Log.w(TAG, "refresh rejected: ${response.status} $body")
            }
            return@withContext null
        }
        val token = runCatching { json.decodeFromString(TokenResponse.serializer(), body) }.getOrNull()
            ?: return@withContext null
        SpotifyTokenStore.saveTokens(
            context,
            token.accessToken,
            token.refreshToken ?: refresh,
            token.expiresIn,
        )
        token.accessToken
    }

    private fun randomVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun challengeFor(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}

/** Tokens live in the same DataStore as everything else, and never leave the phone. */
object SpotifyTokenStore {

    private val Access = stringPreferencesKey("spotify_access_token")
    private val Refresh = stringPreferencesKey("spotify_refresh_token")
    private val ExpiresAt = longPreferencesKey("spotify_expires_at")
    private val PendingVerifier = stringPreferencesKey("spotify_pending_verifier")
    private val PendingState = stringPreferencesKey("spotify_pending_state")

    data class Tokens(val accessToken: String, val refreshToken: String?, val expiresAt: Long)
    data class Pending(val verifier: String, val state: String)

    fun connectedFlow(context: Context) =
        context.applicationContext.filoDataStore.data.map { it[Refresh] != null }

    suspend fun tokens(context: Context): Tokens? {
        val prefs = context.applicationContext.filoDataStore.data.first()
        val access = prefs[Access] ?: return null
        return Tokens(access, prefs[Refresh], prefs[ExpiresAt] ?: 0L)
    }

    suspend fun saveTokens(context: Context, access: String, refresh: String?, expiresIn: Long) {
        context.applicationContext.filoDataStore.edit {
            it[Access] = access
            if (refresh != null) it[Refresh] = refresh
            it[ExpiresAt] = System.currentTimeMillis() + expiresIn * 1000
        }
    }

    suspend fun savePending(context: Context, verifier: String, state: String) {
        context.applicationContext.filoDataStore.edit {
            it[PendingVerifier] = verifier
            it[PendingState] = state
        }
    }

    suspend fun pending(context: Context): Pending? {
        val prefs = context.applicationContext.filoDataStore.data.first()
        val verifier = prefs[PendingVerifier] ?: return null
        val state = prefs[PendingState] ?: return null
        return Pending(verifier, state)
    }

    suspend fun clearPending(context: Context) {
        context.applicationContext.filoDataStore.edit {
            it.remove(PendingVerifier)
            it.remove(PendingState)
        }
    }

    suspend fun clear(context: Context) {
        context.applicationContext.filoDataStore.edit {
            it.remove(Access)
            it.remove(Refresh)
            it.remove(ExpiresAt)
            it.remove(PendingVerifier)
            it.remove(PendingState)
        }
    }
}
