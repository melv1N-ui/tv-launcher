package de.melvin.tvlauncher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val twinkle: Boolean,
    val period: Float,
    val phase: Float,
    val warm: Boolean,
    val big: Boolean,
)

private val StarCold = Color(0xFFE8EEFF)
private val StarWarm = Color(0xFFFFE2B8)

/** Sternenhimmel mit Nebel (Farbe folgt der ausgewählten App) und Planet unten rechts. */
@Composable
fun SpaceBackground(glow: Color, modifier: Modifier = Modifier) {
    val stars = remember {
        val rnd = Random(1337)
        List(190) {
            val big = rnd.nextFloat() > 0.9f
            Star(
                x = rnd.nextFloat() * 1.04f - 0.02f,
                y = rnd.nextFloat() * 1.08f - 0.04f,
                radius = if (big) 1.3f + rnd.nextFloat() * 0.7f else 0.5f + rnd.nextFloat() * 0.7f,
                alpha = 0.35f + rnd.nextFloat() * 0.6f,
                twinkle = rnd.nextFloat() > 0.7f,
                period = 3f + rnd.nextFloat() * 5f,
                phase = rnd.nextFloat() * 6.283f,
                warm = rnd.nextFloat() > 0.85f,
                big = big,
            )
        }
    }

    // Laufende Zeit in Sekunden für Funkeln und Drift
    val transition = rememberInfiniteTransition(label = "space")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3600f,
        animationSpec = infiniteRepeatable(tween(3_600_000, easing = LinearEasing)),
        label = "time",
    )

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val unit = h / 540f

        drawRect(Color(0xFF05060B))

        // Nebel
        drawRect(
            Brush.radialGradient(
                listOf(glow.copy(alpha = 0.32f), Color.Transparent),
                center = Offset(w * 0.72f, h * 0.28f),
                radius = w * 0.42f,
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(Color(0x386E46B4), Color.Transparent),
                center = Offset(w * 0.12f, h * 0.88f),
                radius = w * 0.36f,
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(Color(0x24285AA0), Color.Transparent),
                center = Offset(w * 0.4f, h * 0.1f),
                radius = w * 0.28f,
            )
        )

        // Sterne mit langsamer Drift
        val dx = sin(time / 45f) * w * 0.012f
        val dy = cos(time / 60f) * h * 0.008f
        for (s in stars) {
            val a = if (s.twinkle) s.alpha * (0.6f + 0.4f * sin(time * 6.283f / s.period + s.phase)) else s.alpha
            val c = if (s.warm) StarWarm else StarCold
            val center = Offset(s.x * w + dx, s.y * h + dy)
            if (s.big) drawCircle(c.copy(alpha = (a * 0.22f).coerceIn(0f, 1f)), radius = s.radius * unit * 3f, center = center)
            drawCircle(c.copy(alpha = a.coerceIn(0f, 1f)), radius = s.radius * unit, center = center)
        }

        // Umlaufbahn
        val orbitTopLeft = Offset(w * 0.458f, h * 0.704f)
        val orbitSize = Size(w * 0.677f, h * 0.278f)
        rotate(-12f, pivot = Offset(orbitTopLeft.x + orbitSize.width / 2f, orbitTopLeft.y + orbitSize.height / 2f)) {
            drawOval(
                color = Color.White.copy(alpha = 0.08f),
                topLeft = orbitTopLeft,
                size = orbitSize,
                style = Stroke(width = unit),
            )
        }

        // Planet
        val pc = Offset(w * 0.9167f, h * 1.083f)
        val pr = h * 0.352f
        drawCircle(
            Brush.radialGradient(listOf(glow.copy(alpha = 0.25f), Color.Transparent), center = pc, radius = pr * 1.4f),
            radius = pr * 1.4f,
            center = pc,
        )
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFF2A3350),
                0.45f to Color(0xFF121726),
                0.75f to Color(0xFF07090F),
                center = Offset(pc.x - pr * 0.3f, pc.y - pr * 0.5f),
                radius = pr * 1.6f,
            ),
            radius = pr,
            center = pc,
        )
        drawCircle(Color(0x2EAABEFF), radius = pr, center = pc, style = Stroke(width = unit * 1.5f))
    }
}
