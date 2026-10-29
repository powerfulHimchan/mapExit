package com.powerfulhimchan.mapexit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.min
import kotlin.math.roundToInt

internal fun DrawScope.pixelRect(color: Color, x: Float, y: Float, w: Float, h: Float) {
    if (w > 0f && h > 0f)
        drawRect(
            color,
            Offset(x.roundToInt().toFloat(), y.roundToInt().toFloat()),
            Size(
                w.roundToInt().coerceAtLeast(1).toFloat(),
                h.roundToInt().coerceAtLeast(1).toFloat(),
            ),
        )
}

internal fun spriteUnit(width: Float) = (width / 32).toInt().coerceAtLeast(1).toFloat()

internal fun spriteHeight(type: String, width: Float) =
    (FineSprites.all[type]?.height ?: 48) * spriteUnit(width)

@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.sprite(type: String, x: Float, y: Float, width: Float, color: Color = pale) {
    val art = FineSprites.all[type] ?: FineSprites.all.getValue("npc")
    val unit = spriteUnit(width)
    drawImage(
        art.bitmap,
        srcSize = IntSize(art.width, art.height),
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize((art.width * unit).toInt(), (art.height * unit).toInt()),
        filterQuality = FilterQuality.None,
    )
}

internal fun gearSprite(kind: Kind): String =
    when (kind) {
        Kind.SWORD -> "sword"
        Kind.SPEAR -> "spear"
        Kind.HAMMER -> "hammer"
        Kind.CLAW -> "claw"
        Kind.WHIP -> "whip"
        Kind.SHIELD -> "shield"
        Kind.HEAD -> "head"
        Kind.BODY -> "body"
        Kind.PANTS -> "pants"
        Kind.BOOTS -> "boots"
        Kind.RING -> "ring"
        Kind.NECKLACE -> "necklace"
        Kind.POTION -> "potion"
    }

internal data class HeroPose(val x: Float, val y: Float, val unit: Float)

internal fun DrawScope.adventurer(
    s: State,
    x: Float,
    baseline: Float,
    width: Float,
    back: Boolean = true,
): HeroPose {
    val unit = spriteUnit(width)
    val actual = unit * 32
    val y = baseline - unit * 48
    contactShadow(x, baseline + 3, actual)
    sprite(if (back) "heroBack" else "hero", x, y, actual)
    if (back) {
        val weapon = Equipment.equipped(s).firstOrNull { it.weapon }
        weapon?.let {
            val type = gearSprite(it.kind)
            val drawWidth = unit * 26
            val scale = spriteUnit(drawWidth)
            val gripX = if (it.kind == Kind.WHIP) 7 else 16
            val gripY = if (it.kind == Kind.SPEAR) 40 else if (it.kind == Kind.WHIP) 25 else 27
            sprite(type, x + unit * 27 - gripX * scale, y + unit * 31 - gripY * scale, drawWidth)
        }
        if (Equipment.equipped(s).any { it.kind == Kind.SHIELD }) {
            sprite("shield", x - unit * 5, y + unit * 24, unit * 22)
        }
    }
    return HeroPose(x, y, unit)
}

internal fun DrawScope.drawScene(
    s: State,
    clock: Long = 0,
    effect: String = "",
    effectAt: Long = 0L,
    camera: DungeonViewPose? = null,
) {
    drawRect(ink)
    when {
        s.mode == Mode.ENCOUNTER -> drawEncounter(s, clock)
        s.mode in listOf(Mode.TRAP, Mode.TRAP_GUIDE) -> s.trap?.let { drawTrap(it) }
        s.enemy != null || (s.mode == Mode.VICTORY && s.battleResult != null) ->
            drawBattle(s, clock, effect, effectAt)
        s.floor == 0 || s.mode == Mode.INHERIT -> drawCamp(s, clock)
        else -> drawDungeonPerspective(s, clock, camera)
    }
}

