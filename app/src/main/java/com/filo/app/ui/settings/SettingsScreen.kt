package com.filo.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.filo.app.BuildConfig
import com.filo.app.R
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.sp
import com.filo.app.core.prefs.PairingState
import com.filo.app.nowplaying.NotificationAccess
import com.filo.app.update.UpdateManager
import com.filo.app.core.time.DayMath
import com.filo.app.core.time.PgTime
import com.filo.app.data.model.CoupleSnapshot
import com.filo.app.ui.components.CardValue
import com.filo.app.ui.components.FiloButton
import com.filo.app.ui.components.FiloCard
import com.filo.app.ui.components.FiloSecondaryButton
import com.filo.app.ui.components.FiloSegmented
import com.filo.app.ui.components.FiloTextField
import com.filo.app.ui.components.SectionLabel
import com.filo.app.ui.components.Timestamp
import com.filo.app.ui.theme.Ash
import com.filo.app.ui.theme.FiloType
import com.filo.app.ui.theme.Blood
import com.filo.app.ui.theme.Ink
import com.filo.app.ui.theme.Bone
import com.filo.app.ui.theme.Crimson
import com.filo.app.ui.theme.Surface
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun SettingsScreen(
    snapshot: CoupleSnapshot,
    pairing: PairingState,
    onBack: () -> Unit,
    onSetName: (String) -> Unit,
    onSetLocale: (String) -> Unit,
    clock24h: Boolean,
    onSetClock24h: (Boolean) -> Unit,
    updateState: UpdateManager.State,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: (UpdateManager.ReleaseInfo) -> Unit,
    onInstallUpdate: (UpdateManager.State.ReadyToInstall) -> Unit,
    liveLocationEnabled: Boolean,
    onSetLiveLocation: (Boolean) -> Unit,
    spotifyConfigured: Boolean,
    spotifyConnected: Boolean,
    onConnectSpotify: () -> Unit,
    onDisconnectSpotify: () -> Unit,
    onSetSleepWindow: (LocalTime, LocalTime) -> Unit,
    onSetSinceDate: (LocalDate) -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val me = snapshot.me
    var name by remember(me?.displayName) { mutableStateOf(me?.displayName.orEmpty()) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showLiveLocationSetup by remember { mutableStateOf(false) }

    val sleepStart = PgTime.localTime(me?.sleepStart) ?: LocalTime.of(23, 30)
    val sleepEnd = PgTime.localTime(me?.sleepEnd) ?: LocalTime.of(7, 0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.settings_title), style = FiloType.Title, color = Bone)
            Text(
                stringResource(R.string.pairing_back),
                style = FiloType.Label,
                color = Ash,
                modifier = Modifier.clickable { onBack() },
            )
        }

        FiloCard {
            SectionLabel(stringResource(R.string.settings_name))
            Spacer(Modifier.height(10.dp))
            FiloTextField(value = name, onValueChange = { name = it.take(24) }, label = stringResource(R.string.settings_name))
            Spacer(Modifier.height(10.dp))
            FiloButton(
                text = stringResource(R.string.save),
                enabled = name.isNotBlank() && name != me?.displayName,
                onClick = { onSetName(name.trim()) },
            )
        }

        FiloCard {
            SectionLabel(stringResource(R.string.settings_language))
            Spacer(Modifier.height(10.dp))
            FiloSegmented(
                options = listOf(
                    "en" to stringResource(R.string.pairing_language_en),
                    "it" to stringResource(R.string.pairing_language_it),
                ),
                selectedKey = me?.locale ?: pairing.locale,
                onSelect = onSetLocale,
            )
        }

        FiloCard {
            SectionLabel(stringResource(R.string.settings_tracking))
            Spacer(Modifier.height(8.dp))
            Timestamp(stringResource(R.string.settings_tracking_body))
            Spacer(Modifier.height(12.dp))
            FiloSegmented(
                options = listOf(
                    "off" to stringResource(R.string.settings_tracking_off),
                    "on" to stringResource(R.string.settings_tracking_on),
                ),
                selectedKey = if (liveLocationEnabled) "on" else "off",
                onSelect = { key ->
                    if (key == "on") showLiveLocationSetup = true else onSetLiveLocation(false)
                },
            )
        }

        NowPlayingCard()

        if (spotifyConfigured) {
            FiloCard {
                SectionLabel(stringResource(R.string.spotify_settings))
                Spacer(Modifier.height(8.dp))
                Timestamp(stringResource(R.string.spotify_settings_body))
                Spacer(Modifier.height(12.dp))
                if (spotifyConnected) {
                    FiloSecondaryButton(
                        text = stringResource(R.string.spotify_disconnect),
                        onClick = onDisconnectSpotify,
                    )
                } else {
                    FiloButton(
                        text = stringResource(R.string.spotify_connect),
                        onClick = onConnectSpotify,
                    )
                }
            }
        }

        FiloCard {
            SectionLabel(stringResource(R.string.settings_clock))
            Spacer(Modifier.height(10.dp))
            FiloSegmented(
                options = listOf(
                    "12" to stringResource(R.string.settings_clock_12),
                    "24" to stringResource(R.string.settings_clock_24),
                ),
                selectedKey = if (clock24h) "24" else "12",
                onSelect = { onSetClock24h(it == "24") },
            )
        }

        FiloCard {
            SectionLabel(stringResource(R.string.settings_sleep))
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.clickable { showStartPicker = true }) {
                    Timestamp(stringResource(R.string.settings_sleep_start))
                    CardValue(PgTime.formatTime(sleepStart))
                }
                Column(modifier = Modifier.clickable { showEndPicker = true }) {
                    Timestamp(stringResource(R.string.settings_sleep_end))
                    CardValue(PgTime.formatTime(sleepEnd))
                }
            }
        }

        FiloCard {
            SectionLabel(stringResource(R.string.settings_since))
            Spacer(Modifier.height(10.dp))
            val since = PgTime.localDate(snapshot.couple?.sinceDate)
            CardValue(
                text = since?.let { DayMath.formatDate(it) } ?: stringResource(R.string.days_together_unset),
                modifier = Modifier.clickable { showDatePicker = true },
            )
            if (since != null) {
                Spacer(Modifier.height(4.dp))
                Timestamp(
                    stringResource(
                        R.string.days_together,
                        DayMath.number(DayMath.daysBetween(since, LocalDate.now(ZoneId.systemDefault()))),
                    ),
                )
            }
        }

        FiloCard {
            SectionLabel(stringResource(R.string.settings_invite_code))
            Spacer(Modifier.height(8.dp))
            CardValue(pairing.inviteCode ?: snapshot.couple?.inviteCode ?: "—")
            Spacer(Modifier.height(6.dp))
            Timestamp(stringResource(R.string.settings_invite_code_body))
        }

        FiloCard {
            SectionLabel(stringResource(R.string.settings_battery_optimisation))
            Spacer(Modifier.height(8.dp))
            Timestamp(stringResource(R.string.settings_battery_optimisation_body))
            Spacer(Modifier.height(10.dp))
            val ignoring = remember {
                val pm = context.getSystemService(PowerManager::class.java)
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
            FiloButton(
                text = stringResource(R.string.settings_battery_optimisation),
                enabled = !ignoring,
                onClick = {
                    // Deliberately never nagged about on launch; it lives here and only here.
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:" + context.packageName))
                    runCatching { context.startActivity(intent) }
                },
            )
        }

        FiloCard {
            SectionLabel(stringResource(R.string.settings_permissions))
            Spacer(Modifier.height(10.dp))
            FiloButton(
                text = stringResource(R.string.permission_open_settings),
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                    runCatching { context.startActivity(intent) }
                },
            )
        }

        FiloCard {
            SectionLabel(stringResource(R.string.update_title))
            Spacer(Modifier.height(8.dp))
            Timestamp(stringResource(R.string.settings_version, com.filo.app.BuildConfig.VERSION_NAME))
            Spacer(Modifier.height(10.dp))
            when (updateState) {
                is UpdateManager.State.Checking -> Timestamp(stringResource(R.string.update_checking))
                is UpdateManager.State.UpToDate -> {
                    Timestamp(stringResource(R.string.update_none))
                    Spacer(Modifier.height(8.dp))
                    FiloSecondaryButton(text = stringResource(R.string.update_check), onClick = onCheckUpdate)
                }
                is UpdateManager.State.Available -> {
                    CardValue(stringResource(R.string.update_available, updateState.release.versionName))
                    if (updateState.release.notes.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Timestamp(updateState.release.notes.take(280))
                    }
                    Spacer(Modifier.height(10.dp))
                    FiloButton(
                        text = stringResource(R.string.update_download),
                        onClick = { onDownloadUpdate(updateState.release) },
                    )
                }
                is UpdateManager.State.Downloading -> Timestamp(stringResource(R.string.update_downloading))
                is UpdateManager.State.ReadyToInstall -> FiloButton(
                    text = stringResource(R.string.update_install),
                    onClick = { onInstallUpdate(updateState) },
                )
                is UpdateManager.State.Failed -> {
                    Timestamp(stringResource(R.string.update_failed))
                    Spacer(Modifier.height(8.dp))
                    FiloSecondaryButton(text = stringResource(R.string.update_check), onClick = onCheckUpdate)
                }
                UpdateManager.State.Idle -> FiloSecondaryButton(
                    text = stringResource(R.string.update_check),
                    onClick = onCheckUpdate,
                )
            }
        }

        FiloCard {
            SectionLabel(stringResource(R.string.settings_about))
            Spacer(Modifier.height(8.dp))
            Timestamp(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.settings_gallery),
                    style = FiloType.Label,
                    color = Blood,
                    modifier = Modifier.clickable { onOpenGallery() },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showLiveLocationSetup) {
        LiveLocationSetupDialog(
            onDismiss = { showLiveLocationSetup = false },
            onReady = {
                showLiveLocationSetup = false
                onSetLiveLocation(true)
            },
        )
    }

    if (showStartPicker) {
        TimePickerDialog(
            initial = sleepStart,
            onDismiss = { showStartPicker = false },
            onConfirm = { onSetSleepWindow(it, sleepEnd); showStartPicker = false },
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initial = sleepEnd,
            onDismiss = { showEndPicker = false },
            onConfirm = { onSetSleepWindow(sleepStart, it); showEndPicker = false },
        )
    }
    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = PgTime.localDate(snapshot.couple?.sinceDate)
                ?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            colors = DatePickerDefaults.colors(containerColor = Surface),
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onSetSinceDate(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.save), color = Crimson, style = FiloType.Label) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel), color = Ash, style = FiloType.Label)
                }
            },
        ) {
            DatePicker(
                state = state,
                colors = DatePickerDefaults.colors(
                    containerColor = Surface,
                    titleContentColor = Bone,
                    headlineContentColor = Bone,
                    weekdayContentColor = Ash,
                    dayContentColor = Bone,
                    selectedDayContainerColor = Crimson,
                    selectedDayContentColor = Ink,
                    todayContentColor = Blood,
                    todayDateBorderColor = Blood,
                ),
            )
        }
    }
}

