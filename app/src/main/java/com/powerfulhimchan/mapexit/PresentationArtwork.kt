package com.powerfulhimchan.mapexit

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.*

/** Front and rear views have separate artwork; all dialogue roles share the combat pixel filter. */
internal object PresentationArtwork {
    private data class Assets(val people: ImageBitmap, val icons: ImageBitmap,val walk: Bitmap)
    @Volatile private var assets: Assets? = null
    val loaded get() = assets != null
    @Synchronized fun load(resources: Resources) {
        if (assets != null) return
        fun decode(id: Int) = requireNotNull(BitmapFactory.decodeResource(resources,id,BitmapFactory.Options().apply { inScaled=false })).asImageBitmap()
        assets=Assets(decode(R.drawable.social_people_049),decode(R.drawable.growth_icons_049),decode(R.drawable.camp_walk_057).asAndroidBitmap())
    }
    fun DrawScope.person(type: String,x: Float,baseline: Float,width: Float): Boolean {
        val a=assets ?: return false
        val i=SocialAtlas.people.indexOf(type)
        if(i<0)return false
        drawImage(a.people,srcOffset=IntOffset(i%3*128,i/3*192),srcSize=IntSize(128,192),
            dstOffset=IntOffset(x.roundToInt(),(baseline-width*1.5f).roundToInt()),
            dstSize=IntSize(width.roundToInt().coerceAtLeast(1),(width*1.5f).roundToInt().coerceAtLeast(1)),filterQuality=FilterQuality.None)
        return true
    }
    fun campFrame(clock: Long,direction: Int): Int = if(direction==0)-1 else
        ((Math.floorMod(clock-1600,560L)/140).toInt()+if(direction<0)4 else 0)
    private val walkRight=listOf(Offset(85f,73f),Offset(89f,70f),Offset(95f,77f),Offset(87f,69f),
        Offset(91f,66f),Offset(91f,64f),Offset(91f,71f),Offset(91f,65f))
    private val walkLeft=listOf(Offset(102f,67f),Offset(105f,65f),Offset(108f,71f),Offset(104f,65f),
        Offset(105f,71f),Offset(107f,69f),Offset(107f,78f),Offset(108f,70f))
    fun campShoulder(clock: Long,direction: Int,right: Boolean): Offset {
        val i=campFrame(clock,direction)
        if(i<0)return if(right)Offset(8.5f,19f) else Offset(23.5f,19f)
        val p=if(right)walkRight[i] else walkLeft[i]
        return Offset((p.x-32f)/4,p.y/4)
    }
    private fun dressedCamp(s: State,clock: Long,direction: Int): NativeEquipment057.Dressed? {
        val a=assets ?: return null;val i=campFrame(clock,direction)
        return NativeEquipment057.dressed("camp:$i",Equipment.equipped(s),
            if(i<0)NativeEquipment057.front else NativeEquipment057.side(i)) {
            if(i<0)Bitmap.createBitmap(a.people.asAndroidBitmap(),0,0,128,192)
            else Bitmap.createBitmap(a.walk,i%4*192,i/4*192,192,192)
        }
    }
    private fun DrawScope.campLayer(s: State,x: Float,baseline: Float,width: Float,clock: Long,direction: Int,feetOnly: Boolean) {
        val body=dressedCamp(s,clock,direction) ?: return
        val i=campFrame(clock,direction);val layout=if(i<0)NativeEquipment057.front else NativeEquipment057.side(i)
        val scale=width/128;val left=x-if(i<0)0f else scale*32;val top=baseline-scale*192
        val helmet=Equipment.equipped(s).firstOrNull { it.kind==Kind.HEAD }
        if(!feetOnly && helmet!=null && helmet.variantId!="headband") {
            val h=layout.head;val hw=h.area[2]*scale;val hh=h.area[3]*scale*1.08f
            val neck=Offset(left+h.neck.x*scale,top+h.neck.y*scale)
            withTransform({if(direction>0)scale(-1f,1f,neck)}) {
                with(MotionArtwork) { paintWorn(helmet,neck.x-hw*.5f,neck.y-hh*.88f,hw,hh,null,back=false) }
            }
        }
        val image=if(feetOnly)body.boots else body.body
        drawImage(image,srcSize=IntSize(image.width,image.height),dstOffset=IntOffset(left.roundToInt(),top.roundToInt()),
            dstSize=IntSize((image.width*scale).roundToInt(),(image.height*scale).roundToInt()),filterQuality=FilterQuality.None)
        if(!feetOnly && helmet?.variantId=="headband") {
            val h=layout.head.area
            with(MotionArtwork) { paintWorn(helmet,left+h[0]*scale,top+(h[1]+h[3]*.45f)*scale,h[2]*scale,h[3]*.25f*scale,null,back=false) }
        }
    }
    fun DrawScope.frontBody(s: State,x: Float,baseline: Float,width: Float,stride: Float=0f,clock: Long=0,direction: Int=0): HeroPose {
        contactShadow(x,baseline+3,width)
        campLayer(s,x,baseline,width,clock,direction,false)
        return HeroPose(x,baseline-width*1.5f,width/32)
    }
    fun DrawScope.frontBoots(s: State,x: Float,baseline: Float,width: Float,clock: Long,direction: Int) {
        campLayer(s,x,baseline,width,clock,direction,true)
    }
    fun DrawScope.growthIcon(key: String): Boolean {
        val a=assets ?: return false
        val i=SocialAtlas.icons.indexOf(key)
        if(i<0)return false
        val b=SocialAtlas.iconBounds[i]
        val width=b[2]-b[0];val height=b[3]-b[1]
        val scale=min(size.width/width,size.height/height)
        val w=(width*scale).roundToInt().coerceAtLeast(1);val h=(height*scale).roundToInt().coerceAtLeast(1)
        drawImage(a.icons,srcOffset=IntOffset(i%4*128+b[0],i/4*128+b[1]),srcSize=IntSize(width,height),
            dstOffset=IntOffset(((size.width-w)/2).roundToInt(),((size.height-h)/2).roundToInt()),dstSize=IntSize(w,h),filterQuality=FilterQuality.None)
        return true
    }
}
