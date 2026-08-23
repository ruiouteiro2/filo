package com.filo.app.spotify

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "SpotifyApi"

/** What one person is playing, flattened to the handful of fields the card needs. */
data class NowPlaying(
    val trackId: String?,
    val trackName: String,
    val artist: String,
    val artUrl: String?,
    val isPlaying: Boolean,
)

object SpotifyApi {

    private const val CURRENTLY_PLAYING =
        "https://api.spotify.com/v1/me/player/currently-playing?additional_types=track,episode"

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy { HttpClient(OkHttp) }

    @Serializable
    private data class Response(
        @SerialName("is_playing") val isPlaying: Boolean = false,
        val item: Item? = null,
    )

    @Serializable
    private data class Item(
        val id: String? = null,
        val name: String = "",
        val artists: List<Artist> = emptyList(),
        val album: Album? = null,
        val show: Show? = null,
    )

    @Serializable
    private data class Artist(val name: String = "")

    @Serializable
    private data class Album(val images: List<Image> = emptyList())

    @Serializable
    private data class Show(val name: String = "", val images: List<Image> = emptyList())

    @Serializable
    private data class Image(val url: String = "", val width: Int? = null)

    /**
     * Null means "we could not tell", which is different from "nothing is playing": a 204 is
     * the documented answer for nothing playing, and is returned as a not-playing result so
     * the card clears rather than going stale.
     */
    suspend fun currentlyPlaying(context: Context): NowPlaying? = withContext(Dispatchers.IO) {
        val token = SpotifyAuth.accessToken(context) ?: return@withContext null
        val response = runCatching {
            http.get(CURRENTLY_PLAYING) { header("Authorization", "Bearer $token") }
        }.getOrElse {
            Log.w(TAG, "currently-playing failed", it)
            return@withContext null
        }

        when (response.status.value) {
            // Nothing playing, or a private session.
            204 -> return@withContext NowPlaying(null, "", "", null, isPlaying = false)
            200 -> Unit
            401 -> {
                Log.w(TAG, "token rejected")
                return@withContext null
            }
            403 -> {
                // Development Mode: this account is not on the app's user list.
                Log.w(TAG, "forbidden - is this Spotify account added to the app in the dashboard?")
                return@withContext null
            }
            429 -> {
                Log.w(TAG, "rate limited")
                return@withContext null
            }
            else -> {
                Log.w(TAG, "unexpected status ${response.status}")
                return@withContext null
            }
        }

        val body = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        if (body.isBlank()) return@withContext NowPlaying(null, "", "", null, isPlaying = false)

        val parsed = runCatching { json.decodeFromString(Response.serializer(), body) }.getOrElse {
            Log.w(TAG, "could not read the response", it)
            return@withContext null
        }
        val item = parsed.item ?: return@withContext NowPlaying(null, "", "", null, isPlaying = false)

        val artist = when {
            item.artists.isNotEmpty() -> item.artists.joinToString(", ") { it.name }
            item.show != null -> item.show.name
            else -> ""
        }
        val images = item.album?.images ?: item.show?.images.orEmpty()
        // Around 300px is the middle image, which is all a 56dp thumbnail can use.
        val art = images.minByOrNull { kotlin.math.abs((it.width ?: 640) - 300) }?.url

        NowPlaying(
            trackId = item.id,
            trackName = item.name,
            artist = artist,
            artUrl = art,
            isPlaying = parsed.isPlaying,
        )
    }
}
