package com.powerfulhimchan.mapexit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.min
import kotlin.math.roundToInt

private fun DrawScope.pixelRect(color: Color, x: Float, y: Float, w: Float, h: Float) {
    drawRect(color, Offset(x, y), Size(w, h))
}

private fun DrawScope.poly(color: Color, a: Offset, b: Offset, c: Offset, d: Offset) {
    val pts = listOf(a, b, c, d)
    val p = Path()
    p.moveTo(a.x, a.y)
    pts.drop(1).forEach { p.lineTo(it.x, it.y) }
    p.close()
    drawPath(p, color)
    clipPath(p) { masonry(0f, 0f, size.width, size.height, color) }
    drawPath(p, line, style = Stroke(1f))
}

internal fun DrawScope.drawScene(
    s: State,
    clock: Long = 0,
    effect: String = "",
    effectAt: Long = 0L,
) {
    drawRect(ink)
    if (s.mode == Mode.TRAP) {
        drawTrap(s.trap ?: return)
        return
    }
    if (s.enemy != null) {
        drawBattle(s, clock, effect, effectAt)
        return
    }
    if (s.floor == 0 || s.mode == Mode.INHERIT) {
        val w = size.width
        val h = size.height
        pixelRect(Color(0xFF142838), 0f, h * .58f, w, h * .42f)
        for (i in 0..9) {
            val x = i * w / 9
            pixelRect(Color(0xFF213746), x, h * .15f + (i % 3) * h * .04f, w * .04f, h * .46f)
        }
        pixelRect(line, w * .18f, h * .31f, w * .64f, h * .33f)
        pixelRect(ink, w * .28f, h * .39f, w * .44f, h * .25f)
        pixelRect(amber, w * .21f, h * .25f, w * .58f, h * .035f)
        // The camp gate, paving, supply crates and pennants remain legible at 240px.
        for (row in 0..5) for (col in 0..15) {
            val px = col * 17f + (row % 2) * 8f
            pixelRect(Color(0xFF263A43), px, h * .67f + row * 9f, 15f, 1f)
            pixelRect(Color(0xFF172730), px, h * .67f + row * 9f, 1f, 8f)
        }
        pixelRect(Color(0xFF53626A), w * .2f, h * .28f, 8f, h * .37f)
        pixelRect(Color(0xFF53626A), w * .77f, h * .28f, 8f, h * .37f)
        for (i in 0..5) pixelRect(
            amber.copy(alpha = .4f),
            w * .30f + i * 16f,
            h * .38f,
            2f,
            h * .23f,
        )
        sprite("chest", w * .09f, h * .66f, 34f, amber)
        sprite("npc", w * .75f, h * .53f, 30f, Color(0xFFBF9380))
        sprite("hero", w * .40f, h * .45f, 34f, mint)
        if (s.mode == Mode.INHERIT) {
            for (i in 0..2) pixelRect(amber, w * (.25f + i * .22f), h * .20f, w * .09f, h * .06f)
        }
        return
    }
    val m = s.dungeon ?: return
    val w = size.width
    val h = size.height
    pixelRect(Color(0xFF192530), 0f, h * .5f, w, h * .5f)
    for (i in 0..9) {
        val y = h * .52f + h * .48f * (i * i / 81f)
        drawLine(Color(0xFF34414A), Offset(0f, y), Offset(w, y), 1f)
        for (k in -4..4) drawLine(
            Color(0xFF293942),
            Offset(w * .5f + k * (y - h * .48f) * .7f, y),
            Offset(w * .5f + k * (y - h * .48f) * .85f, (y + 12).coerceAtMost(h)),
            1f,
        )
    }
    for (i in 0..40) {
        val x = ((i * 73 + m.x * 13) % 239).toFloat()
        val y = h * .57f + ((i * 19 + m.y * 7) % 100) / 100f * h * .42f
        pixelRect(if (i % 4 == 0) Color(0xFF58614D) else Color(0xFF24333C), x, y, 2f + (i % 3), 1f)
    }
    for (depth in 4 downTo 0) {
        val near = (.5f - (.48f / (depth + 1)))
        val far = (.5f - (.48f / (depth + 2)))
        val nx = w * near
        val ny = h * near
        val fx = w * far
        val fy = h * far
        val xx = m.x + MapGenerator.dx[m.direction] * depth
        val yy = m.y + MapGenerator.dy[m.direction] * depth
        val left = (m.direction + 3) % 4
        val right = (m.direction + 1) % 4
        if (m.cell(xx + MapGenerator.dx[left], yy + MapGenerator.dy[left]).wall)
            poly(
                Color(0xFF29404F),
                Offset(nx, ny),
                Offset(fx, fy),
                Offset(fx, h - fy),
                Offset(nx, h - ny),
            )
        if (m.cell(xx + MapGenerator.dx[right], yy + MapGenerator.dy[right]).wall)
            poly(
                Color(0xFF223642),
                Offset(w - nx, ny),
                Offset(w - fx, fy),
                Offset(w - fx, h - fy),
                Offset(w - nx, h - ny),
            )
        drawLine(line, Offset(fx, h - fy), Offset(w - fx, h - fy), 1f)
        val front = m.cell(xx + MapGenerator.dx[m.direction], yy + MapGenerator.dy[m.direction])
        if (front.wall) {
            pixelRect(Color(0xFF344D5C), fx, fy, w - 2 * fx, h - 2 * fy)
            masonry(fx, fy, w - 2 * fx, h - 2 * fy, Color(0xFF344D5C))
            drawRect(line, Offset(fx, fy), Size(w - 2 * fx, h - 2 * fy), style = Stroke(2f))
            for (k in 1..3) drawLine(
                line,
                Offset(fx, fy + (h - 2 * fy) * k / 4),
                Offset(w - fx, fy + (h - 2 * fy) * k / 4),
                1f,
            )
        }
    }
    val cell = m.here()
    if (!cell.resolved) {
        val type =
            when (cell.event) {
                Event.CHEST -> "chest"
                Event.MERCHANT,
                Event.NPC -> "npc"
                Event.HEAL -> "heal"
                Event.EXIT -> "door"
                else -> null
            }
        if (type != null)
            sprite(
                type,
                w * .31f,
                h * .47f,
                w * .38f,
                if (cell.event == Event.HEAL) mint else amber,
            )
    }
    val leftWall = (m.direction + 3) % 4
    val rightWall = (m.direction + 1) % 4
    if (m.cell(m.x + MapGenerator.dx[leftWall], m.y + MapGenerator.dy[leftWall]).wall)
        torch(w * .105f, h * .33f, clock)
    if (m.cell(m.x + MapGenerator.dx[rightWall], m.y + MapGenerator.dy[rightWall]).wall)
        torch(w * .86f, h * .33f, clock + 240)
    pixelRect(Color.Black.copy(alpha = .22f), 0f, 0f, w, 5f)
    pixelRect(Color.Black.copy(alpha = .18f), 0f, 0f, 4f, h)
    pixelRect(Color.Black.copy(alpha = .18f), w - 4f, 0f, 4f, h)
    pixelRect(amber, w * .48f, h * .94f, w * .04f, 2f)
}

