package com.filo.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.filo.app.R
import com.filo.app.location.LocationPermissions
import com.filo.app.nowplaying.NotificationAccess
import com.filo.app.ui.components.FiloButton
import com.filo.app.ui.components.FiloSecondaryButton
import com.filo.app.ui.components.OrbBackground
import com.filo.app.ui.components.SectionLabel
import com.filo.app.ui.theme.Ash
import com.filo.app.ui.theme.Blood
import com.filo.app.ui.theme.Bone
import com.filo.app.ui.theme.Crimson
import com.filo.app.ui.theme.FiloType
import com.filo.app.ui.theme.Ink
import com.filo.app.ui.theme.Surface

/**
 * Every permission the app will ever ask for, once, in a row, right after pairing.
 *
 * Previously these were scattered: a dialog on the home screen, a toggle in settings, another
 * toggle somewhere else, each dropping the user into a different system screen with no
 * explanation of why. Asking all of it in one pass, in order, with each one saying plainly
 * what it buys and what it costs, is the difference between a setup and an ambush.
 *
 * Everything here is skippable. The two optional ones say so.
 */
private enum class Ask { Notifications, Location, AlwaysLocation, NowPlaying }

private val Steps = Ask.entries.toList()

@Composable
fun PermissionsOnboarding(onDone: () -> Unit) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    // Half of these are granted on a system screen outside the app, so re-read on return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { index++ }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { index++ }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { index++ }

    if (index >= Steps.size) {
        onDone()
        return
    }
    val step = Steps[index]
    @Suppress("UNUSED_EXPRESSION") tick // re-read grants when we come back
    val granted = isGranted(context, step)

    OrbBackground(modifier = Modifier.fillMaxSize().background(Ink)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Steps.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == index) 9.dp else 7.dp)
                            .background(
                                if (i <= index) Crimson else Ash.copy(alpha = 0.35f),
                                CircleShape,
                            ),
                    )
                }
            }
            Spacer(Modifier.height(40.dp))

            Text(stringResource(titleOf(step)), style = FiloType.Title, color = Bone, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(bodyOf(step)),
                style = FiloType.Body,
                color = Ash,
                textAlign = TextAlign.Center,
            )

            if (!isRequired(step)) {
                Spacer(Modifier.height(12.dp))
                SectionLabel(stringResource(R.string.onboarding_optional))
            }

            // The one that needs a warning: Android blocks this outright for sideloaded apps.
            if (step == Ask.NowPlaying && NotificationAccess.needsRestrictedSettingsUnlock && !granted) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.now_playing_restricted),
                        style = FiloType.Timestamp,
                        color = Ash,
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            if (granted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("●", fontSize = 10.sp, color = Crimson)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.onboarding_granted), style = FiloType.Label, color = Crimson)
                }
                Spacer(Modifier.height(16.dp))
                FiloButton(text = stringResource(R.string.onboarding_next), onClick = { index++ })
            } else {
                FiloButton(
                    text = stringResource(R.string.onboarding_allow),
                    onClick = {
                        when (step) {
                            Ask.Notifications ->
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    index++
                                }

                            Ask.Location -> locationLauncher.launch(LocationPermissions.FOREGROUND)

                            Ask.AlwaysLocation ->
                                if (LocationPermissions.backgroundNeedsSettingsTrip()) {
                                    LocationPermissions.openAppSettings(context)
                                } else {
                                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                }

                            Ask.NowPlaying -> NotificationAccess.openSettings(context)
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                FiloSecondaryButton(
                    text = stringResource(
                        if (isRequired(step)) R.string.onboarding_skip else R.string.onboarding_not_now,
                    ),
                    onClick = { index++ },
                )
            }

            if (step == Ask.NowPlaying && !granted) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.now_playing_app_info),
                    style = FiloType.Label,
                    color = Blood,
                    modifier = Modifier
                        .padding(8.dp)
                        .then(Modifier)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun isGranted(context: Context, step: Ask): Boolean = when (step) {
    Ask.Notifications -> Build.VERSION.SDK_INT < 33 ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    Ask.Location -> LocationPermissions.hasForegroundLocation(context)
    Ask.AlwaysLocation -> LocationPermissions.hasBackgroundLocation(context)
    Ask.NowPlaying -> NotificationAccess.isGranted(context)
}

/** The first two make the app work at all; the last two are extras. */
private fun isRequired(step: Ask): Boolean = step == Ask.Notifications || step == Ask.Location

private fun titleOf(step: Ask): Int = when (step) {
    Ask.Notifications -> R.string.onboarding_notifications_title
    Ask.Location -> R.string.onboarding_location_title
    Ask.AlwaysLocation -> R.string.onboarding_always_title
    Ask.NowPlaying -> R.string.onboarding_music_title
}

private fun bodyOf(step: Ask): Int = when (step) {
    Ask.Notifications -> R.string.onboarding_notifications_body
    Ask.Location -> R.string.onboarding_location_body
    Ask.AlwaysLocation -> R.string.onboarding_always_body
    Ask.NowPlaying -> R.string.onboarding_music_body
}
