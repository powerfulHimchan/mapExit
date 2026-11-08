package com.powerfulhimchan.mapexit

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.powerfulhimchan.mapexit.core.*
import kotlin.math.*

/** Painted reference-matched assets keep their native detail through the existing battle rig. */
internal object MotionArtwork {
    private data class Frame(val image: ImageBitmap, val offset: IntOffset, val size: IntSize, val nativeScale: Float = 4f, val logicalX: Float = 0f)
    private data class Assets(val hero: ImageBitmap, val monsters: ImageBitmap, val gear: Bitmap, val arena: ImageBitmap)
    @Volatile private var assets: Assets? = null
    val loaded get() = assets != null
    private val heroIndex = MotionAtlas.heroPoses.withIndex().associate { it.value to it.index }
    private val monsterIndex = MotionAtlas.monsters.withIndex().associate { it.value to it.index }
    private val monsterPoseIndex = MotionAtlas.monsterPoses.withIndex().associate { it.value to it.index }
    private val gearIndex = MotionAtlas.gear.withIndex().associate { it.value to it.index }
    private val gearCache = object : LinkedHashMap<String, Frame>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Frame>?) = size > 128
    }

    @Synchronized fun load(resources: Resources) {
        if (assets != null) return
        fun decode(id: Int) = requireNotNull(BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inScaled = false }))
        assets = Assets(decode(R.drawable.combat_hero_motion).asImageBitmap(), decode(R.drawable.combat_monster_motion).asImageBitmap(), decode(R.drawable.combat_equipment_motion), decode(R.drawable.combat_arena_refined).asImageBitmap())
    }

    fun has(type: String, pose: String = "idle_0"): Boolean = when {
        type == "heroBack" -> pose in heroIndex
        monsterKey(type) in monsterIndex -> pose in monsterPoseIndex
        else -> base(type) in gearIndex
    }
    private fun base(type: String) = if (type.startsWith("gear:")) type.split(':')[1] else type
    private fun monsterKey(type: String) = if (type.startsWith("monster_")) type else "monster_0_$type"
    private fun tile(image: ImageBitmap, index: Int, columns: Int, cellWidth: Int, cellHeight: Int,
        height: Int = cellHeight, nativeScale: Float = 4f, logicalX: Float = 0f) =
        Frame(image, IntOffset(index % columns * cellWidth, index / columns * cellHeight), IntSize(cellWidth, height), nativeScale, logicalX)

    private fun frame(type: String, pose: String): Frame? {
        val a = assets ?: return null
        if (type == "heroBack") return heroIndex[pose]?.let { tile(a.hero, it, 12, 192, 192, logicalX = -8f) }
        monsterIndex[monsterKey(type)]?.let { index ->
            val phase = monsterPoseIndex[pose] ?: return null
            return tile(a.monsters, index * MotionAtlas.monsterPoses.size + phase, 24, 144, 144, MotionAtlas.monsterHeights[index], 3f, -8f)
        }
        return gear(type, badge = true)
    }

    @Synchronized private fun gear(type: String, badge: Boolean): Frame? {
        val a = assets ?: return null
        val index = gearIndex[base(type)] ?: return null
        val height = MotionAtlas.gearHeights[index]
        if (!type.startsWith("gear:")) return tile(a.gear.asImageBitmap(), index, 10, 128, 192, height)
        val parts = type.split(':')
        val tier = (parts.getOrNull(2)?.toIntOrNull() ?: 1).coerceIn(1, 7)
        val prefix = if (badge) parts.getOrNull(3)?.toIntOrNull() ?: -1 else -1
        val key = "${base(type)}:$tier:$prefix"
        return gearCache.getOrPut(key) {
            val pixels = IntArray(128 * height)
            a.gear.getPixels(pixels, 0, 128, index % 10 * 128, index / 10 * 192, 128, height)
            val palette = MotionAtlas.palette
            val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            val hues = floatArrayOf(0f, 135f, 205f, 275f, 320f, 25f, 45f)
            val hsv = FloatArray(3)
            if (tier > 1) for (i in pixels.indices) {
                val c = pixels[i]
                android.graphics.Color.colorToHSV(c, hsv)
                // Recolour steel planes while preserving the painted light, leather and gold hardware.
                if (c ushr 24 > 0 && hsv[1] < .28f && hsv[2] > .14f) {
                    hsv[0] = hues[tier - 1]
                    hsv[1] = .30f + tier * .025f
                    pixels[i] = android.graphics.Color.HSVToColor(c ushr 24, hsv)
                }
            }
            if (prefix >= 0) {
                val glyphs = listOf(listOf("020", "222", "020"), listOf("002", "020", "200"), listOf("222", "202", "222"), listOf("202", "020", "202"), listOf("222", "020", "020"))
                val color = palette[alphabet.indexOf(listOf('F', 't', 'p', 'B', 'w')[prefix % 5])]
                val x0 = 102; val y0 = height - 32
                for (y in 0..27) for (x in 0..23) pixels[(y0 + y) * 128 + x0 + x] = palette[1]
                glyphs[prefix % 5].forEachIndexed { y, row -> row.forEachIndexed { x, c -> if (c != '0') for (yy in 0..3) for (xx in 0..3) pixels[(y0 + 8 + y * 4 + yy) * 128 + x0 + 4 + x * 4 + xx] = color } }
            }
            Frame(Bitmap.createBitmap(pixels, 128, height, Bitmap.Config.ARGB_8888).asImageBitmap(), IntOffset.Zero, IntSize(128, height))
        }
    }

    private fun DrawScope.paint(frame: Frame, x: Float, y: Float, width: Float, height: Float, flash: Color?, opacity: Float) {
        drawImage(frame.image, srcOffset = frame.offset, srcSize = frame.size,
            dstOffset = IntOffset(x.roundToInt(), y.roundToInt()), dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.None, alpha = opacity.coerceIn(0f, 1f), colorFilter = flash?.let { ColorFilter.tint(it, BlendMode.SrcAtop) })
    }
    fun DrawScope.paintSprite(type: String, pose: String, x: Float, y: Float, unit: Float, flash: Color?, opacity: Float): Boolean {
        val f = frame(type, pose) ?: return false
        paint(f, x + f.logicalX * unit, y, f.size.width / f.nativeScale * unit, f.size.height / f.nativeScale * unit, flash, opacity)
        return true
    }
    fun DrawScope.paintWorn(item: Gear, x: Float, y: Float, width: Float, height: Float, flash: Color?, angle: Float = 0f): Boolean {
        val type = gearSprite(item)
        val index = gearIndex[base(type)] ?: return false
        val f = gear(type, badge = false) ?: return false
        val b = MotionAtlas.gearBounds[index]
        withTransform({ rotate(angle, Offset(x + width / 2, y + height / 2)) }) {
            paint(f.copy(offset = f.offset + IntOffset(b[0], b[1]), size = IntSize(b[2] - b[0], b[3] - b[1])), x, y, width, height, flash, 1f)
        }
        return true
    }

    fun shoulder(pose: String): Offset = MotionAtlas.heroShoulders[heroIndex[pose] ?: 0].let { Offset(it[0] * .25f, it[1] * .25f) }
    fun leftShoulder(pose: String): Offset = MotionAtlas.heroLeftShoulders[heroIndex[pose] ?: 0].let { Offset(it[0] * .25f, it[1] * .25f) }
    fun bodyScale(pose: String) = if (pose.startsWith("death_")) .55f else 1f
    data class WornPlacement(val area: List<Float>, val angle: Float)
    fun wornPlacement(kind: Kind, pose: String): WornPlacement? {
        val part = when (kind) { Kind.HEAD -> 0; Kind.BODY -> 1; Kind.PANTS -> 2; Kind.BOOTS -> 3; else -> return null }
        val cell = MotionAtlas.heroSourceCells[heroIndex[pose] ?: 0]
        val b = MotionAtlas.worn[cell][part]
        return WornPlacement(listOf((b[0] - 32) * .25f, b[1] * .25f, b[2] * .25f, b[3] * .25f), b[4].toFloat())
    }
    fun monsterHand(type: String, pose: String): Offset? {
        val monster = monsterIndex[monsterKey(type)] ?: return null
        val phase = monsterPoseIndex[pose] ?: return null
        return MotionAtlas.monsterHands[monster * MotionAtlas.monsterPoses.size + phase].let { Offset(it[0] / 3f, it[1] / 3f) }
    }
    fun weaponGrip(type: String): Offset? = gearIndex[base(type)]?.let { MotionAtlas.gearGrips[it] }?.let { Offset(it[0] * .25f, it[1] * .25f) }
    fun DrawScope.paintArena(clock: Long, floor: Int): Boolean {
        val a = assets ?: return false
        paint(Frame(a.arena, IntOffset.Zero, IntSize(a.arena.width, a.arena.height)), 0f, 0f, size.width, size.height, null, 1f)
        if (floor > 10) drawRect(Color(DungeonTheme.forFloor(floor).wall).copy(alpha = .16f))
        val pulse = (.022f + .012f * sin(clock * .004).toFloat()).coerceAtLeast(0f)
        for (x in listOf(.045f, .955f)) drawCircle(Color(0xFFFFB957).copy(alpha = pulse), size.width * .12f, Offset(size.width * x, size.height * .21f))
        return true
    }

    fun DrawScope.arm(a: Offset, b: Offset, unit: Float, opacity: Float, metal: Boolean = false): Boolean {
        if (!loaded) return false
        val elbow = (a + b) * .5f + Offset(-unit * 1.5f, unit * 3)
        fun segment(start: Offset, end: Offset, part: String) {
            val f = gear((if (metal) "steel_" else "cloth_") + part, false) ?: return
            val d = end - start
            withTransform({ rotate(atan2(d.y, d.x) * 180f / PI.toFloat() - 90f, start) }) {
                paint(f.copy(size = IntSize(32, f.size.height)), start.x - unit * 2.5f, start.y - unit * .4f, unit * 5, sqrt(d.x * d.x + d.y * d.y) + unit, null, opacity)
            }
        }
        segment(a, elbow, "upper"); segment(elbow, b, "lower")
        return true
    }
    fun DrawScope.grip(hand: Offset, unit: Float, angle: Float, opacity: Float, metal: Boolean = false) {
        val f = gear((if (metal) "steel_" else "cloth_") + "grip", false) ?: return
        withTransform({ rotate(angle, hand) }) {
            paint(f.copy(size = IntSize(32, 32)), hand.x - unit * 2.5f, hand.y - unit * 2.5f, unit * 5, unit * 5, null, opacity)
        }
    }
}