private fun DrawScope.drawBattle(s: State, clock: Long, effect: String, effectAt: Long) {
    val e = s.enemy ?: return
    val w = size.width
    val h = size.height
    masonry(0f, 0f, w, h * .64f, Color(0xFF23313C))
    for (column in listOf(.07f, .87f)) {
        pixelRect(Color(0xFF3A4850), w * column, h * .14f, w * .06f, h * .49f)
        for (r in 0..7) pixelRect(line, w * column, h * (.15f + r * .06f), w * .06f, 1f)
    }
    pixelRect(Color(0xFF1B2935), 0f, h * .65f, w, h * .35f)
    for (row in 0..7) for (col in 0..11) {
        val x = col * 23f + (row % 2) * 11f
        val y = h * .65f + row * 14f
        pixelRect(Color(0xFF3C4850), x, y, 21f, 1f)
        pixelRect(Color(0xFF111E28), x, y, 1f, 13f)
    }
    torch(w * .16f, h * .25f, clock)
    torch(w * .78f, h * .25f, clock + 160)
    pixelRect(line, w * .1f, h * .08f, w * .8f, h * .045f)
    pixelRect(
        if (e.boss) amber else red,
        w * .1f,
        h * .08f,
        w * .8f * (e.hp / e.maxHp).toFloat().coerceIn(0f, 1f),
        h * .045f,
    )
    val bob = if ((clock / 320) % 2 == 0L) 0f else 1f
    val actionVisible = effectAt > 0 && clock - effectAt in 0..160
    val shift =
        if (actionVisible && effect in listOf("attack", "strong")) 6f
        else if (actionVisible && effect == "dodge") -7f else 0f
    val heroWidth = min(w * .29f, h * .25f)
    val heroUnit = (heroWidth / 16).toInt().coerceAtLeast(1).toFloat()
    val heroX = w * .13f + shift
    val heroY = h * .82f - heroUnit * 24 + bob
    drawOval(
        Color.Black.copy(alpha = .4f),
        Offset(heroX - 3, h * .83f),
        Size(heroUnit * 16 + 6, 6f),
    )
    sprite("hero", heroX, heroY, heroWidth, mint)
    // Draw the equipped hand item rather than a generic attack marker.
    val weapon = Equipment.equipped(s).firstOrNull { it.weapon }
    val wx = heroX + heroUnit * 14
    val wy = heroY + heroUnit * 14 - 20f
    val metal = Color(0xFFCDD9D5)
    when (weapon?.kind) {
        null -> Unit
        Kind.HAMMER -> {
            pixelRect(amber, wx, wy, 2f, 26f)
            pixelRect(metal, wx - 7f, wy - 3f, 16f, 9f)
        }
        Kind.SPEAR -> {
            pixelRect(amber, wx, wy - 10f, 2f, 38f)
            pixelRect(metal, wx - 2f, wy - 14f, 6f, 7f)
        }
        Kind.CLAW -> repeat(3) { pixelRect(metal, wx + it * 3, wy, 2f, 13f) }
        Kind.WHIP -> repeat(5) { pixelRect(amber, wx + it * 3, wy + (it % 2) * 4, 4f, 2f) }
        else -> {
            pixelRect(metal, wx, wy, 3f, 23f)
            pixelRect(amber, wx - 3f, wy + 19f, 9f, 2f)
        }
    }
    if (Equipment.equipped(s).any { it.kind == Kind.SHIELD }) {
        pixelRect(Color(0xFF577985), heroX - heroUnit, heroY + heroUnit * 12, 12f, 17f)
        pixelRect(amber, heroX - heroUnit + 5, heroY + heroUnit * 12 + 5, 3f, 6f)
    }
    val enemyType =
        if (e.boss) "boss"
        else if (e.name.contains("기사")) "knight" else if (e.name.contains("박쥐")) "bat" else "slime"
    val enemyWidth = min(w * .34f, h * .23f)
    val enemyUnit = (enemyWidth / 16).toInt().coerceAtLeast(1).toFloat()
    val enemyRows =
        when (enemyType) {
            "slime" -> 12
            "bat" -> 10
            else -> 24
        }
    val enemyX = w * .56f
    val enemyY = h * (if (enemyType == "bat") .64f else .81f) - enemyRows * enemyUnit + bob
    drawOval(
        Color.Black.copy(alpha = .4f),
        Offset(enemyX - 3, h * .83f),
        Size(enemyUnit * 16 + 6, 6f),
    )
    sprite(enemyType, enemyX, enemyY, enemyWidth, if (e.boss) red else Color(0xFF9ACB94))
    if (actionVisible && effect in listOf("attack", "strong")) {
        repeat(if (effect == "strong") 9 else 6) { n ->
            pixelRect(
                if (effect == "strong") amber else pale,
                enemyX + enemyUnit * 3 + n * 3,
                enemyY + enemyUnit * 8 - n * 3,
                3f,
                5f,
            )
        }
    }
    if (actionVisible && effect == "guard") {
        repeat(5) { n ->
            pixelRect(mint, heroX + heroUnit * 16, heroY + heroUnit * 9 + n * 6, 2f, 4f)
        }
    }
}

