package com.powerfulhimchan.mapexit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
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
    (GearSprites.get(type)?.height ?: 48) * spriteUnit(width)

@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.sprite(
    type: String,
    x: Float,
    y: Float,
    width: Float,
    color: Color = pale,
    flash: Color? = null,
    articulated: Boolean = false,
    opacity: Float = 1f,
) {
    val art = GearSprites.get(type) ?: FineSprites.all.getValue("npc")
    val unit = if (type.startsWith("gear:")) (width / 32).coerceAtLeast(.25f) else spriteUnit(width)
    fun DrawScope.paint() {
        drawImage(
            art.bitmap,
            srcSize = IntSize(art.width, art.height),
            dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
            dstSize = IntSize((art.width * unit).toInt(), (art.height * unit).toInt()),
            filterQuality = FilterQuality.None,
            alpha = opacity.coerceIn(0f,1f),
            colorFilter =
                flash?.let {
                    androidx.compose.ui.graphics.ColorFilter.tint(
                        it,
                        androidx.compose.ui.graphics.BlendMode.SrcAtop,
                    )
                },
        )
    }
    if (articulated) {
        val right = type == "heroBack"
        clipRect(
            x + (if (right) 24 else 4) * unit,
            y + 23 * unit,
            x + (if (right) 29 else 9) * unit,
            y + 34 * unit,
            clipOp = androidx.compose.ui.graphics.ClipOp.Difference,
        ) {
            paint()
        }
    } else paint()
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
    armed: Boolean = true,
    hitTint: Color? = null,
    articulated: Boolean = false,
    shadow: Boolean = true,
    stride: Float = 0f,
): HeroPose {
    val unit = spriteUnit(width)
    val actual = unit * 32
    val y = baseline - unit * 48
    if(shadow) contactShadow(x, baseline + 3, actual)
    combatBody(if (back) "heroBack" else "hero",x,y,actual,hitTint,articulated,stride)

    Equipment.equipped(s).forEach { gear ->
        val area = when (gear.kind) {
            Kind.HEAD -> Triple(8f, 0f, 16f)
            Kind.BODY -> Triple(6f, 14f, 20f)
            Kind.PANTS -> Triple(8f, 30f, 17f)
            Kind.BOOTS -> Triple(7f, 38f, 19f)
            Kind.NECKLACE -> Triple(12f, 20f, 8f)
            Kind.RING -> if (s.equipment[Slot.RING1] == gear.id) Triple(23f, 30f, 5f) else Triple(4f, 30f, 5f)
            else -> null
        }
        area?.let { (xx, yy, ww) -> sprite(gearSprite(gear), x + xx * unit, y + yy * unit, ww * unit, flash = hitTint) }
    }
    if (back && armed) {
        val weapon = Equipment.equipped(s).firstOrNull { it.weapon }
        weapon?.let {
            val type = gearSprite(it)
            val drawWidth = unit * 26 * GearAppearance.heldScale(it.kind)
            val scale = drawWidth / 32
            val gripX = if (it.kind == Kind.WHIP) 7 else 16
            val gripY = if (it.kind == Kind.SPEAR) 40 else if (it.kind == Kind.WHIP) 25 else 27
            sprite(type, x + unit * 27 - gripX * scale, y + unit * 31 - gripY * scale, drawWidth)
        }
        Equipment.equipped(s).firstOrNull { it.kind == Kind.SHIELD }?.let { shield ->
            sprite(gearSprite(shield), x - unit * 5, y + unit * 24, unit * 22)
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
    battle: BattleVisual? = null,
) {
    drawRect(ink)
    when {
        s.mode == Mode.ENCOUNTER -> drawEncounter(s, clock)
        s.mode in listOf(Mode.TRAP, Mode.TRAP_GUIDE) -> s.trap?.let { drawObstacle(s, clock) }
        s.enemy != null || (s.mode == Mode.VICTORY && s.battleResult != null) ->
            drawAnimatedBattle(s, clock, battle)
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
            sprite(gearSprite(gear), x, h * .22f, 32f)
        }
    }
}

internal fun DrawScope.arena(clock: Long, horizon: Float = .45f, floor: Int = 1) {
    val theme = DungeonTheme.forFloor(floor)
    val w = size.width
    val h = size.height
    masonry(0f, 0f, w, h * horizon, Color(theme.wall), theme)
    for (x in listOf(.06f, .88f)) {
        pixelRect(Color(theme.grout), w * x + 3, h * .08f, w * .05f, h * (horizon - .08f))
        pixelRect(Color(theme.wall), w * x, h * .08f, w * .045f, h * (horizon - .08f))
        pixelRect(Color(0xff8a9792), w * x, h * .08f, 2f, h * (horizon - .08f))
        for (row in 0..6) pixelRect(line, w * x, h * (.10f + row * .05f), w * .045f, 2f)
    }
    stoneFloor(horizon, theme)
    pixelRect(Color(0xff101d26), 0f, h * horizon - 3, w, 5f)
    pixelRect(Color(0xff66736d), 0f, h * horizon - 4, w, 1f)
    torch(w * .15f, h * .29f, clock, Color(theme.light))
    torch(w * .82f, h * .29f, clock + 170, Color(theme.light))
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
                    Event.ELITE -> amber
                    Event.CHEST -> amber
                    Event.HEAL -> mint
                    Event.MERCHANT -> Color(0xFFBA9DDA)
                    Event.NPC -> Color(0xFF9AC3E4)
                    Event.EXIT -> Color.White
                    Event.TRAP -> Color(0xFFE6A36A)
                    Event.PORTAL -> Color(0xFFBA9DDA)
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
