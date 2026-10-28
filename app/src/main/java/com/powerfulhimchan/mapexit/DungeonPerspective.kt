package com.powerfulhimchan.mapexit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.max

private fun DrawScope.quad(color: Color, a: Offset, b: Offset, c: Offset, d: Offset) {
    val points = listOf(a, b, c, d)
    val path =
        Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
    drawPath(path, color)
}

private fun DrawScope.stoneSide(color: Color, a: Offset, b: Offset, c: Offset, d: Offset) {
    stonePlane(a, b, c, d, color)
}

/** Wall slabs have lit cut faces, beveled tops and contact shadows, including passage jambs. */
internal fun DrawScope.drawDungeonPerspective(s: State, clock: Long) {
    val m = s.dungeon ?: return
    val w = size.width
    val h = size.height
    stoneFloor()
    val last =
        (0..4).firstOrNull { depth ->
            m.cell(
                    m.x + MapGenerator.dx[m.direction] * (depth + 1),
                    m.y + MapGenerator.dy[m.direction] * (depth + 1),
                )
                .wall
        } ?: 4
    for (depth in last downTo 0) {
        val near = .5f - .48f / (depth + 1)
        val far = .5f - .48f / (depth + 2)
        val nx = w * near
        val ny = h * near
        val fx = w * far
        val fy = h * far
        val t = max(3f, 18f / (depth + 1))
        val nearT = max(5f, 24f / (depth + 1))
        stonePlane(
            Offset(nx, ny),
            Offset(w - nx, ny),
            Offset(w - fx, fy),
            Offset(fx, fy),
            Color(0xff1e2e37),
            8,
        )
        val xx = m.x + MapGenerator.dx[m.direction] * depth
        val yy = m.y + MapGenerator.dy[m.direction] * depth
        for (left in listOf(true, false)) {
            val side = (m.direction + (if (left) 3 else 1)) % 4
            val wall = m.cell(xx + MapGenerator.dx[side], yy + MapGenerator.dy[side]).wall
            fun p(x: Float, y: Float) = Offset(if (left) x else w - x, y)
            if (wall) {
                stoneSide(
                    if (left) Color(0xFF354B59) else Color(0xFF263B48),
                    p(nx, ny),
                    p(fx, fy),
                    p(fx, h - fy),
                    p(nx, h - ny),
                )
                // Thick upper lip and projecting front cut surface.
                quad(
                    Color(0xFF6A7B83),
                    p(nx, ny),
                    p(fx, fy),
                    p(fx + t, fy + t),
                    p(nx + nearT, ny + nearT),
                )
                quad(
                    if (left) Color(0xFF637780) else Color(0xFF405866),
                    p(fx, fy),
                    p(fx + t, fy + t),
                    p(fx + t, h - fy - t),
                    p(fx, h - fy),
                )
                quad(
                    Color.Black.copy(alpha = .35f),
                    p(nx, h - ny),
                    p(fx, h - fy),
                    p(fx + t * 2, h - fy + 3),
                    p(nx + nearT * 2, h - ny + 3),
                )
                drawLine(Color(0xFF91A3A5), p(fx, fy), p(fx, h - fy), 1f)
            } else {
                // The opening cuts through a solid wall: near and far reveals expose its depth.
                quad(
                    Color(0xFF101C24),
                    p(nx + nearT, ny + nearT),
                    p(fx - t, fy + t),
                    p(fx - t, h - fy - t),
                    p(nx + nearT, h - ny - nearT),
                )
                quad(
                    Color(0xFF536C78),
                    p(nx, ny),
                    p(nx + nearT, ny + nearT),
                    p(nx + nearT, h - ny - nearT),
                    p(nx, h - ny),
                )
                quad(
                    Color(0xFF324A58),
                    p(fx - t, fy + t),
                    p(fx, fy),
                    p(fx, h - fy),
                    p(fx - t, h - fy - t),
                )
                quad(
                    Color(0xFF70818A),
                    p(nx, ny),
                    p(fx, fy),
                    p(fx - t, fy + t),
                    p(nx + nearT, ny + nearT),
                )
                quad(
                    Color.Black.copy(alpha = .4f),
                    p(nx, h - ny),
                    p(fx, h - fy),
                    p(fx - t, h - fy + 3),
                    p(nx + nearT, h - ny + 3),
                )
            }
        }
        if (m.cell(xx + MapGenerator.dx[m.direction], yy + MapGenerator.dy[m.direction]).wall) {
            pixelRect(Color(0xFF162A36), fx, fy, w - 2 * fx, h - 2 * fy)
            val bevel = t.coerceAtMost((w - 2 * fx) / 5)
            masonry(
                fx + bevel,
                fy + bevel,
                (w - 2 * fx - 2 * bevel).coerceAtLeast(1f),
                (h - 2 * fy - 2 * bevel).coerceAtLeast(1f),
                Color(0xFF3E5665),
            )
            quad(
                Color(0xFF849398),
                Offset(fx, fy),
                Offset(w - fx, fy),
                Offset(w - fx - bevel, fy + bevel),
                Offset(fx + bevel, fy + bevel),
            )
            quad(
                Color(0xFF5B727E),
                Offset(fx, fy),
                Offset(fx + bevel, fy + bevel),
                Offset(fx + bevel, h - fy - bevel),
                Offset(fx, h - fy),
            )
            quad(
                Color(0xFF223947),
                Offset(w - fx, fy),
                Offset(w - fx, h - fy),
                Offset(w - fx - bevel, h - fy - bevel),
                Offset(w - fx - bevel, fy + bevel),
            )
            pixelRect(Color(0xFF101C25), fx, h - fy - bevel, w - 2 * fx, bevel + 3)
        }
    }
    val cell = m.here()
    if (!cell.resolved) {
        val type =
            when (cell.event) {
                Event.CHEST -> "chest"
                Event.HEAL -> "heal"
                Event.MERCHANT -> "merchant"
                Event.NPC -> "npc"
                Event.EXIT -> "door"
                else -> null
            }
        if (type != null)
            sprite(
                type,
                w * .34f,
                h * .89f - spriteHeight(type, minOf(w * .26f, h * .22f)),
                minOf(w * .26f, h * .22f),
                if (cell.event == Event.HEAL) mint else amber,
            )
    }
    val l = (m.direction + 3) % 4
    val r = (m.direction + 1) % 4
    if (m.cell(m.x + MapGenerator.dx[l], m.y + MapGenerator.dy[l]).wall)
        torch(w * .12f, h * .34f, clock)
    if (m.cell(m.x + MapGenerator.dx[r], m.y + MapGenerator.dy[r]).wall)
        torch(w * .84f, h * .34f, clock + 240)
    pixelRect(amber, w * .48f, h * .94f, w * .04f, 2f)
}
