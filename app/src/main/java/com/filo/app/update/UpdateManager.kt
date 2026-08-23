package com.filo.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.filo.app.BuildConfig
import com.filo.app.core.prefs.filoDataStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "UpdateManager"

/**
 * The app updates itself from GitHub Releases.
 *
 * There is no store between these two phones and the code, so the release pipeline is:
 * build a signed APK, publish it as a release on the public repo, and both phones offer the
 * update next time they look. Android treats a same-package, same-signature APK as an update
 * and keeps all local state.
 *
 * The version travels in the asset name as `-vcN.apk`, because a tag is for people and a
 * version code is for machines, and parsing one out of the other goes wrong eventually.
 *
 * Hard-won rules encoded below, each one a bug that actually happened or was proven reachable:
 * an unreadable release is never proof we are current; a failed check must not burn the check
 * budget or erase a known update; a downloaded APK is verified to be Filo at the promised
 * version before the installer ever sees it; and a pending download survives the process death
 * that granting the install permission causes on a first-ever update.
 */
object UpdateManager {

    private const val RELEASES_LATEST = "https://api.github.com/repos/ruiouteiro2/filo/releases/latest"
    private val ASSET_CODE = Regex("-vc(\\d+)\\.apk$")
    private val LOCAL_CODE = Regex("filo-vc(\\d+)\\.apk$")