internal fun DrawScope.drawMap(s: State) {
    val m = s.dungeon
    if (m == null) {
        val w = size.width
        val h = size.height
        for (y in 0..8) for (x in 0..8) if ((x + y) % 3 == 0)
            pixelRect(line, w * .08f + x * w * .10f, h * .12f + y * h * .08f, w * .025f, w * .025f)
        return
    }
    val radius = 4
    val unit = min(size.width / 9, size.height / 9)
    val startX = m.x - radius
    val startY = m.y - radius
    val ox = (size.width - 9 * unit) / 2 - startX * unit
    val oy = (size.height - 9 * unit) / 2 - startY * unit
    for (y in startY..m.y + radius) for (x in startX..m.x + radius) {
        val c = m.cell(x, y)
        val charm = Equipment.charm(s) == Charm.MAP && MapGenerator.charmVisible(m, x, y)
        if (!c.seen && !charm) continue
        val px = ox + x * unit
        val py = oy + y * unit
        pixelRect(if (c.wall) line else Color(0xFF1A303A), px, py, unit - 1, unit - 1)
        if (!c.wall && !c.resolved) {
            val color =
                when (c.event) {
                    Event.MONSTER -> red
                    Event.CHEST -> amber
                    Event.HEAL -> mint
                    Event.MERCHANT -> Color(0xFFBA9DDA)
                    Event.NPC -> Color(0xFF9AC3E4)
                    Event.EXIT -> Color.White
                    Event.TRAP -> Color(0xFFE6A36A)
                    else -> null
                }
            if (color != null)
                pixelRect(color, px + unit * .25f, py + unit * .25f, unit * .5f, unit * .5f)
        }
    }
    val x = ox + (m.x + .5f) * unit
    val y = oy + (m.y + .5f) * unit
    drawCircle(mint, unit * .42f, Offset(x, y))
    drawLine(
        Color.White,
        Offset(x, y),
        Offset(
            x + MapGenerator.dx[m.direction] * unit * .8f,
            y + MapGenerator.dy[m.direction] * unit * .8f,
        ),
        2f,
    )
}

