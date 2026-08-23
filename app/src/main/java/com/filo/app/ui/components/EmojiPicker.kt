package com.filo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.filo.app.R
import com.filo.app.ui.theme.Bone
import com.filo.app.ui.theme.Crimson
import com.filo.app.ui.theme.FiloType
import com.filo.app.ui.theme.Line
import com.filo.app.ui.theme.Surface
import com.filo.app.ui.theme.SurfaceHigh
import androidx.compose.ui.res.stringResource

/**
 * A picker, because reaching for the system keyboard's emoji key to say how your day went
 * is three taps too many. The set is curated rather than complete: these are the ones people
 * actually reach for about a mood, and the text field is still there for anything else.
 */
private val EMOJI = listOf(
    // Feelings, good to bad
    "😊", "🥰", "😍", "😘", "🤗", "😄",
    "😂", "😌", "😇", "😉", "😎", "🤩",
    "🙃", "🤪", "🥳", "🥲",
    "😔", "😞", "😢", "😭", "😩", "😤",
    "😡", "😳", "😱", "🤯", "🤔", "🙄",
    "😐", "😶", "🤐", "🫠",
    // Tired, ill, busy
    "😴", "😪", "🥱", "🤒", "🤢", "🤯",
    "🧠", "💻", "📚", "🏋️", "🚶", "🧘",
    // Hearts and love
    "❤️", "🧡", "💛", "💚", "💙", "💜",
    "🤍", "💔", "💕", "💖", "💘", "💌",
    // Day to day
    "☕", "🍺", "🍷", "🍕", "🍝", "🍣",
    "🍫", "🍰", "🌙", "☀️", "☁️", "🌧️",
    "❄️", "🌈", "⭐", "✨", "🔥", "🎵",
    "🎮", "📺", "🚗", "✈️", "🏠", "🎉",
)

@Composable
fun EmojiPickerDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(SurfaceHigh, Surface)),
                    RoundedCornerShape(22.dp),
                )
                .border(1.dp, Line, RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Text(
                stringResource(R.string.mood_pick_emoji),
                style = FiloType.TitleItalic,
                color = Bone,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(48.dp),
                modifier = Modifier.height(280.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(EMOJI) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clickable { onPick(emoji) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = emoji, fontSize = 26.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            if (onClear != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.mood_clear_emoji),
                    style = FiloType.Label,
                    color = Crimson,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onClear() }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}
