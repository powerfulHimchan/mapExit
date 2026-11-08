package com.powerfulhimchan.mapexit

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
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
        val extent=width*1.5f;val x=center-extent/2;val y=baseline-extent
        contactShadow(center-width/2,baseline+3,width)
        drawImage(image,srcOffset=IntOffset(index%4*192,index/4*192),srcSize=IntSize(192,192),
            dstOffset=IntOffset(x.roundToInt(),y.roundToInt()),dstSize=IntSize(extent.roundToInt().coerceAtLeast(1),extent.roundToInt().coerceAtLeast(1)),
            filterQuality=FilterQuality.None,colorFilter=tint?.let { ColorFilter.tint(it,BlendMode.SrcAtop) })
        // Pose-aware bounds, in the shared 192px cell. The base figure remains unarmed.
        val head=when(pose) {
            "crouch"->floatArrayOf(98f,87f,53f,44f)
            "land","rise"->floatArrayOf(87f,54f,57f,48f)
            "push_right"->floatArrayOf(84f,25f,57f,48f)
            "push_left"->floatArrayOf(51f,25f,57f,48f)
            "jump"->floatArrayOf(83f,38f,57f,48f)
            else->floatArrayOf(65f,12f,62f,52f)
        }
        val body=when(pose) {
            "crouch"->floatArrayOf(51f,119f,80f,35f)
            "land","rise"->floatArrayOf(53f,91f,78f,43f)
            "jump"->floatArrayOf(57f,82f,79f,46f)
            "push_right","push_left"->floatArrayOf(57f,77f,78f,54f)
            else->floatArrayOf(62f,67f,68f,72f)
        }
        for(item in Equipment.equipped(s)) {
            val b=when(item.kind) {
                Kind.HEAD->head
                Kind.BODY->body
                Kind.PANTS->if(pose=="crouch") floatArrayOf(48f,147f,77f,24f) else floatArrayOf(60f,130f,76f,35f)
                Kind.BOOTS->if(pose=="jump") floatArrayOf(55f,151f,76f,32f) else floatArrayOf(49f,164f,93f,24f)
                else->continue
            }
            with(MotionArtwork) { paintWorn(item,x+b[0]/192*extent,y+b[1]/192*extent,b[2]/192*extent,b[3]/192*extent,tint) }
        }
    }
}
