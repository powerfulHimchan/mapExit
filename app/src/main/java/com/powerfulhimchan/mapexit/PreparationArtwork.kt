package com.powerfulhimchan.mapexit

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipRect
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
    /** Coordinates are measured on each cropped inward-facing shield image (lower grip). */
    internal fun shieldGrip(item: Gear): Offset = when(rearKey(item)) {
        "armor_buckler" -> Offset(.51f,.54f)
        "armor_tower" -> Offset(.51f,.73f)
        "armor_spiked" -> Offset(.50f,.68f)
        else -> Offset(.53f,.69f)
    }
    fun DrawScope.paintShield(item: Gear, hand: Offset, unit: Float, tint: Color? = null, foreshorten: Float = 1f, braceOnly: Boolean = false): Boolean {
        val r = rear(item) ?: return false
        // A hand-held shield reaches shoulder to knee, leaving the back and face readable.
        val scale = min(unit*16/r.size.width,unit*20/r.size.height)
        val width = r.size.width*scale*foreshorten; val height = r.size.height*scale
        val grip=shieldGrip(item)
        val x=hand.x-width*grip.x;val y=hand.y-height*grip.y
        if(braceOnly) {
            if(rearKey(item)=="armor_buckler") return true
            val strap=if(rearKey(item)=="armor_spiked") .40f else .35f
            clipRect(x+width*.40f,y+height*(strap-.035f),x+width*.62f,y+height*(strap+.035f)) {
                paint(r,x,y,width,height,tint)
            }
        } else paint(r,x,y,width,height,tint)
        return true
    }

    /** Fit the same rear material to head/torso landmarks and to each leg independently. */
    fun DrawScope.paintFitted(item: Gear,x: Float,y: Float,unit: Float,pose: String,tint: Color?): Boolean {
        val patches=EquipmentFit.patches(item.kind,pose) ?: return false
        val r=rear(item) ?: return false
        for(patch in patches) {
            val f=patch.source
            val source=floatArrayOf(f[0]*r.size.width,f[1]*r.size.height,f[2]*r.size.width,f[1]*r.size.height,
                f[2]*r.size.width,f[3]*r.size.height,f[0]*r.size.width,f[3]*r.size.height)
            val target=FloatArray(8)
            for(i in 0..3) EquipmentFit.point(patch.corners,i*2,x,y,unit,pose).let { target[i*2]=it.x;target[i*2+1]=it.y }
            // Follow the skull angle while fitting the rotated silhouette inside its measured bounds.
            if(item.kind==Kind.HEAD) {
                val w=target[2]-target[0];val h=target[5]-target[1]
                val angle=MotionAtlas.worn[EquipmentFit.cell(pose)][0][4].toFloat()
                val pivot=Offset(target[0]+w/2,target[1]+h/2)
                withTransform({ rotate(angle,pivot) }) {
                    if(item.variantId=="headband")
                        paint(r,target[0]+w*.05f,target[1]+h*.45f,w*.9f,h*.32f,tint)
                    else {
                        val rad=angle*PI.toFloat()/180
                        val c=abs(cos(rad));val s=abs(sin(rad))
                        val scale=min(w/(r.size.width*c+r.size.height*s),h/(r.size.width*s+r.size.height*c))
                        val width=r.size.width*scale;val height=r.size.height*scale
                        paint(r,pivot.x-width/2,pivot.y-height/2,width,height,tint)
                    }
                }
                continue
            }
            val matrix=android.graphics.Matrix()
            check(matrix.setPolyToPoly(source,0,target,0,4))
            val clip=android.graphics.Path().apply {
                moveTo(target[0],target[1]);for(i in 1..3)lineTo(target[i*2],target[i*2+1]);close()
            }
            drawIntoCanvas { canvas ->
                val native=canvas.nativeCanvas
                native.save();native.clipPath(clip);native.concat(matrix)
                val paint=android.graphics.Paint().apply {
                    isFilterBitmap=false;isAntiAlias=false
                    colorFilter=tint?.let { android.graphics.PorterDuffColorFilter(it.toArgb(),android.graphics.PorterDuff.Mode.SRC_ATOP) }
                }
                native.drawBitmap(r.image.asAndroidBitmap(),0f,0f,paint)
                native.restore()
            }
        }
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
