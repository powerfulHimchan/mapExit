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

/** Authored pixel layers keep a logical 32px footprint while using a native 64px grid. */
internal object MotionArtwork {
    private data class Frame(val image: ImageBitmap, val offset: IntOffset, val size: IntSize)
    private data class Assets(val hero: ImageBitmap, val monsters: ImageBitmap, val gear: Bitmap)
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
        assets = Assets(decode(R.drawable.combat_hero_motion).asImageBitmap(), decode(R.drawable.combat_monster_motion).asImageBitmap(), decode(R.drawable.combat_equipment_motion))
    }

    fun has(type: String, pose: String = "idle_0"): Boolean = when {
        type == "heroBack" -> pose in heroIndex
        monsterKey(type) in monsterIndex -> pose in monsterPoseIndex
        else -> base(type) in gearIndex
    }
    private fun base(type: String) = if (type.startsWith("gear:")) type.split(':')[1] else type
    private fun monsterKey(type: String) = if (type.startsWith("monster_")) type else "monster_0_$type"
    private fun tile(image: ImageBitmap, index: Int, columns: Int, height: Int = 96) = Frame(image, IntOffset(index % columns * 64, index / columns * 96), IntSize(64, height))

    private fun frame(type: String, pose: String): Frame? {
        val a = assets ?: return null
        if (type == "heroBack") return heroIndex[pose]?.let { tile(a.hero, it, 12) }
        monsterIndex[monsterKey(type)]?.let { index ->
            val phase = monsterPoseIndex[pose] ?: return null
            return tile(a.monsters, index * MotionAtlas.monsterPoses.size + phase, 24, MotionAtlas.monsterHeights[index])
        }
        return gear(type, badge = true)
    }

    @Synchronized private fun gear(type: String, badge: Boolean): Frame? {
        val a = assets ?: return null
        val index = gearIndex[base(type)] ?: return null
        val height = MotionAtlas.gearHeights[index]
        if (!type.startsWith("gear:")) return tile(a.gear.asImageBitmap(), index, 10, height)
        val parts = type.split(':')
        val tier = (parts.getOrNull(2)?.toIntOrNull() ?: 1).coerceIn(1, 7)
        val prefix = if (badge) parts.getOrNull(3)?.toIntOrNull() ?: -1 else -1
        val key = "${base(type)}:$tier:$prefix"
        return gearCache.getOrPut(key) {
            val pixels = IntArray(64 * height)
            a.gear.getPixels(pixels, 0, 64, index % 10 * 64, index / 10 * 96, 64, height)
            val palette = MotionAtlas.palette
            val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            val ramp = listOf("345", "cde", "BCE", "vwx", "myz", "yz6", "DEF")[tier - 1].map { palette[alphabet.indexOf(it)] }
            for (i in pixels.indices) for (j in 0..2) if (pixels[i] == palette[3 + j]) { pixels[i] = ramp[j]; break }
            if (prefix >= 0) {
                val glyphs = listOf(listOf("020", "222", "020"), listOf("002", "020", "200"), listOf("222", "202", "222"), listOf("202", "020", "202"), listOf("222", "020", "020"))
                val color = palette[alphabet.indexOf(listOf('F', 't', 'p', 'B', 'w')[prefix % 5])]
                val x0 = 50; val y0 = height - 16
                for (y in 0..13) for (x in 0..11) pixels[(y0 + y) * 64 + x0 + x] = palette[1]
                glyphs[prefix % 5].forEachIndexed { y, row -> row.forEachIndexed { x, c -> if (c != '0') for (yy in 0..1) for (xx in 0..1) pixels[(y0 + 4 + y * 2 + yy) * 64 + x0 + 2 + x * 2 + xx] = color } }
            }
            Frame(Bitmap.createBitmap(pixels, 64, height, Bitmap.Config.ARGB_8888).asImageBitmap(), IntOffset.Zero, IntSize(64, height))
        }
    }

    private fun DrawScope.paint(frame: Frame, x: Float, y: Float, width: Float, height: Float, flash: Color?, opacity: Float) {
        drawImage(frame.image, srcOffset = frame.offset, srcSize = frame.size,
            dstOffset = IntOffset(x.roundToInt(), y.roundToInt()), dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.None, alpha = opacity.coerceIn(0f, 1f), colorFilter = flash?.let { ColorFilter.tint(it, BlendMode.SrcAtop) })
    }
    fun DrawScope.paintSprite(type: String, pose: String, x: Float, y: Float, unit: Float, flash: Color?, opacity: Float): Boolean {
        val f = frame(type, pose) ?: return false
        paint(f, x, y, 32 * unit, f.size.height * .5f * unit, flash, opacity)
        return true
    }
    fun DrawScope.paintWorn(item: Gear, x: Float, y: Float, width: Float, height: Float, flash: Color?): Boolean {
        val type = gearSprite(item)
        val index = gearIndex[base(type)] ?: return false
        val f = gear(type, badge = false) ?: return false
        val b = MotionAtlas.gearBounds[index]
        paint(f.copy(offset = f.offset + IntOffset(b[0], b[1]), size = IntSize(b[2] - b[0], b[3] - b[1])), x, y, width, height, flash, 1f)
        return true
    }

    fun shoulder(pose: String): Offset {
        val i = heroIndex[pose] ?: return Offset(23f, 23f)
        return MotionAtlas.heroShoulders[i].let { Offset(it[0] * .5f, it[1] * .5f) }
    }
    fun bodyScale(pose: String) = if (pose.startsWith("death_")) 1f - .12f * (pose.substringAfterLast('_').toInt() + 1) else 1f
    fun wornOffset(kind: Kind, top: Float, pose: String): Offset {
        val scale = bodyScale(pose)
        if (scale < 1f) return Offset(0f, (48f - top) * (1f - scale))
        val bone = shoulder(pose) - Offset(23f, 23f)
        return Offset(if (kind == Kind.BODY) bone.x else 0f,
            if (pose.startsWith("dodge_") || kind == Kind.BODY && pose == "idle_1") bone.y else 0f)
    }

    fun DrawScope.arm(a: Offset, b: Offset, unit: Float, opacity: Float, metal: Boolean = false): Boolean {
        if (!loaded) return false
        val elbow = (a + b) * .5f + Offset(-unit * 1.5f, unit * 3)
        fun segment(start: Offset, end: Offset, part: String) {
            val f = gear((if (metal) "steel_" else "cloth_") + part, false) ?: return
            val d = end - start
            withTransform({ rotate(atan2(d.y, d.x) * 180f / PI.toFloat() - 90f, start) }) {
                paint(f.copy(size = IntSize(16, f.size.height)), start.x - unit * 2.5f, start.y - unit * .4f, unit * 5, sqrt(d.x * d.x + d.y * d.y) + unit, null, opacity)
            }
        }
        segment(a, elbow, "upper"); segment(elbow, b, "lower")
        return true
    }
    fun DrawScope.grip(hand: Offset, unit: Float, angle: Float, opacity: Float, metal: Boolean = false) {
        val f = gear((if (metal) "steel_" else "cloth_") + "grip", false) ?: return
        withTransform({ rotate(angle, hand) }) {
            paint(f.copy(size = IntSize(16, 16)), hand.x - unit * 2.5f, hand.y - unit * 2.5f, unit * 5, unit * 5, null, opacity)
        }
    }
}
