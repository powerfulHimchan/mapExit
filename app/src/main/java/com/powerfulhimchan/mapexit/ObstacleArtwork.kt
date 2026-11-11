package com.powerfulhimchan.mapexit

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.powerfulhimchan.mapexit.core.*
import kotlin.math.roundToInt

/** Parts remain separate so the existing timers/collision geometry drive the painted artwork. */
internal object ObstacleArtwork {
    private var parts: ImageBitmap? = null
    private var heroes: ImageBitmap? = null
    @Synchronized fun load(resources: Resources) {
        if (parts != null) return
        fun decode(id: Int) = requireNotNull(BitmapFactory.decodeResource(resources,id,BitmapFactory.Options().apply { inScaled=false })).asImageBitmap()
        parts=decode(R.drawable.obstacle_parts_046);heroes=decode(R.drawable.obstacle_hero_046)
    }
    fun DrawScope.piece(name: String,x: Float,y: Float,width: Float,height: Float,alpha: Float=1f) {
        val image=parts ?: return
        val i=ObstacleAtlas.pieces.indexOf(name);require(i>=0)
        val b=ObstacleAtlas.bounds[i]
        drawImage(image,srcOffset=IntOffset(i%4*256+b[0],i/4*256+b[1]),srcSize=IntSize(b[2]-b[0],b[3]-b[1]),
            dstOffset=IntOffset(x.roundToInt(),y.roundToInt()),dstSize=IntSize(width.roundToInt().coerceAtLeast(1),height.roundToInt().coerceAtLeast(1)),
            filterQuality=FilterQuality.None,alpha=alpha.coerceIn(0f,1f))
    }
    fun DrawScope.hero(s: State,center: Float,baseline: Float,width: Float,pose: String,tint: Color?=null) {
        val image=heroes ?: return
        val index=ObstacleAtlas.poses.indexOf(pose).coerceAtLeast(0)
        val extent=width*1.5f;val x=center-extent/2;val y=baseline-extent;val scale=extent/192
        val items=Equipment.equipped(s)
        val layout=NativeEquipment057.obstacle(pose)
        val dressed=NativeEquipment057.dressed("obstacle:$pose",items,layout) {
            Bitmap.createBitmap(image.asAndroidBitmap(),index%4*192,index/4*192,192,192)
        }
        val helmet=items.firstOrNull { it.kind==Kind.HEAD }
        contactShadow(center-width/2,baseline+3,width)
        if(helmet!=null && helmet.variantId!="headband")with(PreparationArtwork) {
            paintConnectedHelmet(helmet,layout.head,x,y,scale,tint)
        }
        fun paint(part: ImageBitmap) {
            drawImage(part,srcSize=IntSize(192,192),dstOffset=IntOffset(x.roundToInt(),y.roundToInt()),
                dstSize=IntSize(extent.roundToInt().coerceAtLeast(1),extent.roundToInt().coerceAtLeast(1)),
                filterQuality=FilterQuality.None,colorFilter=tint?.let { ColorFilter.tint(it,BlendMode.SrcAtop) })
        }
        paint(dressed.body)
        if(helmet?.variantId=="headband")with(PreparationArtwork) { paintObstacleHelmet(helmet,pose,x,y,scale,tint) }
        // Same native foot pixels, redrawn in the foreground; never a larger boot icon.
        paint(dressed.boots)
    }
}