private fun DrawScope.drawTrap(t: TrapState) {
    val w = size.width
    val h = size.height
    masonry(0f, 0f, w, h, Color(0xFF172731))
    for (i in 0..12) {
        pixelRect(Color(0xFF4A5556), w * .05f, i * 23f, 3f, 14f)
        pixelRect(Color(0xFF4A5556), w * .93f, i * 23f + 11, 3f, 14f)
    }
    pixelRect(ink.copy(alpha = .7f), w * .07f, h * .15f, w * .86f, h * .70f)
    if (t.phase == TrapPhase.RESULT) {
        val color = if (t.success) mint else red
        pixelRect(color, w * .25f, h * .30f, w * .5f, h * .35f)
        pixelRect(ink, w * .35f, h * .40f, w * .3f, h * .15f)
        return
    }
    if (t.phase == TrapPhase.PREPARE) {
        for (i in 0..2) pixelRect(
            if (t.elapsedMs > i * 400) amber else line,
            w * (.22f + i * .23f),
            h * .4f,
            w * .10f,
            h * .13f,
        )
        return
    }
    when (t.type) {
        0,
        3 -> {
            val reveal = t.type == 0 || t.phase == TrapPhase.REVEAL
            val n = t.pattern.size
            val box = w / (n + 2)
            t.pattern.forEachIndexed { i, b ->
                val x = box * (i + 1)
                val color = if (i < t.progress) mint else if (reveal && b == 1) amber else line
                pixelRect(color, x, h * .38f, box * .8f, box * 1.1f)
                if (reveal) {
                    if (b == 0) {
                        pixelRect(ink, x + box * .2f, h * .38f + box * .25f, box * .4f, box * .18f)
                    } else {
                        pixelRect(ink, x + box * .2f, h * .38f + box * .2f, box * .4f, box * .14f)
                        pixelRect(ink, x + box * .2f, h * .38f + box * .55f, box * .4f, box * .14f)
                    }
                }
            }
        }
        1 -> {
            pixelRect(line, w * .08f, h * .42f, w * .84f, h * .15f)
            pixelRect(
                mint,
                w * (.08f + .84f * (t.target - t.targetWidth / 2).toFloat()),
                h * .42f,
                w * .84f * t.targetWidth.toFloat(),
                h * .15f,
            )
            pixelRect(
                amber,
                w * (.08f + .84f * t.position().toFloat()),
                h * .37f,
                w * .025f,
                h * .25f,
            )
        }
        2 -> {
            for (i in 0 until t.required) {
                val x = w * .12f + (i % 5) * w * .16f
                val y = h * .20f + (i / 5) * h * .16f
                pixelRect(if (i < t.progress) mint else line, x, y, w * .11f, h * .1f)
            }
        }
        4 -> {
            val c = if (t.signal()) mint else amber
            pixelRect(c, w * .3f, h * .25f, w * .4f, h * .4f)
            pixelRect(ink, w * .4f, h * .35f, w * .2f, h * .2f)
        }
    }
    if (t.phase == TrapPhase.ACTIVE) {
        pixelRect(line, w * .1f, h * .85f, w * .8f, h * .025f)
        pixelRect(
            amber,
            w * .1f,
            h * .85f,
            w * .8f * (1 - t.elapsedMs.toFloat() / t.limitMs).coerceIn(0f, 1f),
            h * .025f,
        )
    }
}