@Composable
private fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Surface, androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Ink,
                    clockDialSelectedContentColor = Ink,
                    clockDialUnselectedContentColor = Bone,
                    selectorColor = Crimson,
                    periodSelectorBorderColor = Blood,
                    timeSelectorSelectedContainerColor = Crimson,
                    timeSelectorSelectedContentColor = Ink,
                    timeSelectorUnselectedContainerColor = Ink,
                    timeSelectorUnselectedContentColor = Bone,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), color = Ash, style = FiloType.Label)
                }
                TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                    Text(stringResource(R.string.save), color = Crimson, style = FiloType.Label)
                }
            }
        }
    }
}

@Suppress("unused")
private val sdkGuard = Build.VERSION.SDK_INT

/**
 * Reading what is playing from the phone itself.
 *
 * This is the route that needs no Spotify account, no developer app and nobody's Premium
 * subscription. The awkward part is Android 13's restricted settings: a sideloaded app has
 * this toggle greyed out until the user unblocks it from the app info screen, and without
 * saying so the app just looks broken.
 */
@Composable
private fun NowPlayingCard() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(NotificationAccess.isGranted(context)) }

    // The grant happens on a settings screen, so re-read it every time we come back.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                granted = NotificationAccess.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    FiloCard {
        SectionLabel(stringResource(R.string.now_playing_settings))
        Spacer(Modifier.height(8.dp))
        Timestamp(stringResource(R.string.now_playing_body))
        Spacer(Modifier.height(12.dp))

        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("●", fontSize = 10.sp, color = Crimson)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.now_playing_on), style = FiloType.Body, color = Bone)
            }
            Spacer(Modifier.height(10.dp))
            FiloSecondaryButton(
                text = stringResource(R.string.settings_permissions),
                onClick = { NotificationAccess.openSettings(context) },
            )
        } else {
            FiloButton(
                text = stringResource(R.string.now_playing_enable),
                onClick = { NotificationAccess.openSettings(context) },
            )
            if (NotificationAccess.needsRestrictedSettingsUnlock) {
                Spacer(Modifier.height(12.dp))
                Timestamp(stringResource(R.string.now_playing_restricted))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.now_playing_app_info),
                    style = FiloType.Label,
                    color = Blood,
                    modifier = Modifier.clickable { NotificationAccess.openAppInfo(context) },
                )
            }
        }
    }
}
