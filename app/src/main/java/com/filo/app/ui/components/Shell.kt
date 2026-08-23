package com.filo.app.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.filo.app.ui.theme.Ash
import com.filo.app.ui.theme.Blood
import com.filo.app.ui.theme.FiloType
import com.filo.app.ui.theme.Bone
import com.filo.app.ui.theme.LocalReducedMotion
import com.filo.app.ui.theme.Surface
import java.util.Locale

/**
 * Near black card with a blood red hairline. When it is tappable it lifts and picks up a
 * red wash under the finger, which is this platform's answer to a hover state.
 */
@Composable
fun FiloCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val interactionSource = rememberPressSource()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface)
            .border(1.dp, Blood.copy(alpha = 0.30f), shape)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier
                        .pressGlow(interactionSource)
                        .clickable(interactionSource = interactionSource, indication = null) { onClick() }
                },
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        content = content,
    )
}

/** Uppercase, letter spaced, ash. The quiet label above every value. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    Text(text = text.uppercase(locale), style = FiloType.Label, color = Ash, modifier = modifier)
}

@Composable
fun CardValue(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = Bone) {
    Text(text = text, style = FiloType.Value, color = color, modifier = modifier)
}

@Composable
fun Timestamp(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = FiloType.Timestamp, color = Ash, modifier = modifier)
}

/**
 * The single orchestrated moment in the app: on open, cards fade up 12dp over 240ms with a
 * 40ms stagger. Nothing else animates, and the whole thing is skipped when the system has
 * animations turned off.
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    var shown by remember { mutableStateOf(reducedMotion) }
    LaunchedEffect(Unit) { shown = true }

    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (reducedMotion) {
            tween(0)
        } else {
            tween(durationMillis = 240, delayMillis = index * 40, easing = LinearOutSlowInEasing)
        },
        label = "cardEntrance",
    )
    val offsetPx = with(LocalDensity.current) { 12.dp.toPx() }

    Column(
        modifier = modifier
            .alpha(progress)
            .graphicsLayer { translationY = (1f - progress) * offsetPx },
    ) {
        content()
    }
}
