package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max

/** Small, deterministic material maps are painted once, then reused for every frame. */
internal object PixelMaterials {
    private val walls = mutableMapOf<Pair<Int, Int>, Bitmap>()
    val meshPaint =
        Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
        }
    private val paint =
        Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
        }

    private fun noise(x: Int, y: Int): Int = (x * 73856093 xor y * 19349663).ushr(1)

    private fun tone(base: Int, delta: Int): Int {
        fun channel(shift: Int) = (((base shr shift) and 255) + delta).coerceIn(0, 255)
        return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun wall(color: Color, brickColumns: Int = 4): Bitmap =
        walls.getOrPut(color.toArgb() to brickColumns) {
            val base = color.toArgb()
            val bmp = Bitmap.createBitmap(brickColumns * 32, 384, Bitmap.Config.ARGB_8888)
            val c = NativeCanvas(bmp)
            fun r(x: Int, y: Int, w: Int, h: Int, ink: Int) {
                paint.color = ink
                c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
            }
            c.drawColor(tone(base, -24))
            for (row in 0..15) for (col in -1..brickColumns) {
                val seed = noise(col, row)
                val x = col * 32 + (row % 2) * 16
                val y = row * 24
                val shade = seed % 15 - 7
                r(x + 1, y + 1, 30, 22, tone(base, shade))
                r(x + 2, y + 1, 28, 1, tone(base, shade + 22))
                r(x + 1, y + 2, 1, 19, tone(base, shade + 13))
                r(x + 2, y + 21, 28, 2, tone(base, shade - 15))
                r(x + 30, y + 2, 1, 20, tone(base, shade - 18))
                // Chipped corners, irregular grain and fine fissures have stable tile-local seeds.
                r(x + 1, y + 1, 2, 1, tone(base, -20))
                r(x + 29, y + 21, 2, 2, tone(base, -23))
                for (n in 0..10) {
                    val px = x + 3 + (seed + n * 17) % 25
                    val py = y + 3 + (seed / 7 + n * 13) % 16
                    r(
                        px,
                        py,
                        if (n % 4 == 0) 2 else 1,
                        1,
                        tone(base, shade + if (n % 3 == 0) 15 else -10),
                    )
                }
                if (seed % 4 == 0) {
                    val px = x + 8 + seed % 12
                    r(px, y + 6, 4, 1, tone(base, -28))
                    r(px + 3, y + 7, 1, 4, tone(base, -28))
                    r(px + 4, y + 10, 3, 1, tone(base, -25))
                    r(px + 6, y + 11, 1, 3, tone(base, -25))
                    r(px, y + 5, 3, 1, tone(base, 14))
                }
                if (row >= 11 || seed % 11 == 0) {
                    for (n in 0..5) {
                        val px = x + 3 + (seed + n * 5) % 25
                        r(
                            px,
                            y + 19 - n % 3,
                            2,
                            1,
                            if (n % 2 == 0) 0xff526044.toInt() else 0xff394b3b.toInt(),
                        )
                    }
                }
            }
            bmp
        }

    val floor: Bitmap by lazy {
        val bmp = Bitmap.createBitmap(576, 512, Bitmap.Config.ARGB_8888)
        val c = NativeCanvas(bmp)
        fun r(x: Int, y: Int, w: Int, h: Int, color: Int) {
            paint.color = color
            c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
        }
        c.drawColor(0xff111d25.toInt())
        for (row in 0..15) for (col in -1..12) {
            val seed = noise(col, row)
            val x = col * 48 + (row % 2) * 24
            val y = row * 32
            val base = tone(0xff293a42.toInt(), seed % 12 - 6)
            r(x + 2, y + 2, 45, 28, base)
            r(x + 3, y + 2, 43, 1, tone(base, 17))
            r(x + 2, y + 3, 1, 25, tone(base, 9))
            r(x + 3, y + 28, 42, 2, tone(base, -13))
            r(x + 45, y + 3, 2, 26, tone(base, -11))
            r(x + 2, y + 2, 2, 2, 0xff111d25.toInt())
            r(x + 43, y + 28, 4, 2, 0xff111d25.toInt())
            for (n in 0..11) r(
                x + 5 + (seed + n * 19) % 36,
                y + 5 + (seed / 3 + n * 11) % 20,
                1 + n % 2,
                1,
                tone(base, if (n % 3 == 0) 14 else -9),
            )
            if (seed % 3 == 0) {
                r(x + 13, y + 4, 1, 6, tone(base, -18))
                r(x + 14, y + 9, 5, 1, tone(base, -18))
                r(x + 18, y + 10, 1, 3, tone(base, -18))
            }
            if (seed % 5 == 0) {
                r(x + 7, y + 29, 6, 1, 0xff586044.toInt())
                r(x + 10, y + 28, 2, 1, 0xff6e704b.toInt())
            }
        }
        bmp
    }
}

