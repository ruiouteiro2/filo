package com.filo.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.filo.app.ui.onboarding.PermissionsOnboarding
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.filo.app.ui.FiloViewModel
import com.filo.app.ui.Startup
import com.filo.app.ui.bucket.BucketScreen
import com.filo.app.ui.countdowns.CountdownsScreen
import com.filo.app.ui.gallery.GalleryScreen
import com.filo.app.ui.home.HomeScreen
import com.filo.app.ui.pairing.PairingScreen
import com.filo.app.ui.settings.SettingsScreen
import com.filo.app.ui.theme.FiloTheme
import com.filo.app.ui.theme.Ink
import com.filo.app.widget.WidgetUpdater
import com.filo.app.work.SyncWorker
import kotlinx.coroutines.launch

object Routes {
    const val Home = "home"
    const val Settings = "settings"
    const val Countdowns = "countdowns"
    const val Bucket = "bucket"
    const val Gallery = "gallery"
}

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FiloTheme { FiloRoot() } }
        handleSpotifyExtras(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleSpotifyExtras(intent)
    }

    /**
     * The widget's music line cannot open Spotify itself without risking a dead tap, so it
     * launches us with the track in tow and we bounce straight out through SpotifyLink,
     * which knows the app -> web fallback chain.
     */
    private fun handleSpotifyExtras(intent: android.content.Intent?) {
        intent ?: return
        val trackId = intent.getStringExtra(com.filo.app.widget.EXTRA_SPOTIFY_TRACK).orEmpty()
        val title = intent.getStringExtra(com.filo.app.widget.EXTRA_SPOTIFY_TITLE).orEmpty()
        val artist = intent.getStringExtra(com.filo.app.widget.EXTRA_SPOTIFY_ARTIST).orEmpty()
        intent.removeExtra(com.filo.app.widget.EXTRA_SPOTIFY_TRACK)
        intent.removeExtra(com.filo.app.widget.EXTRA_SPOTIFY_TITLE)
        intent.removeExtra(com.filo.app.widget.EXTRA_SPOTIFY_ARTIST)
        when {
            trackId.isNotBlank() -> com.filo.app.spotify.SpotifyLink.openTrack(this, trackId)
            title.isNotBlank() || artist.isNotBlank() ->
                com.filo.app.spotify.SpotifyLink.openSearch(this, title, artist)
        }
    }

    override fun onStart() {
        super.onStart()
        com.filo.app.update.UpdateManager.appVisible = true
        // An app update unbinds the now-playing listener without telling anyone, so every
        // launch asks for it back. Free and idempotent when it is already bound.
        com.filo.app.nowplaying.NotificationAccess.requestRebind(this)
        // Spec 8: an immediate sync whenever the app is foregrounded, plus the periodic
        // worker that keeps the widgets alive while the app is closed.
        SyncWorker.schedule(this)
        lifecycleScope.launch { WidgetUpdater.updateAll(this@MainActivity) }
    }

    override fun onStop() {
        com.filo.app.update.UpdateManager.appVisible = false
        super.onStop()
    }
}

