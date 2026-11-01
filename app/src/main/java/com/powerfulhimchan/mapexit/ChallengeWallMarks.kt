package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/** Transparent, pixel-edged chalk artwork sampled directly on a real wall face. */
internal object ChallengeWallMarks {
    private val digits = listOf(
        "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
        "111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111",
    )

    fun bitmap(symbol: Int, number: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(48, 56, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = false; strokeWidth = 2f; style = Paint.Style.STROKE }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = canvas.drawLine(x1,y1,x2,y2,paint)
        fun path(vararg points: Pair<Float,Float>, close: Boolean = false) {
            val p = Path(); points.forEachIndexed { i, (x,y) -> if (i == 0) p.moveTo(x,y) else p.lineTo(x,y) }
            if (close) p.close()
            canvas.drawPath(p,paint)
        }
        fun artwork() {
            when (symbol) {
                0 -> { // sun
                    canvas.drawCircle(24f,19f,7f,paint)
                    for (i in 0..7) {
                        val a = i * kotlin.math.PI / 4
                        line(24f+cos(a).toFloat()*11,19f+sin(a).toFloat()*11,
                            24f+cos(a).toFloat()*15,19f+sin(a).toFloat()*15)
                    }
                }
                1 -> { // crescent
                    val p = Path().apply {
                        moveTo(29f,5f); cubicTo(7f,5f,7f,33f,29f,33f)
                        cubicTo(17f,25f,17f,13f,29f,5f); close()
                    }
                    canvas.drawPath(p,paint)
                }
                2 -> { // five-point star
                    val p = Path()
                    for (i in 0..9) {
                        val a = -kotlin.math.PI/2 + i*kotlin.math.PI/5
                        val r = if (i%2 == 0) 15f else 6f
                        val x = 24f+cos(a).toFloat()*r; val y = 19f+sin(a).toFloat()*r
                        if (i == 0) p.moveTo(x,y) else p.lineTo(x,y)
                    }
                    p.close(); canvas.drawPath(p,paint)
                }
                3 -> { // mountain
                    path(7f to 32f,22f to 6f,38f to 32f,close=true)
                    path(16f to 16f,22f to 20f,27f to 15f)
                }
                4 -> { // water
                    for (y in listOf(10f,19f,28f))
                        path(7f to y,13f to y-3,20f to y+3,27f to y-3,34f to y+3,41f to y)
                }
                else -> { // flower
                    for (i in 0..5) {
                        val a = i*kotlin.math.PI/3
                        canvas.drawCircle(24f+cos(a).toFloat()*9,19f+sin(a).toFloat()*9,5f,paint)
                    }
                    canvas.drawCircle(24f,19f,4f,paint)
                }
            }
            paint.style = Paint.Style.FILL
            val text = number.toString()
            val left = (48 - (text.length*8-2))/2
            text.forEachIndexed { i,ch ->
                digits[ch.digitToInt()].forEachIndexed { p,bit ->
                    if (bit == '1') canvas.drawRect((left+i*8+p%3*2).toFloat(),(41+p/3*2).toFloat(),
                        (left+i*8+p%3*2+2).toFloat(),(43+p/3*2).toFloat(),paint)
                }
            }
            paint.style = Paint.Style.STROKE
        }
        // A dark cut under the warm chalk gives contrast on every dungeon material.
        canvas.save(); canvas.translate(1f,1f); paint.color = 0xff12232b.toInt(); artwork(); canvas.restore()
        paint.color = 0xfff1d28e.toInt(); artwork()
        return bitmap
    }
}
