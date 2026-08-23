package com.filo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.filo.app.core.time.DayState
import com.filo.app.ui.theme.Ash
import com.filo.app.ui.theme.Crimson

/**
 * A ring around a face that says one thing: lit means awake, dim means asleep.
 *
 * It replaces an earlier ring that encoded the whole 24 hours as an angle, with the sleep
 * window drawn in near black and a moving dot for the current time. That version had to be
 * explained to be read, which is the same as not working, and its sleep arc was drawn in a
 * colour three points of luminance from the card behind it, so the one thing it most needed
 * to show was invisible.
 *
 * This one is deliberately redundant with the sentence printed under it. Solid versus faded
 * is pre-attentive: it reads in the corner of the eye, in greyscale, in a second, with no
 * legend. If the caption feels like it is repeating the ring, the ring is working.
 */
@Composable
fun PresenceRing(
    state: DayState,
    modifier: Modifier = Modifier,
    diameter: Dp = 128.dp,
    ringWidth: Dp = 8.dp,
    badge: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        val awake = state is DayState.Awake
        val unknown = state is DayState.Unknown

        if (awake) {
            // Only the awake state glows. A halo on both would make the pair look identical,
            // which is the failure this component exists to fix.
            Box(modifier = Modifier.size(diameter).circularGlow(color = Crimson, spread = 16.dp, alpha = 0.34f))
        }

        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = ringWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            when {
                // Awake: one unbroken, fully saturated circle.
                awake -> drawArc(
                    color = Crimson,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                // Asleep: the same circle, faded right back. Ash over Surface, never Ink over
                // Surface, because those two are indistinguishable on this card.
                !unknown -> drawArc(
                    color = Ash.copy(alpha = 0.22f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                // Hours not set: dashed, so "we do not know" never looks like "asleep".
                else -> drawArc(
                    color = Ash.copy(alpha = 0.30f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(
                        width = stroke * 0.5f,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 7.dp.toPx()),
                            0f,
                        ),
                    ),
                )
            }
        }

        content()

        if (badge != null) {
            // Sat on the ring at 4 o'clock. At a 128dp ring the stroke sits ~59dp from the
            // centre, so a 30dp badge centred at (+42, +42) clears an 84dp avatar with room
            // to spare and never steals its tap target.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = diameter * 0.33f, y = diameter * 0.33f),
                content = badge,
            )
        }
    }
}
