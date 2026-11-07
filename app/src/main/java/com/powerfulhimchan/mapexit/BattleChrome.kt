package com.powerfulhimchan.mapexit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.min

private val bronze = Color(0xFF9B7C7B)
private val scarlet = Color(0xFFEE4B56)
private fun framePath(w: Float, h: Float, inset: Float = 0f): Path {
    val c = min(7f, min(w, h) * .16f)
    return Path().apply {
        moveTo(inset + c, inset); lineTo(w - inset - c, inset)
        lineTo(w - inset - c, inset + c * .4f); lineTo(w - inset, inset + c * .4f)
        lineTo(w - inset, h - inset - c); lineTo(w - inset - c, h - inset - c)
        lineTo(w - inset - c, h - inset); lineTo(inset + c, h - inset)
        lineTo(inset + c, h - inset - c * .4f); lineTo(inset, h - inset - c * .4f)
        lineTo(inset, inset + c); lineTo(inset + c, inset + c); close()
    }
}
private val battleShape = GenericShape { size, _ -> addPath(framePath(size.width, size.height)) }

/** Pixel bevels and restrained metal texture without bitmap-stretching the text. */
internal fun Modifier.battlePanel(edge: Color = bronze, pressed: Boolean = false, enabled: Boolean = true): Modifier =
    clip(battleShape).drawWithCache {
        val outer = framePath(size.width, size.height, .8f)
        val inner = framePath(size.width, size.height, 3f)
        val fill = Brush.verticalGradient(if (pressed) listOf(Color(0xFF4A343B), Color(0xFF231C29)) else listOf(Color(0xFF2A232F), Color(0xFF15141F)))
        onDrawBehind {
            drawPath(outer, fill, alpha = if (enabled) .98f else .72f)
            // Fixed flecks and engraved inner rim, shared by panels and buttons.
            for (i in 0..44) {
                val x = 6f + (i * 71 % maxOf(1, (size.width - 12).toInt()))
                val y = 5f + (i * 29 % maxOf(1, (size.height - 10).toInt()))
                drawRect(Color(0xFFBFA198).copy(alpha = .035f), Offset(x, y), Size(2f, 1f))
            }
            drawPath(outer, if (enabled) edge else line, style = Stroke(1.6f))
            drawPath(inner, edge.copy(alpha = .32f), style = Stroke(.8f))
            drawLine(Color(0xFFEBD1BF).copy(alpha = .37f), Offset(8f, 2f), Offset(size.width - 8f, 2f), 1f)
            drawLine(Color.Black.copy(alpha = .55f), Offset(8f, size.height - 2f), Offset(size.width - 8f, size.height - 2f), 2f)
        }
    }

@Composable
internal fun rememberWarningPulse(clock: Long): Float {
    var animate by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { animate = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f }
    return HealthWarning.pulse(clock, animate)
}

@Composable
internal fun HealthHeart(danger: Boolean, pulse: Float) {
    Canvas(Modifier.size(16.dp).semantics { contentDescription = if (danger) "체력 위험 경고" else "체력" }) {
        val u = size.width / 16
        if (danger) {
            drawCircle(scarlet.copy(alpha = .12f + pulse * .16f), 7.5f * u)
            for (i in 0..1) drawCircle(scarlet.copy(alpha = (.28f + pulse * .34f) / (i + 1)), (6.2f + i * 1.25f + pulse * .65f) * u, style = Stroke(u * .55f))
        }
        val rows = listOf("01100110", "11111111", "11111111", "01111110", "00111100", "00011000")
        rows.forEachIndexed { y, row -> row.forEachIndexed { x, pixel -> if (pixel == '1') {
            val color = if (danger) if (y < 2) Color(0xFFFFB1A4) else scarlet else mint.copy(alpha = .8f)
            drawRect(color, Offset((4 + x) * u, (5 + y) * u), Size(u, u))
        } } }
    }
}

@Composable
internal fun HealthMeter(fraction: Float, danger: Boolean, pulse: Float) {
    Canvas(Modifier.fillMaxWidth().height(7.dp)) {
        drawRect(Color(0xFF17121B))
        drawRect(bronze.copy(alpha = .65f), style = Stroke(1f))
        val fill = if (danger) Color(0xFFFF675E) else if (fraction < .3f) red else mint
        drawRect(Brush.verticalGradient(listOf(fill, fill.copy(alpha = .65f))), Offset(1f, 1f), Size(((size.width - 2) * fraction.coerceIn(0f, 1f)).coerceAtLeast(0f), (size.height - 2).coerceAtLeast(1f)))
        if (danger) {
            val cy = size.height * .5f
            val x = size.width * .77f
            val u = size.height * .24f
            val trace = Path().apply {
                moveTo(x - u * 8, cy); lineTo(x - u * 3, cy); lineTo(x - u * 2, cy - u)
                lineTo(x - u, cy + u); lineTo(x, cy - u * 2); lineTo(x + u, cy + u * 1.8f)
                lineTo(x + u * 2, cy); lineTo(x + u * 8, cy)
            }
            drawPath(trace, scarlet.copy(alpha = .45f + pulse * .5f), style = Stroke(1.3f))
        }
    }
}

/** Draw-only overlay: no pointer input, no gameplay timer, and a clear scene centre. */
@Composable
internal fun LowHealthVignette(s: State, clock: Long, modifier: Modifier) {
    if (!HealthWarning.active(s.hp, Equipment.stats(s).hp, s.floor)) return
    if (s.mode !in listOf(Mode.EXPLORE, Mode.MOVING, Mode.COMBAT_ATTACK, Mode.COMBAT_DEFEND, Mode.TRAP, Mode.TRAP_GUIDE, Mode.ENCOUNTER, Mode.VICTORY, Mode.FLOOR_CLEAR)) return
    val pulse = rememberWarningPulse(clock)
    Canvas(modifier) {
        val color = Color(0xFFB51C38).copy(alpha = .12f + pulse * .22f)
        val clear = Color.Transparent
        val side = size.width * .075f
        val cap = min(size.height * .045f, size.width * .09f)
        drawRect(Brush.horizontalGradient(listOf(color, clear), endX = side), size = Size(side, size.height))
        drawRect(Brush.horizontalGradient(listOf(clear, color), startX = size.width - side, endX = size.width), topLeft = Offset(size.width - side, 0f), size = Size(side, size.height))
        drawRect(Brush.verticalGradient(listOf(color, clear), endY = cap), size = Size(size.width, cap))
        drawRect(Brush.verticalGradient(listOf(clear, color), startY = size.height - cap, endY = size.height), topLeft = Offset(0f, size.height - cap), size = Size(size.width, cap))
    }
}
