package com.filo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.filo.app.ui.theme.Ash
import com.filo.app.ui.theme.Bone
import com.filo.app.ui.theme.FiloType
import com.filo.app.ui.theme.Ink

/**
 * A photo, as big as the screen allows, over a near-black scrim. Tap anywhere to leave.
 * That is the whole job: no zoom, no share sheet, no chrome.
 */
@Composable
fun FullImageDialog(
    url: String,
    name: String?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink.copy(alpha = 0.97f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = url,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                if (name != null) {
                    Text(
                        text = name,
                        style = FiloType.Label,
                        color = Ash,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }
            Text(
                text = "×",
                style = FiloType.Title,
                color = Bone,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp),
            )
        }
    }
}
