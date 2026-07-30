package com.dorybrain.app.ui.speech

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * The listening waveform. Bars are driven by real microphone levels from the
 * recognizer's RMS callback, so this shows what the mic is actually picking
 * up. During silence the levels collapse to near zero, which would read as
 * "dead" — so a slow idle ripple keeps a minimum motion to signal the mic is
 * still live through a pause.
 */
@Composable
fun Waveform(
    levels: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val idlePhase by rememberInfiniteTransition(label = "waveformIdle").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2_200), RepeatMode.Restart),
        label = "idlePhase"
    )

    Canvas(modifier = modifier) {
        if (levels.isEmpty()) return@Canvas

        val barWidth = 3.dp.toPx()
        val gap = (size.width - barWidth * levels.size) / max(1, levels.size - 1)
        val centreY = size.height / 2f
        val maxHalf = size.height / 2f

        levels.forEachIndexed { index, level ->
            // A gentle travelling ripple so quiet stretches still move.
            val ripple = abs(sin(idlePhase + index * 0.28f)) * 0.10f
            val amplitude = (level * 0.9f + ripple).coerceIn(0.02f, 1f)

            // Taper the ends so the shape reads as a waveform, not a bar chart.
            val distanceFromCentre = abs(index - (levels.size - 1) / 2f) /
                ((levels.size - 1) / 2f)
            val taper = 1f - (distanceFromCentre * distanceFromCentre) * 0.55f

            val half = maxHalf * amplitude * taper
            val x = index * (barWidth + gap) + barWidth / 2f

            drawLine(
                color = color.copy(alpha = 0.35f + 0.65f * amplitude),
                start = Offset(x, centreY - half),
                end = Offset(x, centreY + half),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
