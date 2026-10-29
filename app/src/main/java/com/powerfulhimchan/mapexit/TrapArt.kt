package com.powerfulhimchan.mapexit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.*

private val iron = Color(0xff647983)
private val edge = Color(0xffc7d8d9)
private val wood = Color(0xff805537)
private val water = Color(0xff7ac8d4)

/** Every challenge is a physical mechanism. No solution letters are painted into the scene. */
internal fun DrawScope.drawObstacle(s: State, clock: Long) {
    val t = s.trap ?: return
    val w = size.width
    val h = size.height
    arena(clock)
    val time = t.elapsedMs.toFloat()
    val danger =
        when (t.phase) {
            TrapPhase.PREPARE -> .10f + .03f * sin(time * .015f)
            TrapPhase.REVEAL -> .10f
            TrapPhase.ACTIVE -> (time / t.limitMs).coerceIn(0f, 1f)
            TrapPhase.RESULT -> if (t.success) .05f else 1f
        }
    val side = t.visualSide()
    when (t.type) {
        0 -> cauldron(t, danger, side)
        1 -> floorBlades(t)
        2 -> closingWalls(t, danger)
        3 -> memoryStones(t)
        4 -> fallingBeam(t, danger)
    }
    // The two stones occupy the same left/right positions as the actual touch buttons.
    for (i in 0..1) {
        val x = w * (if (i == 0) .12f else .65f)
        val lit = side == i
        val color =
            when {
                t.type == 4 && t.signal() && lit -> mint
                t.type == 1 && abs(t.position() - t.target) <= t.targetWidth / 2 && lit -> mint
                lit -> amber
                else -> line
            }
        pixelRect(Color(0xff0b131b), x, h * .84f, w * .23f, h * .07f)
        pixelRect(iron, x + 2, h * .84f + 2, w * .23f - 4, h * .05f)
        pixelRect(color, x + 5, h * .85f, w * .23f - 10, 4f)
        if (lit) {
            drawCircle(color.copy(alpha = .14f), w * .15f, Offset(x + w * .115f, h * .85f))
            for (j in 0..2) pixelRect(color, x + w * (.04f + j * .07f), h * .87f, w * .02f, 3f)
        }
    }
    val heroWidth = min(w * .22f, h * .20f)
    val lastStep =
        if (t.type in listOf(0, 3) && t.progress > 0) t.pattern.getOrNull(t.progress - 1) else null
    val heroX =
        when {
            t.phase == TrapPhase.RESULT && t.success -> w * .72f
            lastStep == 0 -> w * .16f
            lastStep == 1 -> w * .64f
            else -> w * .40f
        }
    adventurer(
        s,
        heroX,
        h * .94f,
        heroWidth,
        armed = false,
        hitTint = if (t.phase == TrapPhase.RESULT && !t.success) red else null,
    )
    if (t.phase == TrapPhase.ACTIVE) {
        pixelRect(line, w * .12f, h * .97f, w * .76f, 4f)
        pixelRect(amber, w * .12f, h * .97f, w * .76f * (1 - time / t.limitMs).coerceIn(0f, 1f), 3f)
    }
}

private fun DrawScope.chain(x: Float, bottom: Float) {
    val h = size.height
    pixelRect(Color(0xff1c2b35), x - 4, 0f, 8f, bottom)
    for (i in 0 until (bottom / 12).toInt()) {
        pixelRect(iron, x - 3, i * 12f, 6f, 8f)
        pixelRect(edge, x - 2, i * 12f + 1, 1f, 5f)
    }
}

private fun DrawScope.cauldron(t: TrapState, danger: Float, side: Int?) {
    val w = size.width
    val h = size.height
    val tilt = (if (side == 1) -1 else 1) * (5f + danger * 28f)
    chain(w * .32f, h * .19f)
    chain(w * .68f, h * .19f)
    withTransform({ rotate(tilt, Offset(w * .5f, h * .23f)) }) {
        drawOval(iron, Offset(w * .23f, h * .13f), Size(w * .54f, h * .28f))
        pixelRect(Color(0xff293f4c), w * .28f, h * .22f, w * .44f, h * .18f)
        drawOval(Color(0xff1b2a33), Offset(w * .23f, h * .12f), Size(w * .54f, h * .11f))
        drawOval(water, Offset(w * .26f, h * .14f), Size(w * .48f, h * .065f))
        drawOval(edge, Offset(w * .23f, h * .12f), Size(w * .54f, h * .11f), style = Stroke(4f))
        drawOval(iron, Offset(w * .16f, h * .20f), Size(w * .10f, h * .12f), style = Stroke(5f))
        drawOval(iron, Offset(w * .74f, h * .20f), Size(w * .10f, h * .12f), style = Stroke(5f))
        for (i in 0..6) pixelRect(
            edge.copy(alpha = .6f),
            w * (.30f + i * .06f),
            h * .25f,
            2f,
            h * .10f,
        )
    }
    for (i in 0..7) {
        val drift = sin(t.elapsedMs * .013f + i) * w * .035f
        pixelRect(
            pale.copy(alpha = .45f),
            w * (.30f + i * .055f) + drift,
            h * (.04f + (i % 3) * .025f),
            w * .018f,
            h * .07f,
        )
    }
    val pour = if (side == 1) .28f else .72f
    val flowing = t.phase == TrapPhase.ACTIVE || (t.phase == TrapPhase.RESULT && !t.success)
    if (flowing)
        for (i in 0..14) {
            val y = h * (.28f + i * .035f)
            val x = w * pour + sin(i * .8f + t.elapsedMs * .025f) * 6
            pixelRect(
                if (i % 3 == 0) edge else water,
                x,
                y,
                w * (.015f + danger * .025f),
                h * .055f,
            )
            if (i > 11) pixelRect(water.copy(alpha = .5f), x - 20, h * .79f, 45f, 4f)
        }
}

