package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.*

/** Sole landmarks come from the visible native boot pixels, not transparent sprite padding. */
internal object GroundContact053 {
    data class Foot(val left: Float, val right: Float, val bottom: Float)
    data class Footprint(val feet: List<Foot>) {
        val bottom = feet.maxOf { it.bottom }
    }
    data class Placement(val top: Float, val shiftY: Float, val feet: List<Foot>,
        val floor: Float, val elevation: Float, val scale: Float)

    fun footprint(bitmap: Bitmap): Footprint {
        val pixels=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        var bottom=bitmap.height-1
        while(bottom>=0 && (0 until bitmap.width).none { pixels[bottom*bitmap.width+it] ushr 24>96 }) bottom--
        require(bottom>=0) { "Native frame has no visible soles" }
        val feet=mutableListOf<Foot>()
        var start=-1
        // The lowest opaque contour is the real support sole. Equipment masks may
        // end above a bent boot's toe, so they must not define the floor plane.
        for(x in 0..bitmap.width) {
            val ink=x<bitmap.width && (max(0,bottom-1)..bottom).any { pixels[it*bitmap.width+x] ushr 24>96 }
            if(ink && start<0) start=x
            if(!ink && start>=0) {
                feet+=Foot(start.toFloat(),x.toFloat(),bottom+1f);start=-1
            }
        }
        return Footprint(feet)
    }

    /** Translate after rotating, so even a leaning actor retains a planted support foot. */
    fun place(footprint: Footprint, left: Float, scale: Float, floor: Float,
        pivotX: Float = left, angle: Float = 0f, elevation: Float = 0f): Placement {
        val lift = elevation.coerceAtLeast(0f)
        val target = floor - lift
        val top = target - footprint.bottom * scale
        val radians = angle * PI.toFloat() / 180
        fun project(x: Float, y: Float): Offset {
            val dx = left + x * scale - pivotX
            val dy = top + y * scale - target
            return Offset(pivotX + dx * cos(radians) - dy * sin(radians),
                target + dx * sin(radians) + dy * cos(radians))
        }
        val projected = footprint.feet.map { f ->
            val a = project(f.left,f.bottom); val b = project(f.right,f.bottom)
            Foot(min(a.x,b.x),max(a.x,b.x),max(a.y,b.y))
        }
        val correction = target - projected.maxOf { it.bottom }
        return Placement(top,correction,projected.map { it.copy(bottom = it.bottom + correction) },floor,lift,scale)
    }

    /** One soft cast shadow remains on the floor; only planted feet get a contact patch. */
    fun DrawScope.shadow(placement: Placement) {
        val feet = placement.feet
        val left = feet.minOf { it.left }; val right = feet.maxOf { it.right }
        val lift = placement.elevation / placement.scale.coerceAtLeast(.01f)
        val spread = (1f-lift/300f).coerceIn(.55f,1f)
        val fade = (1f-lift/210f).coerceIn(.22f,1f)
        val width = ((right-left)*.90f+placement.scale*8f).coerceAtLeast(placement.scale*42f)*spread
        val height = (placement.scale*5.5f).coerceIn(2f,9f)*spread
        val center = (left+right)/2
        drawOval(Color.Black.copy(alpha=.16f*fade),Offset(center-width/2,placement.floor-height*.45f),Size(width,height))
        drawOval(Color.Black.copy(alpha=.13f*fade),Offset(center-width*.38f,placement.floor-height*.22f),Size(width*.76f,height*.60f))
        if (placement.elevation <= .5f) {
            val lowest = feet.maxOf { it.bottom }
            for (foot in feet.filter { lowest-it.bottom <= placement.scale*2f }) {
                val h = (placement.scale*2.4f).coerceIn(1.5f,4f)
                drawOval(Color.Black.copy(alpha=.34f),Offset(foot.left-placement.scale,placement.floor-h*.60f),
                    Size(foot.right-foot.left+placement.scale*2,h))
            }
        }
    }
}
