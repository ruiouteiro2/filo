package com.filo.app.ui

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filo.app.FiloApp
import com.filo.app.core.prefs.FiloPrefs
import com.filo.app.core.prefs.PairingState
import com.filo.app.data.FiloRepository
import com.filo.app.location.LiveLocationController
import com.filo.app.spotify.SpotifyAuth
import com.filo.app.spotify.SpotifyTokenStore
import com.filo.app.update.UpdateManager
import com.filo.app.push.PushSetup
import com.filo.app.widget.WidgetUpdater
import com.filo.app.work.writeSnapshot
import com.filo.app.data.PairError
import com.filo.app.data.PairException
import com.filo.app.core.geo.DistanceState
import com.filo.app.data.model.CoupleSnapshot
import com.filo.app.data.weather.Weather
import com.filo.app.ui.permissions.PermissionAsk
import com.filo.app.ui.permissions.hasLocationPermission
import com.filo.app.ui.permissions.hasNotificationPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

sealed interface Startup {
    data object Checking : Startup
    data object NeedsPairing : Startup
    data object Paired : Startup
}

class FiloViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: FiloRepository = (app as FiloApp).repository
    private val prefs: FiloPrefs = (app as FiloApp).prefs

    val snapshot: StateFlow<CoupleSnapshot> = repo.snapshot
    val online: StateFlow<Boolean> = repo.online

    val pairing: StateFlow<PairingState> =
        prefs.pairing.stateIn(viewModelScope, SharingStarted.Eagerly, PairingState())

    private val _startup = MutableStateFlow<Startup>(Startup.Checking)
    val startup: StateFlow<Startup> = _startup.asStateFlow()

    private val _pairError = MutableStateFlow<PairError?>(null)
    val pairError: StateFlow<PairError?> = _pairError.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** The code we just created, shown large with a share button. */
    private val _createdCode = MutableStateFlow<String?>(null)
    val createdCode: StateFlow<String?> = _createdCode.asStateFlow()

    val weather: StateFlow<Weather?> = repo.weather
    val photoUrls: StateFlow<Map<String, String>> = repo.photoUrls

    /** 12 or 24 hour clock. A per person display choice, so it lives on the phone. */
    val clock24h: StateFlow<Boolean> =
        prefs.clock24h.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setClock24h(use24: Boolean) = viewModelScope.launch {
        prefs.setClock24h(use24)
        pushToWidgets()
    }

    /**
     * Derived from the snapshot rather than assigned at call sites. Computing it imperatively
     * meant the card could show "no location yet" for a beat after a sync had already brought
     * the coordinates in, because whoever wrote the position was not the one recomputing this.
     */
    val distance: StateFlow<DistanceState> = repo.snapshot
        .map { repo.distanceState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DistanceState.Missing)

    /** Everything is asked once, in one flow, straight after pairing. */
    val onboardingDone: StateFlow<Boolean> =
        prefs.onboardingDone.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun finishOnboarding() = viewModelScope.launch {
        prefs.markOnboardingDone()
        refresh()
    }

    private val _pendingAsk = MutableStateFlow<PermissionAsk?>(null)
    val pendingAsk: StateFlow<PermissionAsk?> = _pendingAsk.asStateFlow()

    init {
        // The update machinery must never gate startup: its answer is cosmetic at this
        // moment, and a slow GitHub round trip was holding the whole app on a blank screen.
        viewModelScope.launch {
            UpdateManager.cleanUp(getApplication())
            UpdateManager.check(getApplication())
        }
        viewModelScope.launch {
            repo.ensureSignedIn()
            val state = prefs.currentPairing()
            if (state.isPaired) {
                _startup.value = Startup.Paired
                repo.loadCachedWeather()
                repo.syncEverything(readLocation = true)
                pushToWidgets()
                PushSetup.registerToken(
                    context = getApplication(),
                    onToken = { repo.setFcmToken(it) },
                    scope = viewModelScope,
                )
                repo.startRealtime()
                // A realtime change should reach the home screen widgets too, not just the app.
                repo.onRemoteChange = { viewModelScope.launch { pushToWidgets() } }
            } else {
                _startup.value = Startup.NeedsPairing
            }
        }
    }

    /** Foreground sync: presence, battery, one location read, then weather, then widgets. */
    fun refresh() = viewModelScope.launch {
        repo.loadCachedWeather()
        repo.syncEverything(readLocation = true)
        pushToWidgets()
    }

    /** After every sync the widgets get a fresh snapshot and are told to redraw. */
    private suspend fun pushToWidgets() {
        val app = getApplication<Application>()
        runCatching {
            writeSnapshot(app, repo, prefs)
            WidgetUpdater.updateAll(app)
        }
    }

    /**
     * Asks for location first, then notifications, and only ever once each. A refusal is
     * remembered so the app never nags; settings has a way back in.
     */
    /**
     * Nothing to do any more: permissions are asked once in the onboarding flow rather than
     * ambushing people from the home screen. Kept so settings can still send them back.
     */
    fun evaluatePermissions(context: android.content.Context) = viewModelScope.launch {
        _pendingAsk.value = null
    }

    fun onPermissionResolved(ask: PermissionAsk, context: android.content.Context) = viewModelScope.launch {
        when (ask) {
            PermissionAsk.Location -> prefs.markAskedLocation()
            PermissionAsk.Notifications -> prefs.markAskedNotifications()
        }
        _pendingAsk.value = null
        evaluatePermissions(context)
        repo.syncEverything(readLocation = true)
    }

    // --------------------------------------------------------------- pairing

    fun createCouple(displayName: String, locale: String) = withBusy {
        val result = repo.createCouple(displayName, locale)
        _createdCode.value = result.inviteCode
        applyLocale(locale)
        // Deliberately not Paired yet: the code screen has to be seen and shared first.
    }

    /** Called when they dismiss the invite code screen. */
    fun finishPairing() {
        _startup.value = Startup.Paired
    }

    fun joinCouple(code: String, displayName: String, locale: String) = withBusy {
        repo.joinCouple(code, displayName, locale)
        applyLocale(locale)
        _startup.value = Startup.Paired
    }

    /** Whether this phone is sharing its position continuously. */
    val liveLocationEnabled: StateFlow<Boolean> =
        LiveLocationController.enabledFlow(app)
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Turning it on must happen from a visible screen: API 31+ refuses to let an app start a
     * location foreground service from the background.
     */
    fun setLiveLocationEnabled(context: android.content.Context, enabled: Boolean) {
        if (enabled) {
            LiveLocationController.enableAndStart(context)
        } else {
            LiveLocationController.setEnabled(context, false)
            viewModelScope.launch { repo.setLocationLive(false) }
        }
    }

    /** Self update, driven from settings and surfaced on the home screen. */
    val updateState: StateFlow<UpdateManager.State> = UpdateManager.state

    fun checkForUpdate(force: Boolean = false) = viewModelScope.launch {
        UpdateManager.check(getApplication(), force = force)
    }

    fun downloadUpdate(release: UpdateManager.ReleaseInfo) = viewModelScope.launch {
        val state = UpdateManager.download(getApplication(), release)
        if (state is UpdateManager.State.ReadyToInstall) {
            val app = getApplication<Application>()
            // From the background Android drops the installer intent on the floor without a
            // word; leave ReadyToInstall standing and the Install button does the honours.
            if (UpdateManager.canInstall(app) && UpdateManager.appVisible) {
                UpdateManager.install(app, state.file)
            }
        }
    }

    fun installUpdate(context: android.content.Context, state: UpdateManager.State.ReadyToInstall) {
        if (UpdateManager.canInstall(context)) {
            UpdateManager.install(context, state.file)
        } else {
            UpdateManager.requestInstallPermission(context)
        }
    }

    /** Whether this phone has a Spotify account linked. */
    val spotifyConnected: StateFlow<Boolean> =
        SpotifyTokenStore.connectedFlow(app)
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val spotifyConfigured: Boolean get() = SpotifyAuth.isConfigured

    fun connectSpotify(context: android.content.Context) = viewModelScope.launch {
        SpotifyAuth.beginAuthorisation(context)?.let { runCatching { context.startActivity(it) } }
    }

    fun disconnectSpotify(context: android.content.Context) = viewModelScope.launch {
        SpotifyTokenStore.clear(context)
        repo.clearNowPlaying()
    }

    fun startLiveLocation() = repo.startLiveLocation()

    fun stopLiveLocation() = repo.stopLiveLocation()

    fun clearPairError() {
        _pairError.value = null
    }

    private fun withBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _pairError.value = null
            try {
                block()
            } catch (e: PairException) {
                _pairError.value = e.error
            } catch (e: Exception) {
                _pairError.value = PairError.Unknown
            } finally {
                _busy.value = false
            }
        }
    }

    fun normaliseCode(raw: String) = repo.normaliseCode(raw)

    // -------------------------------------------------------------- mutators

    fun setMood(emoji: String?, text: String?) = viewModelScope.launch { repo.setMood(emoji, text) }
    fun setNote(text: String) = viewModelScope.launch { repo.setNote(text) }
    fun setSleepWindow(start: LocalTime, end: LocalTime) = viewModelScope.launch { repo.setSleepWindow(start, end) }
    fun setDisplayName(name: String) = viewModelScope.launch { repo.setDisplayName(name) }
    fun setSinceDate(date: LocalDate) = viewModelScope.launch { repo.setSinceDate(date) }
    fun sendPing() = viewModelScope.launch { repo.sendPing() }
    fun uploadAvatar(uri: android.net.Uri) = viewModelScope.launch { repo.uploadAvatar(uri) }
    fun uploadDailyPhoto(uri: android.net.Uri) = viewModelScope.launch { repo.uploadDailyPhoto(uri) }

    fun setLocale(locale: String) = viewModelScope.launch {
        repo.setLocale(locale)
        applyLocale(locale)
    }

    fun addCountdown(labelEn: String, labelIt: String, date: LocalDate, emoji: String?, primary: Boolean) =
        viewModelScope.launch { repo.addCountdown(labelEn, labelIt, date, emoji, primary) }

    fun updateCountdown(id: String, labelEn: String, labelIt: String, date: LocalDate, emoji: String?) =
        viewModelScope.launch { repo.updateCountdown(id, labelEn, labelIt, date, emoji) }

    fun setPrimaryCountdown(id: String) = viewModelScope.launch { repo.setPrimaryCountdown(id) }
    fun deleteCountdown(id: String) = viewModelScope.launch { repo.deleteCountdown(id) }

    fun addBucketItem(text: String) = viewModelScope.launch { repo.addBucketItem(text) }
    fun setBucketDone(id: String, done: Boolean) = viewModelScope.launch { repo.setBucketDone(id, done) }
    fun deleteBucketItem(id: String) = viewModelScope.launch { repo.deleteBucketItem(id) }

    /**
     * The language follows this stored setting, not the device locale. AppCompat persists the
     * choice itself, so it survives a restart without us storing it twice.
     */
    private fun applyLocale(locale: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale))
    }
}
