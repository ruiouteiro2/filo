package com.filo.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.filo.app.R
import com.filo.app.ui.components.FiloButton
import com.filo.app.ui.components.FiloSecondaryButton
import com.filo.app.ui.theme.Ash
import com.filo.app.ui.theme.FiloType
import com.filo.app.ui.theme.Bone
import com.filo.app.ui.theme.Surface

/** Which permission the app is currently explaining. */
enum class PermissionAsk { Location, Notifications }

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
    runCatching { context.startActivity(intent) }
}

/**
 * The explanation always comes before the system dialog, in the app's own voice, and
 * declining is a first class outcome rather than something to nag about.
 */
@Composable
fun PermissionRationaleDialog(
    ask: PermissionAsk,
    onResolved: () -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onResolved()
    }

    Dialog(onDismissRequest = onResolved) {
        Column(
            modifier = Modifier
                .background(Surface, RoundedCornerShape(20.dp))
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(
                    when (ask) {
                        PermissionAsk.Location -> R.string.permission_location_title
                        PermissionAsk.Notifications -> R.string.permission_notifications_title
                    },
                ),
                style = FiloType.Value,
                color = Bone,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    when (ask) {
                        PermissionAsk.Location -> R.string.permission_location_body
                        PermissionAsk.Notifications -> R.string.permission_notifications_body
                    },
                ),
                style = FiloType.Body,
                color = Ash,
            )
            Spacer(Modifier.height(24.dp))
            FiloButton(
                text = stringResource(R.string.permission_continue),
                onClick = {
                    when (ask) {
                        PermissionAsk.Location -> launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        PermissionAsk.Notifications ->
                            if (Build.VERSION.SDK_INT >= 33) {
                                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onResolved()
                            }
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            FiloSecondaryButton(text = stringResource(R.string.permission_not_now), onClick = onResolved)
        }
    }
}
