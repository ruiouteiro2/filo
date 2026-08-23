package com.filo.app.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.filo.app.ui.theme.Crimson
import com.filo.app.ui.theme.Ruby
import com.filo.app.ui.theme.Ink
import com.filo.app.ui.theme.LocalReducedMotion

/**
 * Soft blood red orbs bled into the black background. Pure decoration, kept far enough down
 * in luminance that text never has to fight it.
 */
@Composable
fun OrbBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawOrb(center = Offset(size.width * 0.12f, size.height * 0.06f), radius = size.width * 0.62f, color = Ruby, peak = 0.30f)
            drawOrb(center = Offset(size.width * 0.95f, size.height * 0.30f), radius = size.width * 0.55f, color = Crimson, peak = 0.14f)
            drawOrb(center = Offset(size.width * 0.30f, size.height * 0.92f), radius = size.width * 0.70f, color = Crimson, peak = 0.16f)
        }
        content()
    }
}

private fun DrawScope.drawOrb(center: Offset, radius: Float, color: Color, peak: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = peak), color.copy(alpha = peak * 0.35f), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** A halo behind a circular element, used around the day ring. */
fun Modifier.circularGlow(color: Color = Crimson, spread: Dp = 26.dp, alpha: Float = 0.35f): Modifier =
    drawBehind {
        val radius = size.minDimension / 2f + spread.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = alpha * 0.55f), Color.Transparent),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = radius,
            ),
            radius = radius,
        )
    }

/**
 * The Android equivalent of a hover: the surface lifts and picks up a red rim while a finger
 * is on it. Skipped when the system has animations turned off.
 */
fun Modifier.pressGlow(
    interactionSource: MutableInteractionSource,
    color: Color = Crimson,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (reducedMotion) tween(0) else tween(140, easing = LinearOutSlowInEasing),
        label = "pressGlow",
    )
    this
        .graphicsLayer {
            val scale = 1f - 0.015f * progress
            scaleX = scale
            scaleY = scale
        }
        .drawBehind {
            if (progress <= 0f) return@drawBehind
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.22f * progress), Color.Transparent),
                    startY = 0f,
                    endY = size.height,
                ),
                size = Size(size.width, size.height),
            )
        }
}

@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/** The flat ground the orbs sit on. */
val ScreenBackground: Color = Ink