private fun DrawScope.sprite(type: String, x: Float, y: Float, width: Float, color: Color) {
    val pattern =
        when (type) {
            "hero",
            "knight",
            "boss" ->
                listOf(
                    "0000003333000000",
                    "0000333333330000",
                    "0003335553333000",
                    "0003355555533000",
                    "0003554444553000",
                    "0003541441453000",
                    "0000354444530000",
                    "0000034444300000",
                    "0000225555220000",
                    "0002222552222000",
                    "0022222552222200",
                    "0026555555556200",
                    "0026222552226200",
                    "0044222552224400",
                    "0044222552224400",
                    "0000222552220000",
                    "0000222222220000",
                    "0000222002220000",
                    "0000222002220000",
                    "0000555005550000",
                    "0000555005550000",
                    "0000111001110000",
                    "0001111001111000",
                    "0001111001111000",
                )
            "slime" ->
                listOf(
                    "0000000000000000",
                    "0000055555000000",
                    "0000556665500000",
                    "0005666666650000",
                    "0056666666665000",
                    "0566655665566500",
                    "0566611661166500",
                    "5666666666666650",
                    "5666666666666650",
                    "5666666666666650",
                    "0556665556665500",
                    "0005555555550000",
                )
            "bat" ->
                listOf(
                    "1000000000000001",
                    "1100000000000011",
                    "1550001001000551",
                    "1555015555105551",
                    "1555555665555551",
                    "0155556446555510",
                    "0011556666551100",
                    "0000155555100000",
                    "0000055555000000",
                    "0000010010000000",
                )
            "chest" ->
                listOf(
                    "0000333333330000",
                    "0033555555553300",
                    "0355666666665530",
                    "3355555555555533",
                    "3333333333333333",
                    "3555333333335553",
                    "3555333443335553",
                    "3555333443335553",
                    "3555333333335553",
                    "3555333333335553",
                    "3333333333333333",
                    "0110000000001100",
                )
            "door" ->
                listOf(
                    "0000555555550000",
                    "0055333333335500",
                    "0553333333333550",
                    "0531111111111350",
                    "0531222222221350",
                    "0531222222221350",
                    "0531222222221350",
                    "0531222222221350",
                    "0531222222321350",
                    "0531222222321350",
                    "0531222222221350",
                    "0531222222221350",
                    "0531222222221350",
                    "0531222222221350",
                    "0555555555555550",
                    "0555555555555550",
                )
            "heal" ->
                listOf(
                    "0000006666000000",
                    "0000065555600000",
                    "0000655555560000",
                    "0006655555566000",
                    "0000655555560000",
                    "0000065555600000",
                    "0000006666000000",
                    "0000003333000000",
                    "0000553333550000",
                    "0005555555550000",
                    "0055555555555000",
                    "0055555555555000",
                )
            else ->
                listOf(
                    "0000005555000000",
                    "0000555555550000",
                    "0005555555555000",
                    "0005544444455000",
                    "0000541441450000",
                    "0000044444400000",
                    "0000054444500000",
                    "0000555555550000",
                    "0005556666555000",
                    "0055556666555500",
                    "0054456666544500",
                    "0054456666544500",
                    "0000556666550000",
                    "0000055555500000",
                    "0000055005500000",
                    "0000111001110000",
                )
        }
    val u = (width / 16).toInt().coerceAtLeast(1).toFloat()
    val base = if (type == "knight") Color(0xFF8B929E) else if (type == "boss") red else color
    pattern.forEachIndexed { row, data ->
        data.forEachIndexed { col, c ->
            if (c != '0') {
                val tint =
                    when (c) {
                        '1' -> Color(0xFF111D29)
                        '2' ->
                            base.copy(
                                red = base.red * .5f,
                                green = base.green * .5f,
                                blue = base.blue * .5f,
                            )
                        '3' -> amber
                        '4' -> if (type in listOf("boss", "bat")) red else Color(0xFFDBB79D)
                        '5' -> base
                        '6' -> Color(0xFFD4E4D1).copy(alpha = .8f)
                        else -> color
                    }
                pixelRect(
                    tint,
                    (x + col * u).roundToInt().toFloat(),
                    (y + row * u).roundToInt().toFloat(),
                    u.toInt().coerceAtLeast(1).toFloat(),
                    u.toInt().coerceAtLeast(1).toFloat(),
                )
            }
        }
    }
}

