package com.filo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
 * A photo as big as the screen, and closer if you want: pinch to zoom up to 5x, drag to pan,
 * double tap to jump between fitted and 2.5x. A tap closes it only while un-zoomed, so
 * panning around a zoomed picture never accidentally dismisses it.
 */
@Composable
fun FullImageDialog(
    url: String,
    name: String?,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink.copy(alpha = 0.97f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 5f)
                        // Pan scales with zoom so a finger movement matches the picture.
                        offset = if (next > 1f) offset + pan else Offset.Zero
                        scale = next
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (scale <= 1.02f) onDismiss() },
                        onDoubleTap = {
                            if (scale > 1.02f) reset() else scale = 2.5f
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            if (name != null && scale <= 1.02f) {
                Text(
                    text = name,
                    style = FiloType.Label,
                    color = Ash,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp),
                )
            }
            Text(
                text = "×",
                style = FiloType.Title,
                color = Bone,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            )
        }
    }
}
