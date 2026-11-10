package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.powerfulhimchan.mapexit.core.Gear
import com.powerfulhimchan.mapexit.core.Kind
import kotlin.math.*

/** Dress the painted anatomy itself: no inventory silhouette can enlarge a body or foot. */
internal object NativeEquipment057 {
    data class Head(val area: List<Float>,val neck: Offset,val angle: Float=0f)
    data class Layout(val feet: List<List<Float>>,val trousers: List<List<Float>>,val head: Head)
    data class Dressed(val body: ImageBitmap,val boots: ImageBitmap)
    private val cache=object : LinkedHashMap<String,Dressed>(80,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,Dressed>?)=size>80
    }
    fun green(c: Int): Boolean {
        val r=c shr 16 and 255;val g=c shr 8 and 255;val b=c and 255
        return c ushr 24>0 && g>25 && g>r*1.06f && g>b*1.04f
    }
    private fun mask(w: Int,h: Int,polygons: List<List<Float>>): IntArray {
        val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val canvas=android.graphics.Canvas(b)
        val paint=android.graphics.Paint().apply { color=-1;isAntiAlias=false }
        for(p in polygons) {
            val path=android.graphics.Path().apply {
                moveTo(p[0],p[1]);for(i in 2 until p.size step 2)lineTo(p[i],p[i+1]);close()
            };canvas.drawPath(path,paint)
        }
        return IntArray(w*h).also { b.getPixels(it,0,w,0,0,w,h);b.recycle() }
    }
    private fun clothMask(original: IntArray,w: Int,h: Int): BooleanArray {
        val m=BooleanArray(original.size)
        for(i in original.indices)if(green(original[i])) {
            val x=i%w;val y=i/w
            for(yy in max(0,y-1)..min(h-1,y+1))for(xx in max(0,x-1)..min(w-1,x+1))m[yy*w+xx]=true
        }
        return m
    }
    private fun material(c: Int,item: Gear,x: Int,y: Int): Int {
        val r=c shr 16 and 255;val g=c shr 8 and 255;val b=c and 255
        val lum=(r*3+g*6+b)/10f
        // Original buckles, piping and stitching retain their painted shape and gold.
        if(g>105 && r>g*1.12f && g>b*1.3f)return c
        val metal=item.variantId in listOf("platearmor","chainmail","ironboots","platepants","heavyhelm")
        var value=(if(metal)lum*1.22f+20 else lum*1.04f+10).coerceIn(0f,235f)
        if(item.variantId=="chainmail" && value>65)value+=if((x+y)%4<2)7 else -7
        var rr: Float;var gg: Float;var bb: Float
        if(metal) { rr=value*.88f;gg=value*.97f;bb=value*1.04f }
        else { rr=value*1.10f;gg=value*.76f;bb=value*.47f }
        if(item.tier>1) {
            val hues=floatArrayOf(0f,135f,205f,275f,320f,25f,45f)
            val hsv=floatArrayOf(hues[item.tier.coerceIn(1,7)-1],.27f,value/255)
            val tinted=android.graphics.Color.HSVToColor(hsv)
            rr=rr*.55f+(tinted shr 16 and 255)*.45f
            gg=gg*.55f+(tinted shr 8 and 255)*.45f;bb=bb*.55f+(tinted and 255)*.45f
        }
        return (c and 0xff000000.toInt()) or (rr.toInt().coerceIn(0,255) shl 16) or
            (gg.toInt().coerceIn(0,255) shl 8) or bb.toInt().coerceIn(0,255)
    }
    /** Keep the collar and native neck; only remove hair/face above its shared neck plane. */
    fun clearHead(pixels: IntArray,original: IntArray,w: Int,h: Int,head: Head) {
        val cloth=clothMask(original,w,h);val a=head.area
        val slope=tan(head.angle.coerceIn(-65f,65f)*PI.toFloat()/180)
        val eligible=BooleanArray(original.size)
        for(y in 0 until h)for(x in 0 until w) {
            val i=y*w+x
            eligible[i]=original[i] ushr 24>0 && x>=a[0]-32 && x<=a[0]+a[2]+32 && y>=a[1]-20 &&
                y<head.neck.y+(x-head.neck.x)*slope+2 && !cloth[i] && hypot(x-head.neck.x,y-head.neck.y)>3f
        }
        // Follow connected hair/face pixels beyond the old rectangular head box.
        // Separate raised hands remain intact because they are not part of the head.
        val seen=BooleanArray(original.size);val queue=java.util.ArrayDeque<Int>()
        for(y in 0 until h)for(x in 0 until w) {
            val start=y*w+x;if(!eligible[start] || seen[start])continue
            val component=mutableListOf<Int>();queue.add(start);seen[start]=true
            var core=false;var top=h;var sx=0f
            while(queue.isNotEmpty()) {
                val i=queue.removeFirst();val xx=i%w;val yy=i/w
                component.add(i);top=min(top,yy);sx+=xx
                if(xx>=a[0]+a[2]*.20f && xx<=a[0]+a[2]*.80f && yy<a[1]+a[3]*.80f)core=true
                for(dy in -1..1)for(dx in -1..1) {
                    val nx=xx+dx;val ny=yy+dy
                    if(nx !in 0 until w || ny !in 0 until h)continue
                    val n=ny*w+nx
                    if(eligible[n] && !seen[n]) { seen[n]=true;queue.add(n) }
                }
            }
            val cx=sx/component.size
            val hairSpeck=component.size<32 && top<a[1]+a[3]*.62f && cx>=a[0]-20 && cx<=a[0]+a[2]+20
            if(core || hairSpeck)for(i in component)pixels[i]=0
        }
    }
    @Synchronized fun dressed(key: String,items: List<Gear>,layout: Layout,raw: ()->Bitmap): Dressed {
        val signature=items.filter { it.kind in listOf(Kind.BODY,Kind.PANTS,Kind.BOOTS,Kind.HEAD) }
            .sortedBy { it.kind.name }.joinToString { "${it.kind}:${it.variantId}:${it.tier}" }
        return cache.getOrPut("$key:$signature") {
            val b=raw();val result=fit(b,items,layout);b.recycle();result
        }
    }
    fun fit(raw: Bitmap,items: List<Gear>,layout: Layout): Dressed {
        val w=raw.width;val h=raw.height;val original=IntArray(w*h);raw.getPixels(original,0,w,0,0,w,h)
        val pixels=original.copyOf();val feet=mask(w,h,layout.feet);val pants=mask(w,h,layout.trousers)
        val cloth=clothMask(original,w,h)
        val armor=items.firstOrNull { it.kind==Kind.BODY };val trouser=items.firstOrNull { it.kind==Kind.PANTS }
        val boot=items.firstOrNull { it.kind==Kind.BOOTS };val boots=IntArray(w*h)
        for(i in original.indices) {
            if(original[i] ushr 24==0)continue
            val x=i%w;val y=i/w
            if(armor!=null && cloth[i])pixels[i]=material(original[i],armor,x,y)
            if(trouser!=null && pants[i]!=0 && !cloth[i])pixels[i]=material(original[i],trouser,x,y)
            if(boot!=null && feet[i]!=0 && !cloth[i]) {
                pixels[i]=material(original[i],boot,x,y);boots[i]=pixels[i]
            }
        }
        if(items.any { it.kind==Kind.HEAD && it.variantId!="headband" })clearHead(pixels,original,w,h,layout.head)
        return Dressed(Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888).asImageBitmap(),
            Bitmap.createBitmap(boots,w,h,Bitmap.Config.ARGB_8888).asImageBitmap())
    }
    fun rect(x: Float,y: Float,w: Float,h: Float)=listOf(x,y,x+w,y,x+w,y+h,x,y+h)
    fun obstacle(pose: String): Layout {
        val f=ObstacleFit056.forPose(pose)
        // These are material masks inside the native alpha, not replacement boot outlines.
        val feet=f.boots.map { p ->
            val cx=(p[0]+p[2]+p[4]+p[6])/4;val cy=(p[1]+p[3]+p[5]+p[7])/4
            p.mapIndexed { i,v -> if(i%2==0)cx+(v-cx)*1.35f else cy+(v-cy)*1.3f-4 }
        }
        val head=f.head
        return Layout(feet,listOf(f.hips)+f.legs.flatMap { leg ->
            leg.zipWithNext().map { (a,b) ->
                val d=b-a;val n=Offset(-d.y,d.x)/d.getDistance().coerceAtLeast(.1f)*12f
                listOf(a.x+n.x,a.y+n.y,a.x-n.x,a.y-n.y,b.x-n.x,b.y-n.y,b.x+n.x,b.y+n.y)
            }
        },Head(head.take(4),Offset(head[0]+head[2]*.5f,head[1]+head[3]-2),head[4]))
    }
    private val battleNecks=listOf(Offset(109f,66f),Offset(105f,69f),Offset(124f,80f),Offset(124f,95f),
        Offset(113f,111f),Offset(94f,58f),Offset(65f,132f),Offset(116f,66f))
    fun battleHead(pose: String): Head {
        val cell=EquipmentFit.cell(pose);val b=MotionAtlas.worn[cell][0]
        val bob=EquipmentFit.bob(pose)
        return Head(listOf(b[0].toFloat(),b[1]+bob,b[2].toFloat(),b[3].toFloat()),
            battleNecks[cell]+Offset(0f,bob),b[4].toFloat())
    }
    val front=Layout(listOf(rect(20f,145f,40f,45f),rect(68f,145f,40f,45f)),
        listOf(rect(28f,129f,72f,53f)),Head(listOf(26f,4f,75f,60f),Offset(64f,61f)))
    private val sideHeads=listOf(
        Head(listOf(68f,3f,64f,58f),Offset(104f,55f)),Head(listOf(68f,3f,65f,55f),Offset(105f,52f)),
        Head(listOf(76f,4f,57f,61f),Offset(107f,60f)),Head(listOf(67f,3f,65f,57f),Offset(104f,53f)),
        Head(listOf(63f,3f,64f,57f),Offset(87f,55f)),Head(listOf(61f,3f,64f,55f),Offset(86f,52f)),
        Head(listOf(60f,3f,62f,62f),Offset(84f,60f)),Head(listOf(63f,3f,64f,56f),Offset(87f,53f)))
    private val sideFeet=listOf(
        listOf(listOf(48f,133f,73f,146f,69f,190f,28f,190f),rect(108f,139f,50f,51f)),
        listOf(listOf(51f,130f,88f,126f,101f,152f,65f,177f),rect(77f,139f,43f,51f)),
        listOf(listOf(41f,137f,66f,150f,64f,190f,21f,190f),listOf(114f,135f,139f,135f,158f,180f,117f,190f)),
        listOf(rect(74f,147f,47f,43f),listOf(96f,131f,119f,136f,103f,168f,71f,166f)),
        listOf(rect(26f,138f,49f,52f),listOf(112f,133f,136f,145f,157f,190f,111f,190f)),
        listOf(rect(76f,141f,43f,49f),listOf(91f,129f,113f,138f,122f,167f,91f,174f)),
        listOf(listOf(49f,136f,72f,142f,63f,190f,19f,190f),listOf(114f,139f,139f,140f,158f,190f,113f,190f)),
        listOf(rect(75f,144f,45f,46f),listOf(92f,129f,113f,138f,124f,166f,91f,174f)))
    fun side(index: Int)=Layout(sideFeet[index],listOf(rect(25f,116f,140f,68f)),sideHeads[index])
}
