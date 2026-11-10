package com.powerfulhimchan.mapexit

import android.content.res.Resources
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
    private data class Assets(val people: ImageBitmap, val icons: ImageBitmap)
    @Volatile private var assets: Assets? = null
    val loaded get() = assets != null
    @Synchronized fun load(resources: Resources) {
        if (assets != null) return
        fun decode(id: Int) = requireNotNull(BitmapFactory.decodeResource(resources,id,BitmapFactory.Options().apply { inScaled=false })).asImageBitmap()
        assets=Assets(decode(R.drawable.social_people_049),decode(R.drawable.growth_icons_049))
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
    fun DrawScope.frontBody(s: State,x: Float,baseline: Float,width: Float,stride: Float=0f): HeroPose {
        val unit=width/32; val y=baseline-unit*48
        contactShadow(x,baseline+3,width)
        val split=y+unit*32
        fun legs(paint: DrawScope.()->Unit) {
            for(side in 0..1) {
                val step=if(side==0)stride else -stride
                withTransform({translate(step*unit*1.2f,-abs(step)*unit*.55f)}) {
                    clipRect(x+side*unit*16,split,x+(side+1)*unit*16,baseline+unit) { paint() }
                }
            }
        }
        if(abs(stride)>.001f) {
            clipRect(x,y,x+width,split) { person("hero_front",x,baseline,width) }
            legs { person("hero_front",x,baseline,width) }
        } else person("hero_front",x,baseline,width)
        // Coordinates are in the fitted 32x48 front cell, independent of rear battle poses.
        for(item in Equipment.equipped(s).sortedBy { if(it.kind==Kind.BOOTS)1 else 0 }) {
            val b=when(item.kind) {
                Kind.HEAD->floatArrayOf(7f,1f,18f,15f)
                Kind.BODY->floatArrayOf(7.8f,15.7f,16.4f,18.0f)
                Kind.PANTS->floatArrayOf(9.2f,32.7f,13.6f,10.5f)
                Kind.BOOTS->floatArrayOf(7f,40.6f,18f,6.4f)
                Kind.NECKLACE->floatArrayOf(13f,18f,6f,7f)
                else->continue
            }
            // Front ornaments and steel-tier colours come from the original front equipment.
            fun DrawScope.paintGear() { with(MotionArtwork) { paintWorn(item,x+b[0]*unit,y+b[1]*unit,b[2]*unit,b[3]*unit,null,back=false) } }
            if(item.kind in listOf(Kind.PANTS,Kind.BOOTS) && abs(stride)>.001f)legs { paintGear() } else paintGear()
        }
        return HeroPose(x,y,unit)
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