    private val EtagKey = stringPreferencesKey("update_etag")
    private val CachedReleaseKey = stringPreferencesKey("update_cached_release")
    private val LastCheckKey = longPreferencesKey("update_last_check")

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: ReleaseInfo) : State
        data class Downloading(val release: ReleaseInfo, val progress: Float) : State
        data class ReadyToInstall(val release: ReleaseInfo, val file: File) : State
        data class Failed(val transient: Boolean) : State
    }

    @Serializable
    data class ReleaseInfo(
        val versionCode: Long,
        val versionName: String,
        val notes: String,
        val downloadUrl: String,
        val sizeBytes: Long,
    )

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tagName: String = "",
        val name: String = "",
        val body: String = "",
        val assets: List<GhAsset> = emptyList(),
    )

    @Serializable
    private data class GhAsset(
        val name: String = "",
        val label: String? = null,
        @SerialName("browser_download_url") val downloadUrl: String = "",
        val size: Long = 0,
    ) {
        /**
         * gh's "file#label" upload syntax renames only the label, not the file, and that
         * once made a release invisible to this matcher. Accept the version from either.
         */
        val versionCode: Long?
            get() = sequenceOf(name, label.orEmpty())
                .mapNotNull { ASSET_CODE.find(it)?.groupValues?.get(1)?.toLongOrNull() }
                .firstOrNull()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Whether some activity of ours is on screen. Android 10+ silently drops activity starts
     * from the background, so the auto-install after a download only fires when this is true.
     */
    @Volatile
    var appVisible: Boolean = false

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy {
        HttpClient(OkHttp) {
            // Connects fail fast; the overall budget is set per request, because a metadata
            // check and a multi-megabyte download deserve very different patience.
            install(HttpTimeout) { connectTimeoutMillis = 10_000 }
        }
    }

    val installedVersionName: String get() = BuildConfig.VERSION_NAME

    private fun apkDir(context: Context) = File(context.cacheDir, "apk").apply { mkdirs() }

    /**
     * Deletes downloads the running version has made obsolete - and only those. A file newer
     * than us is a pending update the user may already have paid the bandwidth for; granting
     * the install permission on a first-ever update kills this process, and the relaunch used
     * to delete the APK it had just downloaded. If such a file matches the cached release, the
     * ReadyToInstall state is restored so the Install button is waiting when the user returns.
     */
    suspend fun cleanUp(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            var pending: Pair<Long, File>? = null
            apkDir(context).listFiles()?.forEach { file ->
                val code = LOCAL_CODE.find(file.name)?.groupValues?.get(1)?.toLongOrNull()
                if (code == null || code <= BuildConfig.VERSION_CODE) {
                    file.delete()
                } else {
                    pending = code to file
                }
            }
            val (code, file) = pending ?: return@runCatching
            val cached = decodeCached(context.filoDataStore.data.first()[CachedReleaseKey])
            if (cached != null && cached.versionCode == code && file.length() == cached.sizeBytes) {
                _state.value = State.ReadyToInstall(cached, file)
            } else {
                file.delete()
            }
        }
    }

    /**
     * One unauthenticated call, budgeted: at most one live check per interval, an ETag so
     * unchanged answers are cheap, and 403/429 reads as "try later" rather than "no update",
     * because the rate limit is shared with everything behind the same NAT.
     *
     * The budget is only spent by answers worth keeping (a parsed 200, a 304, a clean 404).
     * Failures cost nothing, so the next launch simply tries again - a failed check must
     * never stand between these two phones and an update for six hours.
     */
    suspend fun check(context: Context, force: Boolean = false): State = withContext(Dispatchers.IO) {
        // An installer flow may be pending; a background re-check must not wipe that state.
        val current = _state.value
        if (!force && (current is State.Downloading || current is State.ReadyToInstall)) {
            return@withContext current
        }
        runCatching { checkInner(context, force) }
            .getOrElse {
                if (it is CancellationException) throw it
                // DataStore corruption or anything else unforeseen: report, never crash the caller.
                Log.w(TAG, "update check blew up", it)
                State.Failed(transient = true).also { s -> _state.value = s }
            }
    }

    private suspend fun checkInner(context: Context, force: Boolean): State {
        val prefs = context.applicationContext.filoDataStore
        val now = System.currentTimeMillis()
        val stored = prefs.data.first()
        val cached = decodeCached(stored[CachedReleaseKey])

        if (!force) {
            val last = stored[LastCheckKey] ?: 0L
            // Answer from the cache - but an empty cache is no answer, so go live for one.
            if (now - last < CHECK_INTERVAL_MS && cached != null) {
                return evaluate(cached).also { _state.value = it }
            }
        }

        _state.value = State.Checking
        val response = runCatching {
            http.get(RELEASES_LATEST) {
                timeout { requestTimeoutMillis = 20_000 }
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                stored[EtagKey]?.let { header("If-None-Match", it) }
            }
        }.getOrElse {
            if (it is CancellationException) throw it
            Log.w(TAG, "update check failed", it)
            return failedButRemember(cached)
        }

        when (response.status.value) {
            // Nothing changed since last time: judge the cached release, never "no update".
            304 -> {
                prefs.edit { it[LastCheckKey] = now }
                return (if (cached != null) evaluate(cached) else State.UpToDate)
                    .also { _state.value = it }
            }
            200 -> Unit
            403, 429 -> {
                Log.w(TAG, "rate limited")
                return failedButRemember(cached)
            }
            404 -> {
                // No releases exist: the cache is describing a world that is gone.
                prefs.edit {
                    it[LastCheckKey] = now
                    it.remove(CachedReleaseKey)
                    it.remove(EtagKey)
                }
                return State.UpToDate.also { s -> _state.value = s }
            }
            else -> return failedButRemember(cached)
        }

        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        val release = runCatching { json.decodeFromString(GhRelease.serializer(), body) }.getOrNull()
            ?: return failedButRemember(cached)

        val apk = release.assets.firstOrNull { it.versionCode != null }
        val code = apk?.versionCode
        if (apk == null || code == null) {
            // A release we cannot read is not proof we are current - do not claim it is.
            Log.w(TAG, "latest release has no -vcN.apk asset")
            return failedButRemember(cached)
        }

        val info = ReleaseInfo(
            versionCode = code,
            versionName = release.tagName.removePrefix("v"),
            notes = release.body,
            downloadUrl = apk.downloadUrl,
            sizeBytes = apk.size,
        )
        prefs.edit {
            it[LastCheckKey] = now
            response.headers["ETag"]?.let { etag -> it[EtagKey] = etag }
            it[CachedReleaseKey] = json.encodeToString(ReleaseInfo.serializer(), info)
        }
        return evaluate(info).also { _state.value = it }
    }

    /** A failed check falls back to what we already knew: a known update outranks an error. */
    private fun failedButRemember(cached: ReleaseInfo?): State {
        val state = if (cached != null && cached.versionCode > BuildConfig.VERSION_CODE) {
            State.Available(cached)
        } else {
            State.Failed(transient = true)
        }
        _state.value = state
        return state
    }

    private fun decodeCached(raw: String?): ReleaseInfo? =
        raw?.let { runCatching { json.decodeFromString(ReleaseInfo.serializer(), it) }.getOrNull() }

    private fun evaluate(info: ReleaseInfo): State =
        if (info.versionCode > BuildConfig.VERSION_CODE) State.Available(info) else State.UpToDate

    /** Streams the APK into the cache directory FileProvider is allowed to share. */
    suspend fun download(context: Context, release: ReleaseInfo): State = withContext(Dispatchers.IO) {
        _state.value = State.Downloading(release, 0f)
        val target = File(apkDir(context), "filo-vc${release.versionCode}.apk")
        val result = runCatching {
            val response = http.get(release.downloadUrl) {
                timeout { requestTimeoutMillis = 10 * 60_000 }
            }
            if (response.status.value !in 200..299) error("download got HTTP ${response.status.value}")
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(64 * 1024)
            var written = 0L
            target.outputStream().use { out ->
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    if (read > 0) {
                        out.write(buffer, 0, read)
                        written += read
                        if (release.sizeBytes > 0) {
                            _state.value = State.Downloading(
                                release,
                                (written.toFloat() / release.sizeBytes).coerceIn(0f, 1f),
                            )
                        }
                    }
                }
            }
            // A short download is a broken download; installing it would just error later.
            if (release.sizeBytes > 0 && target.length() != release.sizeBytes) {
                error("size mismatch: got ${target.length()}, expected ${release.sizeBytes}")
            }
            verifyApk(context, target, release)
            target
        }
        result.fold(
            onSuccess = { State.ReadyToInstall(release, it).also { s -> _state.value = s } },
            onFailure = {
                target.delete()
                if (it is CancellationException) throw it
                Log.w(TAG, "download failed", it)
                State.Failed(transient = true).also { s -> _state.value = s }
            },
        )
    }

    /**
     * The asset name is a promise; the APK inside is the truth. A mislabeled or swapped asset
     * would otherwise loop forever: install "succeeds", the version does not change, and the
     * same update is offered again. Refuse anything that is not Filo at the promised version.
     */
    private fun verifyApk(context: Context, file: File, release: ReleaseInfo) {
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
            ?: error("downloaded file is not a readable APK")
        if (info.packageName != BuildConfig.APPLICATION_ID) {
            error("APK is ${info.packageName}, not ${BuildConfig.APPLICATION_ID}")
        }
        val actual = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
        if (actual != release.versionCode) {
            error("APK is versionCode $actual, asset promised ${release.versionCode}")
        }
        if (actual <= BuildConfig.VERSION_CODE) {
            error("APK versionCode $actual is not newer than installed ${BuildConfig.VERSION_CODE}")
        }
    }

    /**
     * Whether this app itself may summon the installer. It is a per-app grant: the one the
     * user gave WhatsApp or the file manager when first sideloading does not carry over.
     */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Hands the APK to the system installer, which shows its own confirmation. There is no
     * callback from this route: success is observed as a higher version code next launch.
     */
    fun install(context: Context, file: File): Boolean {
        // The file may have been cleaned up or truncated since the state was minted.
        val release = (_state.value as? State.ReadyToInstall)?.release
        if (!file.exists() || (release != null && release.sizeBytes > 0 && file.length() != release.sizeBytes)) {
            Log.w(TAG, "install target missing or truncated; back to download")
            _state.value = release?.let { State.Available(it) } ?: State.Idle
            return false
        }
        val uri = FileProvider.getUriForFile(
            context,
            BuildConfig.APPLICATION_ID + ".fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            // One call. setData followed by setType nulls the other, and the resulting
            // dialog says "there was a problem parsing the package".
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }
            .onFailure { Log.w(TAG, "installer launch failed", it) }
            .getOrDefault(false)
    }

    fun dismiss() {
        _state.value = State.Idle
    }

    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
}
