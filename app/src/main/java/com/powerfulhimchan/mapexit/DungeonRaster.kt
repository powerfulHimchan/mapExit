package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.powerfulhimchan.mapexit.core.Dungeon
import com.powerfulhimchan.mapexit.core.DungeonViewPose
import kotlin.math.*

/** World-space textures and a continuous camera keep courses aligned across cell boundaries. */
internal class DungeonRaster(private val width: Int, private val height: Int) {
    private class Texture(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels =
            IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }

        fun at(u: Float, v: Float): Int {
            val x = ((u - floor(u)) * width).toInt().coerceIn(0, width - 1)
            val y = ((v - floor(v)) * height).toInt().coerceIn(0, height - 1)
            return pixels[y * width + x]
        }
    }

    private val wall = Texture(PixelMaterials.wall(Color(0xff435c68)))
    private val ceiling = Texture(PixelMaterials.wall(Color(0xff24343d), 8))
    private val floorTexture = Texture(PixelMaterials.floor)
    private val torch = Texture(FineSprites.all.getValue("torch").bitmap.asAndroidBitmap())
    private val pixels = IntArray(width * height)
    private val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val image = bitmap.asImageBitmap()
    private val rayX = FloatArray(width)
    private val rayY = FloatArray(width)
    private var lastMap: Dungeon? = null
    private var lastCamera: DungeonViewPose? = null
    private var lastGeometry = 0

    private fun shade(color: Int, light: Int): Int =
        (0xff shl 24) or
            ((((color ushr 16) and 255) * light / 256) shl 16) or
            ((((color ushr 8) and 255) * light / 256) shl 8) or
            ((color and 255) * light / 256)

    fun render(map: Dungeon, camera: DungeonViewPose): ImageBitmap {
        val geometry = map.cells.fold(1) { hash, cell -> hash * 31 + if (cell.wall) 1 else 0 }
        if (lastMap === map && lastCamera == camera && lastGeometry == geometry) return image
        val dx = cos(camera.angle)
        val dy = sin(camera.angle)
        val plane = .70f
        val focal = width / (2f * plane)
        val horizon = height * .48f
        for (x in 0 until width) {
            val side = (2f * (x + .5f) / width - 1f) * plane
            rayX[x] = dx - dy * side
            rayY[x] = dy + dx * side
        }
        // Floor casting uses the same rays as the walls. The pattern never restarts per step.
        for (y in 0 until height) {
            val below = y + .5f > horizon
            val distance =
                (.5f * focal / abs(y + .5f - horizon).coerceAtLeast(.25f)).coerceAtMost(100f)
            val light =
                ((if (below) 225f else 170f) / (1f + distance * .14f)).toInt().coerceAtLeast(48)
            val material = if (below) floorTexture else ceiling
            for (x in 0 until width) {
                val worldX = camera.x + rayX[x] * distance
                val worldY = camera.y + rayY[x] * distance
                pixels[y * width + x] = shade(material.at(worldX / 2, worldY / 2), light)
            }
        }
        fun solid(x: Int, y: Int): Boolean =
            x !in 0 until map.width || y !in 0 until map.height || map.cells[y * map.width + x].wall
        for (x in 0 until width) {
            val rx = rayX[x]
            val ry = rayY[x]
            var mx = floor(camera.x).toInt()
            var my = floor(camera.y).toInt()
            val deltaX = if (abs(rx) < .00001f) 100000f else abs(1f / rx)
            val deltaY = if (abs(ry) < .00001f) 100000f else abs(1f / ry)
            val stepX = if (rx < 0) -1 else 1
            val stepY = if (ry < 0) -1 else 1
            var sideX = (if (rx < 0) camera.x - mx else mx + 1f - camera.x) * deltaX
            var sideY = (if (ry < 0) camera.y - my else my + 1f - camera.y) * deltaY
            var side = 0
            var distance = 1f
            for (step in 0..96) {
                if (sideX < sideY) {
                    distance = sideX
                    sideX += deltaX
                    mx += stepX
                    side = 0
                } else {
                    distance = sideY
                    sideY += deltaY
                    my += stepY
                    side = 1
                }
                if (solid(mx, my)) break
            }
            distance = distance.coerceAtLeast(.03f)
            val span = focal / distance
            val top = horizon - span * .5f
            val bottom = horizon + span * .5f
            val start = ceil(top).toInt().coerceAtLeast(0)
            val end = floor(bottom).toInt().coerceAtMost(height - 1)
            val hit = if (side == 0) camera.y + ry * distance else camera.x + rx * distance
            val u = hit - floor(hit)
            val light =
                ((if (side == 0) 232f else 208f) / (1f + distance * .10f)).toInt().coerceAtLeast(65)
            val hasTorch = ((mx * 31 + my * 17) and 7) == 0
            for (y in start..end) {
                val v = (y + .5f - top) / span
                var color = wall.at(u, v)
                if (hasTorch && u >= .38f && u <= .62f && v >= .24f && v <= .60f) {
                    val flame = torch.at((u - .38f) / .24f, (v - .24f) / .36f)
                    if ((flame ushr 24) > 0) color = flame
                }
                // One contact edge at the actual floor, rather than a projecting lip per tile.
                val edge = if (bottom - y < 2f || y - top < 1f) light * 2 / 3 else light
                pixels[y * width + x] = shade(color, edge)
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        lastMap = map
        lastCamera = camera
        lastGeometry = geometry
        return image
    }
}