/** Warp a cached material into a wall slab; the brick courses shrink naturally with depth. */
internal fun DrawScope.stonePlane(
    a: Offset,
    b: Offset,
    c: Offset,
    d: Offset,
    color: Color,
    brickColumns: Int = 4,
) {
    val columns = 4
    val rows = 12
    val vertices = FloatArray((columns + 1) * (rows + 1) * 2)
    var n = 0
    for (row in 0..rows) for (col in 0..columns) {
        val u = col.toFloat() / columns
        val v = row.toFloat() / rows
        val top = a + (b - a) * u
        val bottom = d + (c - d) * u
        val p = top + (bottom - top) * v
        vertices[n++] = p.x
        vertices[n++] = p.y
    }
    drawIntoCanvas {
        it.nativeCanvas.drawBitmapMesh(
            PixelMaterials.wall(color, brickColumns),
            columns,
            rows,
            vertices,
            0,
            null,
            0,
            PixelMaterials.meshPaint,
        )
    }
}

internal fun DrawScope.masonry(x: Float, y: Float, w: Float, h: Float, base: Color) {
    if (w <= 0f || h <= 0f) return
    stonePlane(
        Offset(x, y),
        Offset(x + w, y),
        Offset(x + w, y + h),
        Offset(x, y + h),
        base,
        (w / 32).toInt().coerceIn(1, 16),
    )
}

/** Full floor plane, projected from the vanishing line to the bottom of the viewport. */
internal fun DrawScope.stoneFloor(horizon: Float = .50f) {
    val w = size.width
    val h = size.height
    pixelRect(Color(0xff172630), 0f, h * horizon, w, h * (1 - horizon))
    val cols = 8
    val rows = 16
    val points = FloatArray((cols + 1) * (rows + 1) * 2)
    var n = 0
    for (row in 0..rows) for (col in 0..cols) {
        val depth = max(.006f, row.toFloat() / rows)
        points[n++] = w * .50f + (col.toFloat() / cols - .5f) * w * (1f + 2f * depth)
        points[n++] = h * horizon + h * (1 - horizon) * depth * depth
    }
    drawIntoCanvas {
        it.nativeCanvas.drawBitmapMesh(
            PixelMaterials.floor,
            cols,
            rows,
            points,
            0,
            null,
            0,
            PixelMaterials.meshPaint,
        )
    }
}

internal fun DrawScope.contactShadow(x: Float, y: Float, w: Float) {
    pixelRect(Color(0xff101c24), x - w * .08f, y, w * 1.12f, 4f)
    pixelRect(Color(0xff14212a), x + w * .07f, y + 4, w * .87f, 2f)
}

internal fun DrawScope.torch(x: Float, y: Float, clock: Long) {
    // Stepped pools of light keep the same fine pixel grid as the stone and flame.
    for (layer in 5 downTo 1) {
        val radius = layer * 9f
        pixelRect(
            Color(0xffffb75b).copy(alpha = .015f + (6 - layer) * .005f),
            x - radius,
            y - radius,
            radius * 2,
            radius * 2.4f,
        )
    }
    sprite("torch", x - 16f, y - 19f, 32f, amber)
    val frame = ((clock / 150) % 3).toInt()
    pixelRect(Color(0xffffd879), x - 1f, y - 11f - frame, 2f, 4f + frame)
    pixelRect(Color(0xffffe3a0), x + 3f, y - 22f - frame, 1f, 2f)
}