private fun DrawScope.drawCamp(s: State, clock: Long) {
    val w = size.width
    val h = size.height
    masonry(0f, 0f, w, h * .66f, Color(0xff293d47))
    stoneFloor(.59f)
    pixelRect(Color(0xff141f29), w * .26f, h * .14f, w * .48f, h * .55f)
    pixelRect(Color(0xff71817f), w * .23f, h * .12f, w * .54f, 8f)
    for (x in listOf(.23f, .74f)) {
        pixelRect(Color(0xff354c59), w * x, h * .16f, w * .03f, h * .52f)
        pixelRect(Color(0xff8a9791), w * x, h * .16f, 2f, h * .52f)
        for (row in 0..4) pixelRect(line, w * x, h * (.18f + row * .10f), w * .03f, 2f)
    }
    for (i in 0..8) {
        val x = w * .29f + i * w * .051f
        pixelRect(Color(0xff3a342d), x, h * .20f, 4f, h * .45f)
        pixelRect(Color(0xffc4a46a), x, h * .20f, 1f, h * .45f)
    }
    pixelRect(Color(0xff8d7150), w * .26f, h * .29f, w * .48f, 4f)
    pixelRect(Color(0xff476b5c), w * .14f, h * .22f, w * .06f, h * .30f)
    pixelRect(mint, w * .15f, h * .25f, 2f, h * .21f)
    pixelRect(amber, w * .14f, h * .21f, w * .06f, 2f)
    torch(w * .10f, h * .38f, clock)
    torch(w * .86f, h * .38f, clock + 170)
    val heroWidth = 68f
    adventurer(s, w * .42f, h * .92f, heroWidth, false)
    sprite("chest", w * .08f, h * .78f, 68f)
    val npcWidth = 60f
    sprite("merchant", w * .77f, h * .88f - spriteHeight("merchant", npcWidth), npcWidth)
    if (s.mode == Mode.INHERIT) {
        val candidates = s.inventory.filter { !it.inherited && it.kind != Kind.POTION }.take(3)
        candidates.forEachIndexed { i, gear ->
            val x = w * (.26f + i * .18f)
            pixelRect(amber.copy(alpha = .20f), x - 4, h * .22f - 4, 40f, 40f)
            sprite(gearSprite(gear.kind), x, h * .22f, 32f)
        }
    }
}

internal fun DrawScope.arena(clock: Long, horizon: Float = .45f) {
    val w = size.width
    val h = size.height
    masonry(0f, 0f, w, h * horizon, Color(0xff30434b))
    for (x in listOf(.06f, .88f)) {
        pixelRect(Color(0xff182b35), w * x + 3, h * .08f, w * .05f, h * (horizon - .08f))
        pixelRect(Color(0xff566b72), w * x, h * .08f, w * .045f, h * (horizon - .08f))
        pixelRect(Color(0xff8a9792), w * x, h * .08f, 2f, h * (horizon - .08f))
        for (row in 0..6) pixelRect(line, w * x, h * (.10f + row * .05f), w * .045f, 2f)
    }
    stoneFloor(horizon)
    pixelRect(Color(0xff101d26), 0f, h * horizon - 3, w, 5f)
    pixelRect(Color(0xff66736d), 0f, h * horizon - 4, w, 1f)
    torch(w * .15f, h * .29f, clock)
    torch(w * .82f, h * .29f, clock + 170)
}