@Composable
private fun FiloRoot() {
    val vm: FiloViewModel = viewModel()
    val startup by vm.startup.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.pairError.collectAsState()
    val createdCode by vm.createdCode.collectAsState()
    val pairing by vm.pairing.collectAsState()
    val snapshot by vm.snapshot.collectAsState()
    val online by vm.online.collectAsState()
    val weather by vm.weather.collectAsState()
    val distance by vm.distance.collectAsState()
    val pendingAsk by vm.pendingAsk.collectAsState()
    val photoUrls by vm.photoUrls.collectAsState()
    val clock24h by vm.clock24h.collectAsState()
    val liveLocationEnabled by vm.liveLocationEnabled.collectAsState()
    val spotifyConnected by vm.spotifyConnected.collectAsState()
    val onboardingDone by vm.onboardingDone.collectAsState()
    val updateState by vm.updateState.collectAsState()
    val context = LocalContext.current
    val navController = rememberNavController()

    when (startup) {
        Startup.Checking -> Box(Modifier.fillMaxSize().background(Ink))

        Startup.NeedsPairing -> PairingScreen(
            busy = busy,
            error = error,
            createdCode = createdCode,
            initialLocale = pairing.locale,
            onCreate = vm::createCouple,
            onJoin = vm::joinCouple,
            onNormaliseCode = vm::normaliseCode,
            onDismissError = vm::clearPairError,
            onDone = vm::finishPairing,
        )

        Startup.Paired -> if (!onboardingDone) {
            PermissionsOnboarding(onDone = vm::finishOnboarding)
        } else {
            // Live location lives exactly as long as this screen does.
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_START -> {
                            vm.startLiveLocation()
                            vm.startNowPlayingWatch()
                        }
                        androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                            vm.stopLiveLocation()
                            vm.stopNowPlayingWatch()
                        }
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    vm.stopLiveLocation()
                    vm.stopNowPlayingWatch()
                }
            }
            NavHost(navController = navController, startDestination = Routes.Home) {
            composable(Routes.Home) {
                HomeScreen(
                    snapshot = snapshot,
                    online = online,
                    locale = pairing.locale,
                    weather = weather,
                    distance = distance,
                    photoUrls = photoUrls,
                    clock24h = clock24h,
                    onOpenSettings = { navController.navigate(Routes.Settings) },
                    onOpenCountdowns = { navController.navigate(Routes.Countdowns) },
                    onOpenBucket = { navController.navigate(Routes.Bucket) },
                    onSetMood = vm::setMood,
                    onSetNote = vm::setNote,
                    onPickAvatar = vm::uploadAvatar,
                    onPickDailyPhoto = vm::uploadDailyPhoto,
                    onToggleBucket = vm::setBucketDone,
                    updateAvailable = updateState is com.filo.app.update.UpdateManager.State.Available ||
                        updateState is com.filo.app.update.UpdateManager.State.Downloading ||
                        updateState is com.filo.app.update.UpdateManager.State.ReadyToInstall,
                    onOpenSettingsForUpdate = { navController.navigate(Routes.Settings) },
                    onPing = vm::sendPing,
                    onRefresh = vm::refresh,
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    snapshot = snapshot,
                    pairing = pairing,
                    onBack = { navController.popBackStack() },
                    onSetName = vm::setDisplayName,
                    onSetLocale = vm::setLocale,
                    clock24h = clock24h,
                    onSetClock24h = vm::setClock24h,
                    updateState = updateState,
                    onCheckUpdate = { vm.checkForUpdate(force = true) },
                    onDownloadUpdate = vm::downloadUpdate,
                    onInstallUpdate = { st -> vm.installUpdate(context, st) },
                    liveLocationEnabled = liveLocationEnabled,
                    onSetLiveLocation = { on -> vm.setLiveLocationEnabled(context, on) },
                    spotifyConfigured = vm.spotifyConfigured,
                    spotifyConnected = spotifyConnected,
                    onConnectSpotify = { vm.connectSpotify(context) },
                    onDisconnectSpotify = { vm.disconnectSpotify(context) },
                    onSetSleepWindow = vm::setSleepWindow,
                    onSetSinceDate = vm::setSinceDate,
                    onOpenGallery = { navController.navigate(Routes.Gallery) },
                )
            }
            composable(Routes.Countdowns) {
                CountdownsScreen(
                    countdowns = snapshot.countdowns,
                    locale = pairing.locale,
                    onBack = { navController.popBackStack() },
                    onAdd = vm::addCountdown,
                    onUpdate = vm::updateCountdown,
                    onDelete = vm::deleteCountdown,
                    onSetPrimary = vm::setPrimaryCountdown,
                )
            }
            composable(Routes.Bucket) {
                BucketScreen(
                    items = snapshot.bucket,
                    onBack = { navController.popBackStack() },
                    onAdd = vm::addBucketItem,
                    onToggle = vm::setBucketDone,
                    onDelete = vm::deleteBucketItem,
                )
            }
                composable(Routes.Gallery) { GalleryScreen() }
            }
        }
    }
}
