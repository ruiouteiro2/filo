package com.filo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filo.app.ui.theme.Blood
import com.filo.app.ui.theme.Crimson
import com.filo.app.ui.theme.Ink
import com.filo.app.ui.theme.Karla
import java.util.Locale

/**
 * Round face. Falls back to the initial of the display name on black, which is what both
 * people see until someone actually uploads a photo.
 */
@Composable
fun Avatar(
    displayName: String?,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Ink)
            .border(1.dp, Blood.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                text = initial(displayName),
                fontFamily = Karla,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp,
                color = Crimson,
            )
        }
    }
}

private fun initial(displayName: String?): String {
    val trimmed = displayName?.trim().orEmpty()
    if (trimmed.isEmpty()) return "?"
    return trimmed.substring(0, 1).uppercase(Locale.getDefault())
}