private fun DrawScope.drawBattle(s: State, clock: Long, effect: String, effectAt: Long) {
    val e = s.enemy ?: s.battleResult?.enemy ?: return
    val w = size.width
    val h = size.height
    arena(clock)
    val action = effectAt > 0 && clock - effectAt in 0..180
    val shift =
        if (action && effect in listOf("attack", "strong")) 12f
        else if (action && effect == "dodge") -14f else 0f
    val hero = adventurer(s, w * .18f + shift, h * .90f, min(w * .34f, h * .32f))
    val type =
        when {
            e.boss -> "boss"
            e.name.contains("기사") -> "knight"
            e.name.contains("박쥐") -> "bat"
            else -> "slime"
        }
    val width = min(w * (if (e.boss) .34f else .28f), h * (if (e.boss) .26f else .20f))
    val unit = spriteUnit(width)
    val ex = w * .64f
    val baseline = h * (if (type == "bat") .57f else .73f)
    val bob = if ((clock / 320) % 2 == 0L) 0f else 1f
    val ey = baseline - spriteHeight(type, width) + bob
    contactShadow(ex, h * .74f, unit * 32)
    if (s.mode != Mode.VICTORY) sprite(type, ex, ey, width)
    else {
        pixelRect(Color(0xff3c524a), ex, baseline - 5, unit * 32, 5f)
        for (i in 0..6) pixelRect(amber, ex + i * 7, baseline - 9 - (i % 3) * 3, 1f, 2f)
        sprite("coin", ex + unit * 12, baseline - 35, 32f)
    }
    if (action && effect in listOf("attack", "strong")) {
        val color = if (effect == "strong") amber else pale
        for (i in 0..12) {
            pixelRect(color, ex + unit * 5 + i * 4, ey + unit * 10 - i * 3, 2f, 6f)
            if (i % 2 == 0)
                pixelRect(Color.White, ex + unit * 5 + i * 4 + 2, ey + unit * 10 - i * 3, 1f, 3f)
        }
    }
    if (action && effect == "guard")
        for (i in 0..6) {
            pixelRect(mint, hero.x + hero.unit * 31, hero.y + hero.unit * 20 + i * 6, 2f, 4f)
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

private fun DrawScope.glyph(letter: Char, x: Float, y: Float, unit: Float, color: Color) {
    val rows =
        when (letter) {
            'A' -> listOf("01110", "11011", "11011", "11111", "11011", "11011", "11011")
            'B' -> listOf("11110", "11011", "11011", "11110", "11011", "11011", "11110")
            '+' -> listOf("00000", "00100", "00100", "11111", "00100", "00100", "00000")
            else -> listOf("01110", "11011", "00011", "00110", "00100", "00000", "00100")
        }
    rows.forEachIndexed { row, s ->
        s.forEachIndexed { col, c ->
            if (c == '1') pixelRect(color, x + col * unit, y + row * unit, unit, unit)
        }
    }
}

private fun DrawScope.plate(x: Float, y: Float, w: Float, h: Float, accent: Color = line) {
    pixelRect(Color(0xff101c25), x, y, w, h)
    pixelRect(Color(0xff53656d), x + 2, y + 2, w - 4, 2f)
    pixelRect(Color(0xff354953), x + 2, y + 4, 2f, h - 8)
    pixelRect(accent, x + 4, y + h - 4, w - 8, 1f)
    for (px in listOf(x + 5, x + w - 7)) for (py in listOf(y + 6, y + h - 8)) {
        pixelRect(amber, px, py, 2f, 2f)
        pixelRect(Color(0xff624c32), px + 1, py + 1, 1f, 1f)
    }
}

private fun DrawScope.drawTrap(t: TrapState) {
    val w = size.width
    val h = size.height
    masonry(0f, 0f, w, h, Color(0xff263942))
    for (x in listOf(.04f, .94f)) {
        pixelRect(Color(0xff101c25), w * x, h * .06f, 8f, h * .88f)
        for (i in 0..15) {
            pixelRect(Color(0xff56636a), w * x, h * .07f + i * h * .055f, 5f, 10f)
            pixelRect(Color(0xff83908c), w * x + 1, h * .07f + i * h * .055f, 1f, 6f)
        }
    }
    plate(w * .08f, h * .14f, w * .84f, h * .69f)
    if (t.phase == TrapPhase.RESULT) {
        val c = if (t.success) mint else red
        for (i in 0..3) {
            val inset = i * 7f
            pixelRect(
                c.copy(alpha = .08f + i * .025f),
                w * .24f + inset,
                h * .28f + inset,
                w * .52f - inset * 2,
                h * .39f - inset * 2,
            )
        }
        return
    }
    if (t.phase == TrapPhase.PREPARE) {
        for (i in 0..2) plate(
            w * (.30f + i * .14f),
            h * .37f,
            w * .10f,
            h * .16f,
            if (t.elapsedMs > i * 330) amber else line,
        )
        return
    }
    when (t.type) {
        0,
        3 -> {
            val reveal = t.type == 0 || t.phase == TrapPhase.REVEAL
            val cell = w * .78f / t.pattern.size
            t.pattern.forEachIndexed { i, b ->
                val x = w * .11f + i * cell
                val tileW = cell - 6
                val color = if (i < t.progress) mint else if (reveal) amber else muted
                plate(x, h * .36f, tileW, h * .23f, color)
                val unit = min(tileW / 9, h * .23f / 11).toInt().coerceAtLeast(1).toFloat()
                glyph(
                    if (i < t.progress) '+' else if (reveal) if (b == 0) 'A' else 'B' else '?',
                    x + (tileW - unit * 5) / 2,
                    h * .36f + (h * .23f - unit * 7) / 2,
                    unit,
                    color,
                )
            }
        }
        1 -> {
            plate(w * .10f, h * .39f, w * .80f, h * .19f)
            pixelRect(
                mint.copy(alpha = .7f),
                w * (.12f + .76f * (t.target - t.targetWidth / 2).toFloat()),
                h * .42f,
                w * .76f * t.targetWidth.toFloat(),
                h * .12f,
            )
            for (i in 0..20) pixelRect(muted, w * (.12f + i * .038f), h * .57f, 1f, 3f)
            val x = w * (.12f + .76f * t.position().toFloat())
            pixelRect(amber, x, h * .37f, 3f, h * .23f)
            pixelRect(pale, x + 1, h * .39f, 1f, h * .18f)
        }
        2 -> {
            val count = t.required
            val cell = w * .72f / count
            for (i in 0 until count) {
                val x = w * .14f + i * cell
                plate(x, h * .37f, cell - 5, h * .23f, if (i < t.progress) mint else amber)
                if (i < t.progress) pixelRect(mint, x + 5, h * .43f, cell - 15, h * .10f)
            }
        }
        4 -> {
            val c = if (t.signal()) mint else amber
            plate(w * .30f, h * .29f, w * .40f, h * .38f, c)
            val unit = min(w * .40f / 10, h * .38f / 11).toInt().coerceAtLeast(1).toFloat()
            glyph(
                if (t.pattern[0] == 0) 'A' else 'B',
                w * .50f - unit * 2.5f,
                h * .48f - unit * 3.5f,
                unit,
                c,
            )
            if (t.signal())
                for (i in 0..3) pixelRect(mint, w * (.24f + i * .16f), h * .71f, w * .05f, 3f)
        }
    }
    if (t.phase == TrapPhase.ACTIVE) {
        pixelRect(line, w * .14f, h * .77f, w * .72f, 4f)
        pixelRect(
            amber,
            w * .14f,
            h * .77f,
            w * .72f * (1 - t.elapsedMs.toFloat() / t.limitMs).coerceIn(0f, 1f),
            3f,
        )
    }
}
