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
    /** Dress the existing body silhouette at native resolution, rather than layering a
     * differently shaped inventory coat over it. Hair, limbs, belt and body size are retained. */
    fun dressedBody(item: Gear, body: Bitmap, pose: String): Bitmap? = dressNative(item,body,
        EquipmentFit.coat(pose),EquipmentFit.torso(pose),EquipmentFit.belt(pose),
        MotionAtlas.worn[EquipmentFit.cell(pose)][0],EquipmentFit.bob(pose))

    fun dressedObstacleBody(item: Gear,body: Bitmap,pose: String): Bitmap? {
        val f=ObstacleFit056.forPose(pose)
        return dressNative(item,body,f.coat,f.torso,f.belt,f.head.take(4).map { it.toInt() }.toIntArray(),0f)
    }
    private fun dressNative(item: Gear,body: Bitmap,coatPoints: List<Float>,torsoPoints: List<Float>,
        beltPoints: List<Float>,head: IntArray,bob: Float): Bitmap? {
        val material=rear(item) ?: return null
        val width=body.width;val height=body.height
        val original=IntArray(width*height);body.getPixels(original,0,width,0,0,width,height)
        fun path(points: List<Float>)=android.graphics.Path().apply {
            moveTo(points[0],points[1]+bob)
            for(i in 2 until points.size step 2)lineTo(points[i],points[i+1]+bob)
            close()
        }
        fun mask(points: List<Float>): IntArray {
            val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bitmap).drawPath(path(points),android.graphics.Paint().apply { color=android.graphics.Color.WHITE;isAntiAlias=false })
            return IntArray(width*height).also { bitmap.getPixels(it,0,width,0,0,width,height);bitmap.recycle() }
        }
        val coat=mask(coatPoints);val belt=mask(beltPoints)
        fun greenCloth(color: Int): Boolean {
            val r=android.graphics.Color.red(color);val g=android.graphics.Color.green(color);val b=android.graphics.Color.blue(color)
            return color ushr 24>0 && g>25 && g>r*1.06f && g>b*1.04f
        }
        // Follow the painted cloth as well as the measured contour. Idle breathing and
        // the asymmetric far shoulder contain small folds beyond a polygon's corners.
        for(i in original.indices) if(greenCloth(original[i])) {
            val x=i%width;val y=i/width
            for(yy in (y-2).coerceAtLeast(0)..(y+2).coerceAtMost(height-1))
                for(xx in (x-2).coerceAtLeast(0)..(x+2).coerceAtMost(width-1))coat[yy*width+xx]=-1
        }
        val backing=original.copyOf()
        val steel=item.variantId in listOf("platearmor","chainmail")
        if(steel) for(i in backing.indices) {
            val color=original[i];val r=android.graphics.Color.red(color);val g=android.graphics.Color.green(color);val b=android.graphics.Color.blue(color)
            val luminance=(r*3+g*6+b)/10f
            val value=(if(luminance<30)luminance*1.15f else luminance*.95f+30).toInt().coerceIn(0,225)
            backing[i]=android.graphics.Color.rgb((value*.90f).toInt(),value,(value*1.07f).toInt().coerceAtMost(255))
        }
        // Opaque backing prevents the old green coat showing through an icon's armholes.
        for(i in backing.indices) backing[i]=backing[i] or 0xff000000.toInt()
        val fitted=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888).apply {
            setPixels(backing,0,width,0,0,width,height)
        }
        val target=torsoPoints.toFloatArray()
        for(i in 1 until target.size step 2)target[i]+=bob
        val source=floatArrayOf(0f,0f,material.size.width.toFloat(),0f,material.size.width.toFloat(),material.size.height.toFloat(),0f,material.size.height.toFloat())
        val matrix=android.graphics.Matrix();check(matrix.setPolyToPoly(source,0,target,0,4))
        android.graphics.Canvas(fitted).drawBitmap(material.image.asAndroidBitmap(),matrix,android.graphics.Paint().apply { isFilterBitmap=false;isAntiAlias=false })
        val pixels=IntArray(width*height);fitted.getPixels(pixels,0,width,0,0,width,height);fitted.recycle()
        for(i in pixels.indices) {
            val px=i%width;val py=i/width-bob
            val inHead=px>=head[0] && px<head[0]+head[2] && py>=head[1] && py<head[1]+head[3]-5
            val hairOrSkin=inHead && android.graphics.Color.green(original[i])<=android.graphics.Color.red(original[i])*1.05f
            // The original alpha is the final clothing outline in every frame.
            pixels[i]=if(coat[i]!=0 && (belt[i]==0 || greenCloth(original[i])) && !hairOrSkin && original[i] ushr 24>0)
                (original[i] and 0xff000000.toInt()) or (pixels[i] and 0x00ffffff) else original[i]
        }
        return Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
    }
    /** Project a rear material onto native obstacle landmarks, independently per limb. */
    fun DrawScope.paintNativePatch(item: Gear,patch: EquipmentFit.Patch,x: Float,y: Float,scale: Float,tint: Color?): Boolean {
        val r=rear(item) ?: return false
        val f=patch.source
        val source=floatArrayOf(f[0]*r.size.width,f[1]*r.size.height,f[2]*r.size.width,f[1]*r.size.height,
            f[2]*r.size.width,f[3]*r.size.height,f[0]*r.size.width,f[3]*r.size.height)
        val target=patch.corners.mapIndexed { i,v -> (if(i%2==0)x else y)+v*scale }.toFloatArray()
        val matrix=android.graphics.Matrix();if(!matrix.setPolyToPoly(source,0,target,0,4))return false
        val outline=android.graphics.Path().apply {
            moveTo(target[0],target[1]);for(i in 1..3)lineTo(target[i*2],target[i*2+1]);close()
        }
        drawIntoCanvas { canvas ->
            val c=canvas.nativeCanvas;c.save();c.clipPath(outline);c.concat(matrix)
            c.drawBitmap(r.image.asAndroidBitmap(),0f,0f,android.graphics.Paint().apply {
                isFilterBitmap=false;isAntiAlias=false
                colorFilter=tint?.let { android.graphics.PorterDuffColorFilter(it.toArgb(),android.graphics.PorterDuff.Mode.SRC_ATOP) }
            });c.restore()
        }
        return true
    }
    fun DrawScope.paintObstacleHelmet(item: Gear,pose: String,x: Float,y: Float,scale: Float,tint: Color?) {
        val h=ObstacleFit056.forPose(pose).head
        val top=if(item.variantId=="headband")h[1]+h[3]*.46f else h[1]
        val height=if(item.variantId=="headband")h[3]*.22f else h[3]
        val corners=listOf(h[0],top,h[0]+h[2],top,h[0]+h[2],top+height,h[0],top+height)
        withTransform({rotate(h[4],Offset(x+(h[0]+h[2]/2)*scale,y+(h[1]+h[3]/2)*scale))}) {
            paintNativePatch(item,EquipmentFit.Patch(listOf(0f,0f,1f,1f),corners),x,y,scale,tint)
        }
    }

    /** Coordinates are measured on each cropped inward-facing shield image (lower grip). */
    internal fun shieldGrip(item: Gear): Offset = when(rearKey(item)) {
        "armor_buckler" -> Offset(.51f,.54f)
        "armor_tower" -> Offset(.51f,.73f)
        "armor_spiked" -> Offset(.50f,.68f)
        else -> Offset(.53f,.69f)
    }
    internal fun shieldBrace(item: Gear): Offset = when(rearKey(item)) {
        "armor_buckler" -> Offset(.50f,.21f)
        "armor_tower" -> Offset(.51f,.36f)
        "armor_spiked" -> Offset(.50f,.36f)
        else -> Offset(.51f,.35f)
    }
    data class ShieldMount(val hand: Offset,val grip: Offset,val brace: Offset,val width: Float,val height: Float,val angle: Float) {
        fun project(point: Offset): Offset {
            val p=Offset((point.x-grip.x)*width,(point.y-grip.y)*height)
            val rad=angle*PI.toFloat()/180
            return hand+Offset(p.x*cos(rad)-p.y*sin(rad),p.x*sin(rad)+p.y*cos(rad))
        }
    }
    /** The hand and elbow jointly constrain position, angle and scale. A shield can no
     * longer pivot independently while its upper strap floats away from the forearm. */
    fun mountShield(item: Gear,hand: Offset,elbow: Offset,foreshorten: Float=.90f): ShieldMount? {
        val r=rear(item) ?: return null
        val grip=shieldGrip(item);val brace=shieldBrace(item)
        val local=Offset((grip.x-brace.x)*r.size.width*foreshorten,(grip.y-brace.y)*r.size.height)
        val forearm=hand-elbow
        val scale=forearm.getDistance()/local.getDistance().coerceAtLeast(.001f)
        val angle=(atan2(forearm.y,forearm.x)-atan2(local.y,local.x))*180f/PI.toFloat()
        return ShieldMount(hand,grip,brace,r.size.width*scale*foreshorten,r.size.height*scale,angle)
    }
    fun DrawScope.paintShield(item: Gear,mount: ShieldMount,tint: Color?=null,braceOnly: Boolean=false): Boolean {
        val r=rear(item) ?: return false
        val width=mount.width;val height=mount.height
        val x=mount.hand.x-width*mount.grip.x;val y=mount.hand.y-height*mount.grip.y
        withTransform({ rotate(mount.angle,mount.hand) }) {
            if(braceOnly) {
                if(rearKey(item)!="armor_buckler") clipRect(x+width*.40f,y+height*(mount.brace.y-.035f),x+width*.62f,y+height*(mount.brace.y+.035f)) {
                    paint(r,x,y,width,height,tint)
                }
            } else paint(r,x,y,width,height,tint)
        }
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
