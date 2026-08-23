package com.filo.app.nowplaying

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState

/** What is playing on this phone, in the shape the members row stores. */
data class LocalNowPlaying(
    val trackUri: String?,
    val title: String,
    val artist: String,
    val artUrl: String?,
    val isPlaying: Boolean,
) {
    /** The bare id, which is what a deep link wants. */
    val trackId: String?
        get() = trackUri?.substringAfterLast(':')?.takeIf { it.length == 22 }
}

object NowPlayingReader {

    val SPOTIFY_PACKAGES = setOf("com.spotify.music", "com.spotify.lite")

    /** Spotify appends this to the artist when Smart Shuffle is on. */
    private const val SMART_SHUFFLE_SUFFIX = " • Smart Shuffle"

    private const val ADVERTISEMENT_KEY = "android.media.metadata.ADVERTISEMENT"

    fun read(controller: MediaController): LocalNowPlaying? {
        if (controller.packageName !in SPOTIFY_PACKAGES) return null
        val metadata = controller.metadata ?: return null

        val uri = spotifyUri(metadata)
        if (isAdvert(metadata, uri)) return null

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        if (title.isEmpty()) return null

        val artist = (
            metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ).orEmpty()
            .removeSuffix(SMART_SHUFFLE_SUFFIX)
            .trim()

        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING

        return LocalNowPlaying(
            trackUri = uri,
            title = title,
            artist = artist,
            artUrl = artUrl(metadata, uri),
            isPlaying = playing,
        )
    }

    /**
     * The exact Spotify URI, if this build of Spotify offers one.
     *
     * It is deliberately strict: only the two keys that are supposed to carry an identity are
     * allowed to yield a bare id, and anything else has to look unmistakably like a Spotify
     * URI. A loose scan over every metadata key will happily mistake an album name for an id.
     */
    private fun spotifyUri(metadata: MediaMetadata): String? {
        val candidates = listOfNotNull(
            metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI),
            metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
        )
        for (value in candidates) {
            normalise(value)?.let { return it }
            // These two keys are allowed to hold a bare base62 id.
            if (value.length == 22 && value.all { it.isLetterOrDigit() }) return "spotify:track:$value"
        }
        return null
    }

    private fun normalise(value: String): String? = when {
        value.startsWith("spotify:track:") || value.startsWith("spotify:episode:") -> value
        value.contains("open.spotify.com/track/") ->
            "spotify:track:" + value.substringAfter("open.spotify.com/track/").substringBefore('?').substringBefore('/')
        value.contains("open.spotify.com/episode/") ->
            "spotify:episode:" + value.substringAfter("open.spotify.com/episode/").substringBefore('?').substringBefore('/')
        else -> null
    }

    /**
     * Album art as a URL rather than a bitmap, so it travels in the existing column.
     *
     * Spotify hands out `spotify:image:<hash>`, which maps straight onto the CDN. The bitmap
     * the session also carries is not used: uploading it would mean a round trip through
     * storage for something already served from a public CDN.
     */
    private fun artUrl(metadata: MediaMetadata, trackUri: String?): String? {
        val candidates = listOfNotNull(
            metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
            metadata.getString(MediaMetadata.METADATA_KEY_ART_URI),
            metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
        )
        for (value in candidates) {
            if (value.startsWith("http")) return value
            if (value.startsWith("spotify:image:")) {
                return "https://i.scdn.co/image/" + value.removePrefix("spotify:image:")
            }
        }
        return null
    }

    private fun isAdvert(metadata: MediaMetadata, uri: String?): Boolean {
        if (uri?.startsWith("spotify:ad:") == true) return true
        if (runCatching { metadata.getLong(ADVERTISEMENT_KEY) }.getOrDefault(0L) == 1L) return true
        // A free tier advert usually arrives with no artist at all.
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim()
        return artist.isNullOrEmpty() && album.isNullOrEmpty()
    }
}