private fun DrawScope.masonry(x: Float, y: Float, w: Float, h: Float, base: Color) {
    pixelRect(base, x, y, w, h)
    val brickH = 11f
    val brickW = 25f
    for (row in 0..(h / brickH).toInt()) for (col in -1..(w / brickW).toInt()) {
        val px = x + col * brickW + (row % 2) * brickW / 2
        val py = y + row * brickH
        val right = min(px + brickW - 1, x + w)
        val bottom = min(py + brickH - 1, y + h)
        val left = px.coerceAtLeast(x)
        if (right <= left || bottom <= py) continue
        pixelRect(Color(0xFF17252E), left, py, right - left, 1f)
        pixelRect(Color(0xFF17252E), left, py, 1f, bottom - py)
        pixelRect(
            Color(0xFF658075).copy(alpha = .2f),
            left + 1,
            py + 1,
            (right - left - 2).coerceAtLeast(0f),
            1f,
        )
        if ((row * 7 + col * 3) % 5 == 0) {
            pixelRect(Color(0xFF182A30), left + 5, py + 4, 3f, 1f)
            pixelRect(Color(0xFF182A30), left + 7, py + 5, 1f, 3f)
        }
        if ((row + col * 7) % 9 == 0)
            pixelRect(Color(0xFF647153).copy(alpha = .5f), left + 2, py + 7, 4f, 2f)
    }
}

private fun DrawScope.torch(x: Float, y: Float, clock: Long) {
    val flicker = ((clock / 170) % 3).toInt()
    pixelRect(Color(0xFFD99749).copy(alpha = .05f), x - 13, y - 14, 34f, 42f)
    pixelRect(Color(0xFFECAD5D).copy(alpha = .08f), x - 5, y - 8, 18f, 25f)
    pixelRect(Color(0xFF14222C), x - 2, y + 5, 11f, 14f)
    pixelRect(Color(0xFF7D6145), x + 2, y + 3, 3f, 14f)
    pixelRect(Color(0xFFF0A457), x, y - 5 - flicker, 7f, 10f + flicker)
    pixelRect(Color(0xFFFFDA8E), x + 2, y - 3 - flicker, 3f, 7f)
    pixelRect(Color(0xFFFFF0B6), x + 3, y, 2f, 3f)
    pixelRect(Color(0xFFB3BEB6), x - 1, y + 5, 9f, 2f)
}
