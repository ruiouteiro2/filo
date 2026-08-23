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
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.jvm.javaio.copyTo
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
 */
object UpdateManager {

    private const val RELEASES_LATEST = "https://api.github.com/repos/ruiouteiro2/filo/releases/latest"
    private val ASSET_CODE = Regex("-vc(\\d+)\\.apk$")

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
        @SerialName("browser_download_url") val downloadUrl: String = "",
        val size: Long = 0,
    )

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy { HttpClient(OkHttp) }

    val installedVersionName: String get() = BuildConfig.VERSION_NAME

    private fun apkDir(context: Context) = File(context.cacheDir, "apk").apply { mkdirs() }

    /** Old downloads are garbage the moment the app launches as a newer version. */
    suspend fun cleanUp(context: Context) = withContext(Dispatchers.IO) {
        runCatching { apkDir(context).listFiles()?.forEach { it.delete() } }
    }

    /**
     * One unauthenticated call, budgeted: at most one live check per interval unless forced,
     * an ETag so unchanged answers are cheap, and 403/429 reads as "try later" rather than
     * "no update", because the rate limit is shared with everything behind the same NAT.
     */
    suspend fun check(context: Context, force: Boolean = false): State = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.filoDataStore
        val now = System.currentTimeMillis()
        val stored = prefs.data.first()

        if (!force) {
            val last = stored[LastCheckKey] ?: 0L
            if (now - last < CHECK_INTERVAL_MS) {
                // Answer from the cache rather than going out again.
                return@withContext evaluateCached(stored[CachedReleaseKey]).also { _state.value = it }
            }
        }

        _state.value = State.Checking
        val response = runCatching {
            http.get(RELEASES_LATEST) {
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                stored[EtagKey]?.let { header("If-None-Match", it) }
            }
        }.getOrElse {
            Log.w(TAG, "update check failed", it)
            return@withContext State.Failed(transient = true).also { s -> _state.value = s }
        }

        prefs.edit { it[LastCheckKey] = now }

        when (response.status.value) {
            // Nothing changed since last time: judge the cached release, never "no update".
            304 -> return@withContext evaluateCached(stored[CachedReleaseKey]).also { _state.value = it }
            200 -> Unit
            403, 429 -> {
                Log.w(TAG, "rate limited")
                return@withContext State.Failed(transient = true).also { s -> _state.value = s }
            }
            404 -> return@withContext State.UpToDate.also { s -> _state.value = s } // no releases yet
            else -> return@withContext State.Failed(transient = true).also { s -> _state.value = s }
        }

        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        val release = runCatching { json.decodeFromString(GhRelease.serializer(), body) }.getOrNull()
            ?: return@withContext State.Failed(transient = true).also { s -> _state.value = s }

        val apk = release.assets.firstOrNull { ASSET_CODE.containsMatchIn(it.name) }
        val code = apk?.let { ASSET_CODE.find(it.name)?.groupValues?.get(1)?.toLongOrNull() }
        if (apk == null || code == null) {
            Log.w(TAG, "latest release has no -vcN.apk asset")
            return@withContext State.UpToDate.also { s -> _state.value = s }
        }

        val info = ReleaseInfo(
            versionCode = code,
            versionName = release.tagName.removePrefix("v"),
            notes = release.body,
            downloadUrl = apk.downloadUrl,
            sizeBytes = apk.size,
        )
        prefs.edit {
            response.headers["ETag"]?.let { etag -> it[EtagKey] = etag }
            it[CachedReleaseKey] = json.encodeToString(ReleaseInfo.serializer(), info)
        }
        evaluate(info).also { _state.value = it }
    }

    private fun evaluateCached(raw: String?): State {
        val info = raw?.let { runCatching { json.decodeFromString(ReleaseInfo.serializer(), it) }.getOrNull() }
            ?: return State.UpToDate
        return evaluate(info)
    }

    private fun evaluate(info: ReleaseInfo): State =
        if (info.versionCode > BuildConfig.VERSION_CODE) State.Available(info) else State.UpToDate

    /** Streams the APK into the cache directory FileProvider is allowed to share. */
    suspend fun download(context: Context, release: ReleaseInfo): State = withContext(Dispatchers.IO) {
        _state.value = State.Downloading(release, 0f)
        val target = File(apkDir(context), "filo-vc${release.versionCode}.apk")
        val result = runCatching {
            val response = http.get(release.downloadUrl)
            target.outputStream().use { out -> response.bodyAsChannel().copyTo(out) }
            // A short download is a broken download; installing it would just error later.
            if (release.sizeBytes > 0 && target.length() != release.sizeBytes) {
                error("size mismatch: got ${target.length()}, expected ${release.sizeBytes}")
            }
            target
        }
        result.fold(
            onSuccess = { State.ReadyToInstall(release, it).also { s -> _state.value = s } },
            onFailure = {
                Log.w(TAG, "download failed", it)
                target.delete()
                State.Failed(transient = true).also { s -> _state.value = s }
            },
        )
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
