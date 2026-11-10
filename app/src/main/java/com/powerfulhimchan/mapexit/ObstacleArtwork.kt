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
    private val dressed=object : LinkedHashMap<String,ImageBitmap>(36,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,ImageBitmap>?)=size>36
    }
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
        val armor=items.firstOrNull { it.kind==Kind.BODY }
        val key="$pose:${armor?.variantId}:${armor?.tier}"
        val art=dressed.getOrPut(key) {
            val raw=Bitmap.createBitmap(image.asAndroidBitmap(),index%4*192,index/4*192,192,192)
            val result=armor?.let { PreparationArtwork.dressedObstacleBody(it,raw,pose) }
            if(result!=null) { raw.recycle();result.asImageBitmap() } else raw.asImageBitmap()
        }
        val fit=ObstacleFit056.forPose(pose)
        val helmet=items.firstOrNull { it.kind==Kind.HEAD }
        fun outline(points: List<Float>)=Path().apply {
            moveTo(x+points[0]*scale,y+points[1]*scale)
            for(i in 2 until points.size step 2)lineTo(x+points[i]*scale,y+points[i+1]*scale)
            close()
        }
        fun paintBody() {
            drawImage(art,srcSize=IntSize(192,192),dstOffset=IntOffset(x.roundToInt(),y.roundToInt()),
                dstSize=IntSize(extent.roundToInt().coerceAtLeast(1),extent.roundToInt().coerceAtLeast(1)),
                filterQuality=FilterQuality.None,colorFilter=tint?.let { ColorFilter.tint(it,BlendMode.SrcAtop) })
        }
        contactShadow(center-width/2,baseline+3,width)
        if(helmet!=null && helmet.variantId!="headband") {
            val h=fit.head
            val head=outline(listOf(h[0],h[1],h[0]+h[2],h[1],h[0]+h[2],h[1]+h[3],h[0],h[1]+h[3]))
            clipPath(head,ClipOp.Difference) { paintBody() }
        } else paintBody()
        items.firstOrNull { it.kind==Kind.PANTS }?.let { item ->
            for(patch in ObstacleFit056.patches(Kind.PANTS,pose))with(PreparationArtwork) { paintNativePatch(item,patch,x,y,scale,tint) }
            // The coat hem is nearer than the trousers and follows the native silhouette.
            clipPath(outline(fit.coat)) { paintBody() }
        }
        helmet?.let { with(PreparationArtwork) { paintObstacleHelmet(it,pose,x,y,scale,tint) } }
        // Foreground boots are last; each foot follows its own bent or raised shin.
        items.firstOrNull { it.kind==Kind.BOOTS }?.let { item ->
            for(patch in ObstacleFit056.patches(Kind.BOOTS,pose))with(PreparationArtwork) { paintNativePatch(item,patch,x,y,scale,tint) }
        }
    }
}
