package com.powerfulhimchan.mapexit

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntRect
import com.powerfulhimchan.mapexit.core.*
import kotlin.math.roundToInt

/** Independent art layers; the rules, poses and equipment identities remain live. */
internal object BattleArtwork {
    private data class Frame(val image: ImageBitmap, val offset: IntOffset, val size: IntSize)
    private data class Assets(val chamber: ImageBitmap, val actors: List<Frame>, val gear: List<Frame>, val worn: List<Frame>)
    @Volatile private var assets: Assets? = null
    private val families = listOf("slime", "knight", "bat", "spider", "golem", "wisp", "boss")
    private val kinds = listOf(Kind.SWORD, Kind.SPEAR, Kind.HAMMER, Kind.CLAW, Kind.WHIP, Kind.SHIELD, Kind.HEAD, Kind.BODY, Kind.PANTS, Kind.BOOTS)
    private val authoredBounds = mutableMapOf<String, IntRect>()

    @Synchronized fun load(resources: Resources) {
        if (assets != null) return
        fun decode(id: Int) = requireNotNull(BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inScaled = false }))
        val actors = decode(R.drawable.battle_actors)
        val gear = decode(R.drawable.battle_equipment)
        assets = Assets(decode(R.drawable.battle_chamber).asImageBitmap(), grid(actors, 8, 5), grid(gear, 10, 4), grid(gear, 10, 4, crop = true))
    }

    private fun grid(bitmap: Bitmap, columns: Int, rows: Int, crop: Boolean = false): List<Frame> {
        val image = bitmap.asImageBitmap()
        return (0 until rows).flatMap { row -> (0 until columns).map { column ->
            val left = column * bitmap.width / columns
            val top = row * bitmap.height / rows
            val right = (column + 1) * bitmap.width / columns
            val bottom = (row + 1) * bitmap.height / rows
            if (!crop) Frame(image, IntOffset(left, top), IntSize(right - left, bottom - top))
            else {
                var x0 = right; var y0 = bottom; var x1 = left; var y1 = top
                for (y in top until bottom) for (x in left until right)
                    if (bitmap.getPixel(x, y).ushr(24) >= 128) {
                        x0 = minOf(x0, x); y0 = minOf(y0, y); x1 = maxOf(x1, x); y1 = maxOf(y1, y)
                    }
                require(x0 <= x1 && y0 <= y1) { "Empty equipment cell $column,$row" }
                Frame(image, IntOffset(x0, y0), IntSize(x1 - x0 + 1, y1 - y0 + 1))
            }
        } }
    }

    private fun equipmentCell(type: String): Int? {
        val base = if (type.startsWith("gear:")) type.split(':')[1] else type
        val weapon = WeaponCatalog.all.firstOrNull { "weapon_${it.id}" == base }
        val armor = ArmorCatalog.all.firstOrNull { (if (it.id == it.kind.name.lowercase()) it.id else "armor_${it.id}") == base }
        val kind = weapon?.kind ?: armor?.kind ?: kinds.firstOrNull { it.name.lowercase() == base } ?: return null
        val column = kinds.indexOf(kind)
        val row = when {
            weapon != null -> WeaponCatalog.of(kind).indexOf(weapon)
            armor != null -> ArmorCatalog.of(kind).indexOf(armor)
            else -> 0
        }.coerceIn(0, 3)
        return row * 10 + column
    }

    private fun frame(type: String): Frame? {
        val loaded = assets ?: return null
        if (type == "heroBack") return loaded.actors[0]
        if (type.startsWith("monster_")) {
            val parts = type.split('_')
            val row = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..4 } ?: return null
            val column = families.indexOf(parts.getOrNull(2))
            if (column >= 0) return loaded.actors[row * 8 + column + 1]
        }
        val legacyColumn = families.indexOf(type)
        if (legacyColumn >= 0) return loaded.actors[legacyColumn + 1]
        return equipmentCell(type)?.let { loaded.worn[it] }
    }

    fun has(type: String) = frame(type) != null

    fun DrawScope.paintSprite(type: String, x: Float, y: Float, width: Float, height: Float, flash: Color?, opacity: Float): Boolean {
        val frame = frame(type) ?: return false
        val base = if (type.startsWith("gear:")) type.split(':')[1] else type
        val authored = FineSprites.all[base]?.takeIf { equipmentCell(type) != null }
        if (authored != null) {
            // Keep the original grip/attachment coordinates while replacing icon art.
            val bounds = authoredBounds.getOrPut(base) {
                val rows = authored.rows
                IntRect(rows.filter { row -> row.any { it != '0' } }.minOf { row -> row.indexOfFirst { it != '0' } },
                    rows.indexOfFirst { row -> row.any { it != '0' } },
                    rows.maxOf { row -> row.indexOfLast { it != '0' } } + 1,
                    rows.indexOfLast { row -> row.any { it != '0' } } + 1)
            }
            paint(frame, x + bounds.left * width / authored.width, y + bounds.top * height / authored.height,
                bounds.width * width / authored.width, bounds.height * height / authored.height, flash, opacity)
        } else paint(frame, x, y, width, height, flash, opacity)
        return true
    }

    fun DrawScope.paintWorn(gear: Gear, x: Float, y: Float, width: Float, height: Float, flash: Color?): Boolean {
        val frame = equipmentCell(gearSprite(gear))?.let { assets?.worn?.get(it) } ?: return false
        paint(frame, x, y, width, height, flash, 1f)
        return true
    }

    private fun DrawScope.paint(frame: Frame, x: Float, y: Float, width: Float, height: Float, flash: Color?, opacity: Float) {
        drawImage(frame.image, srcOffset = frame.offset, srcSize = frame.size,
            dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
            dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.None, alpha = opacity.coerceIn(0f, 1f),
            colorFilter = flash?.let { ColorFilter.tint(it, BlendMode.SrcAtop) })
    }

    fun DrawScope.paintArena(floor: Int, clock: Long): Boolean {
        val loaded = assets ?: return false
        val theme = DungeonTheme.forFloor(floor)
        val matrix = when (theme) {
            DungeonTheme.TIDAL -> floatArrayOf(.65f,0f,0f,0f,0f, 0f,.94f,0f,0f,3f, 0f,0f,1.22f,0f,8f, 0f,0f,0f,1f,0f)
            DungeonTheme.CRYSTAL -> floatArrayOf(1.02f,0f,0f,0f,4f, 0f,.76f,0f,0f,0f, 0f,0f,1.27f,0f,7f, 0f,0f,0f,1f,0f)
            DungeonTheme.EMBER -> floatArrayOf(1.23f,0f,0f,0f,8f, 0f,.77f,0f,0f,0f, 0f,0f,.64f,0f,0f, 0f,0f,0f,1f,0f)
            DungeonTheme.ABYSS -> floatArrayOf(.71f,0f,0f,0f,0f, 0f,.73f,0f,0f,0f, 0f,0f,1.03f,0f,5f, 0f,0f,0f,1f,0f)
            else -> null
        }
        drawImage(loaded.chamber, srcSize = IntSize(loaded.chamber.width, loaded.chamber.height),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.None,
            colorFilter = matrix?.let { ColorFilter.colorMatrix(ColorMatrix(it)) })
        // Small live pools of torchlight over the painted chamber.
        val flicker = .035f + kotlin.math.sin(clock * .011).toFloat() * .012f
        for (x in listOf(.15f, .83f)) drawCircle(Color(theme.light).copy(alpha = flicker), size.width * .13f, Offset(size.width * x, size.height * .18f))
        return true
    }
}
