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

/** Rear clothing is deliberately separate from the unchanged front-facing inventory icons. */
internal object PreparationArtwork {
    private data class Region(val image: ImageBitmap, val offset: IntOffset, val size: IntSize)
    private data class Assets(val rear: Bitmap, val icons: ImageBitmap, val camp: ImageBitmap)
    @Volatile private var assets: Assets? = null
    val loaded get() = assets != null
    private val tinted = object : LinkedHashMap<String, Region>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Region>?) = size > 64
    }
    private val aliases = mapOf("body" to "armor_leatherarmor", "pants" to "armor_leatherpants", "boots" to "armor_leatherboots")

    @Synchronized fun load(resources: Resources) {
        if (assets != null) return
        fun decode(id: Int) = requireNotNull(BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inScaled = false }))
        assets = Assets(decode(R.drawable.rear_equipment_048), decode(R.drawable.preparation_icons_048).asImageBitmap(), decode(R.drawable.preparation_camp_048).asImageBitmap())
    }

    internal fun rearKey(item: Gear): String = gearSprite(item).split(':').let { if (it[0] == "gear") it[1] else it[0] }.let { aliases[it] ?: it }

    @Synchronized private fun rear(item: Gear): Region? {
        val a = assets ?: return null
        val key = rearKey(item)
        val index = PreparationAtlas.rearKeys.indexOf(key)
        if (index < 0) return null
        val tier = item.tier.coerceIn(1, 7)
        return tinted.getOrPut("$key:$tier") {
            val b = PreparationAtlas.rearBounds[index]
            val width = b[2] - b[0]; val height = b[3] - b[1]
            val pixels = IntArray(width * height)
            a.rear.getPixels(pixels, 0, width, index % 4 * 128 + b[0], index / 4 * 160 + b[1], width, height)
            // Same steel-tier tint as the front icons; keep leather, gold and painted shading.
            val hues = floatArrayOf(0f,135f,205f,275f,320f,25f,45f)
            val hsv = FloatArray(3)
            if (tier > 1) for (i in pixels.indices) {
                val c = pixels[i]
                android.graphics.Color.colorToHSV(c, hsv)
                if (c ushr 24 > 0 && hsv[1] < .28f && hsv[2] > .14f) {
                    hsv[0] = hues[tier-1]; hsv[1] = .30f + tier * .025f
                    pixels[i] = android.graphics.Color.HSVToColor(c ushr 24, hsv)
                }
            }
            Region(Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888).asImageBitmap(),IntOffset.Zero,IntSize(width,height))
        }
    }

    private fun DrawScope.paint(r: Region, x: Float, y: Float, width: Float, height: Float, tint: Color? = null) {
        drawImage(r.image,srcOffset=r.offset,srcSize=r.size,dstOffset=IntOffset(x.roundToInt(),y.roundToInt()),
            dstSize=IntSize(width.roundToInt().coerceAtLeast(1),height.roundToInt().coerceAtLeast(1)),filterQuality=FilterQuality.None,
            colorFilter=tint?.let { ColorFilter.tint(it,BlendMode.SrcAtop) })
    }
    fun DrawScope.paintWorn(item: Gear, x: Float, y: Float, width: Float, height: Float, tint: Color?, angle: Float): Boolean {
        val r = rear(item) ?: return false
        withTransform({ rotate(angle,Offset(x+width/2,y+height/2)) }) { paint(r,x,y,width,height,tint) }
        return true
    }
    fun DrawScope.paintShield(item: Gear, hand: Offset, unit: Float, tint: Color? = null): Boolean {
        val r = rear(item) ?: return false
        val scale = min(unit*22/r.size.width,unit*27/r.size.height)
        val width = r.size.width*scale; val height = r.size.height*scale
        paint(r,hand.x-width/2,hand.y-height/2,width,height,tint)
        return true
    }
    fun DrawScope.paintIcon(action: String): Boolean {
        val a = assets ?: return false
        val i = PreparationAtlas.menuKeys.indexOf(action)
        if (i < 0) return false
        val b = PreparationAtlas.menuBounds[i]
        val r = Region(a.icons,IntOffset(i%3*128+b[0],i/3*128+b[1]),IntSize(b[2]-b[0],b[3]-b[1]))
        val scale = min(size.width/r.size.width,size.height/r.size.height)
        val w = r.size.width*scale; val h = r.size.height*scale
        paint(r,(size.width-w)/2,(size.height-h)/2,w,h)
        return true
    }
    fun DrawScope.paintCamp(clock: Long) {
        val a = assets ?: return
        // Centre-crop preserves the masonry proportions at every phone/banner aspect ratio.
        val scale = max(size.width/a.camp.width,size.height/a.camp.height)
        val width = a.camp.width*scale; val height = a.camp.height*scale
        paint(Region(a.camp,IntOffset.Zero,IntSize(a.camp.width,a.camp.height)),(size.width-width)/2,(size.height-height)/2,width,height)
        val alpha = .018f + .009f*sin(clock*.004).toFloat()
        for (x in listOf(.27f,.72f)) drawCircle(Color(0xFFFFBD68).copy(alpha=alpha),size.height*.18f,Offset(size.width*x,size.height*.27f))
    }
}