private fun DrawScope.floorBlades(t: TrapState) {
    val w = size.width
    val h = size.height
    val extent =
        when (t.phase) {
            TrapPhase.PREPARE,
            TrapPhase.REVEAL -> .10f
            TrapPhase.ACTIVE -> .12f + t.position().toFloat() * .65f
            TrapPhase.RESULT -> if (t.success) .02f else .82f
        }
    pixelRect(Color(0xff080f16), w * .08f, h * .75f, w * .84f, h * .09f)
    for (i in 0..6) {
        val x = w * (.11f + i * .115f)
        pixelRect(iron, x, h * .72f, w * .075f, h * .04f)
        val top = h * (.76f - extent * .65f)
        val blade =
            Path().apply {
                moveTo(x, h * .76f)
                lineTo(x + w * .035f, top)
                lineTo(x + w * .07f, h * .76f)
                close()
            }
        drawPath(blade, edge)
        drawLine(iron, Offset(x + w * .035f, top + 8), Offset(x + w * .035f, h * .75f), 3f)
    }
    val safe = t.phase == TrapPhase.ACTIVE && abs(t.position() - t.target) <= t.targetWidth / 2
    for (i in 0..5) pixelRect(
        if (safe) mint else red.copy(alpha = .6f),
        w * (.14f + i * .13f),
        h * .79f,
        12f,
        4f,
    )
}

private fun DrawScope.closingWalls(t: TrapState, danger: Float) {
    val w = size.width
    val h = size.height
    val pressure = (danger * .75f - t.progress.toFloat() / t.required * .5f).coerceIn(0f, .75f)
    val width = w * (.13f + pressure * .40f)
    for (left in listOf(true, false)) {
        val x = if (left) 0f else w - width
        masonry(x, h * .10f, width, h * .70f, Color(0xff48545a))
        pixelRect(iron, if (left) width - 8 else x, h * .10f, 8f, h * .70f)
        for (j in 0..4) {
            val cy = h * (.20f + j * .12f)
            val path =
                Path().apply {
                    val edgeX = if (left) width else x
                    moveTo(edgeX, cy)
                    lineTo(edgeX + (if (left) 18 else -18), cy + 9)
                    lineTo(edgeX, cy + 18)
                    close()
                }
            drawPath(path, edge)
        }
        val handleX = if (left) width - 21 else x + 12
        pixelRect(amber, handleX, h * .50f, 9f, h * .12f)
        pixelRect(Color(0xff2b2520), handleX + 3, h * .51f, 3f, h * .10f)
    }
    for (i in 0 until t.required) pixelRect(
        if (i < t.progress) mint else iron,
        w * (.25f + i * .10f),
        h * .15f,
        14f,
        5f,
    )
}

private fun DrawScope.memoryStones(t: TrapState) {
    val w = size.width
    val h = size.height
    val side = t.visualSide()
    for (row in 0..3) for (col in 0..1) {
        val x = w * (.18f + col * .36f)
        val y = h * (.34f + row * .11f)
        pixelRect(Color(0xff0b131b), x - 3, y - 3, w * .29f + 6, h * .075f + 6)
        pixelRect(
            if (side == col) Color(0xff557767) else Color(0xff3e515b),
            x,
            y,
            w * .29f,
            h * .075f,
        )
        pixelRect(if (side == col) mint else iron, x + 4, y + 4, w * .29f - 8, 3f)
        pixelRect(if (side == col) mint else muted, x + w * .13f, y + h * .02f, w * .025f, h * .03f)
    }
    for (i in 0..4) {
        val x = w * (.12f + i * .17f)
        chain(x + 12, h * .13f)
        pixelRect(Color(0xff38454d), x, h * .10f, 27f, h * .10f)
        pixelRect(iron, x + 3, h * .10f + 3, 21f, 3f)
        if (t.phase == TrapPhase.RESULT && !t.success) pixelRect(iron, x, h * .39f, 27f, h * .30f)
    }
    for (i in t.pattern.indices) pixelRect(
        if (i < t.progress) mint else line,
        w * (.30f + i * .10f),
        h * .26f,
        12f,
        4f,
    )
}

private fun DrawScope.fallingBeam(t: TrapState, danger: Float) {
    val w = size.width
    val h = size.height
    val fall =
        if (t.holdStarted >= 0 && t.phase == TrapPhase.ACTIVE)
            ((t.elapsedMs - t.holdStarted).toFloat() / t.signalMs).coerceIn(0f, 1.2f)
        else danger
    val y = h * (.14f + fall * .38f)
    chain(w * .19f, y)
    chain(w * .81f, y)
    pixelRect(Color(0xff33291f), w * .09f, y + 5, w * .82f, h * .14f)
    pixelRect(wood, w * .09f, y, w * .82f, h * .12f)
    for (i in 0..7) pixelRect(Color(0xffaa7c50), w * (.12f + i * .10f), y + h * .025f, w * .07f, 3f)
    for (x in listOf(.17f, .76f)) {
        pixelRect(iron, w * x, y - 2, w * .045f, h * .13f)
        pixelRect(edge, w * x + 2, y + 2, 2f, h * .10f)
    }
    val signal = t.phase == TrapPhase.ACTIVE && t.signal()
    drawCircle(if (signal) mint else amber, w * .035f, Offset(w * .50f, h * .09f))
    if (signal) for (i in 0..3) pixelRect(mint, w * (.40f + i * .055f), h * .73f, 10f, 3f)
}
