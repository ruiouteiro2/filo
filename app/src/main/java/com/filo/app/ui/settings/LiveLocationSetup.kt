package com.filo.app.ui.settings

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.filo.app.R
import com.filo.app.location.LocationPermissions
import com.filo.app.ui.components.FiloButton
import com.filo.app.ui.components.FiloSecondaryButton
import com.filo.app.ui.theme.Ash
import com.filo.app.ui.theme.Crimson
import com.filo.app.ui.theme.Bone
import com.filo.app.ui.theme.FiloType
import com.filo.app.ui.theme.Surface

/** The ladder that has to be climbed, in this order, before always-on tracking can start. */
private enum class Step { Foreground, Background, Battery, AutoRevoke, Ready }

private fun nextStep(context: Context): Step = when {
    !LocationPermissions.hasForegroundLocation(context) -> Step.Foreground
    !LocationPermissions.hasBackgroundLocation(context) -> Step.Background
    !LocationPermissions.isIgnoringBatteryOptimisations(context) -> Step.Battery
    !LocationPermissions.isAutoRevokeExempt(context) -> Step.AutoRevoke
    else -> Step.Ready
}

/**
 * Walks the user through everything Android demands before an app may report location with
 * the screen off. Each step is explained in the app's own words first, because on API 30+
 * the system offers no explanation of its own for the background grant: it simply drops you
 * into a settings page.
 *
 * The step is recomputed every time the app comes back to the foreground, because most of
 * these grants are made on a screen outside the app.
 */
@Composable
fun LiveLocationSetupDialog(
    onDismiss: () -> Unit,
    onReady: () -> Unit,
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(nextStep(context)) }

    // Settings trips happen outside the app, so re-read the truth on every resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) step = nextStep(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { step = nextStep(context) }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { step = nextStep(context) }

    if (step == Step.Ready) {
        onReady()
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Surface, RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Text(text = titleFor(step), style = FiloType.Value, color = Bone)
            Spacer(Modifier.height(12.dp))
            Text(text = bodyFor(step), style = FiloType.Body, color = Ash)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.setup_step_of, step.ordinal + 1, Step.Ready.ordinal),
                style = FiloType.Timestamp,
                color = Crimson,
            )
            Spacer(Modifier.height(20.dp))
            FiloButton(
                text = stringResource(R.string.permission_continue),
                onClick = {
                    when (step) {
                        Step.Foreground -> foregroundLauncher.launch(LocationPermissions.FOREGROUND)

                        Step.Background ->
                            if (LocationPermissions.backgroundNeedsSettingsTrip()) {
                                // API 30+: no system dialog exists for this. Settings is the
                                // only route, and the user has just been told what to pick.
                                LocationPermissions.openAppSettings(context)
                            } else {
                                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }

                        Step.Battery -> LocationPermissions.requestIgnoreBatteryOptimisations(context)
                        Step.AutoRevoke -> LocationPermissions.requestAutoRevokeExemption(context)
                        Step.Ready -> Unit
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            FiloSecondaryButton(text = stringResource(R.string.permission_not_now), onClick = onDismiss)
        }
    }
}

@Composable
private fun titleFor(step: Step): String = stringResource(
    when (step) {
        Step.Foreground -> R.string.permission_precise_title
        Step.Background -> R.string.permission_background_title
        Step.Battery -> R.string.setup_battery_title
        Step.AutoRevoke -> R.string.setup_autorevoke_title
        Step.Ready -> R.string.settings_tracking_on
    },
)

@Composable
private fun bodyFor(step: Step): String = when (step) {
    Step.Foreground -> stringResource(R.string.permission_precise_body)
    Step.Background -> {
        val context = LocalContext.current
        val base = stringResource(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                R.string.permission_background_body_settings
            } else {
                R.string.permission_background_body
            },
        )
        // Ask the system what it is actually going to call the option on this device, in
        // this language, rather than hardcoding a phrase that OEM ROMs reword.
        val label = LocationPermissions.backgroundOptionLabel(context)
        if (label == null) {
            base
        } else {
            listOf(base, context.getString(R.string.permission_background_label, label))
                .joinToString(System.lineSeparator() + System.lineSeparator())
        }
    }
    Step.Battery -> stringResource(R.string.setup_battery_body)
    Step.AutoRevoke -> stringResource(R.string.setup_autorevoke_body)
    Step.Ready -> stringResource(R.string.settings_tracking_on)
}
