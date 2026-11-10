package com.powerfulhimchan.mapexit

import androidx.compose.ui.geometry.Offset
import com.powerfulhimchan.mapexit.core.Kind

/** Native 192px landmarks; thighs, shins and boots follow their own painted limb. */
internal object ObstacleFit056 {
    data class Fit(val head: List<Float>,val torso: List<Float>,val belt: List<Float>,
        val hips: List<Float>,val legs: List<List<Offset>>,val boots: List<List<Float>>) {
        val coat get()=torso.mapIndexed { i,v -> if(i%2==0) {
            val middle=(torso[0]+torso[2]+torso[4]+torso[6])/4
            v+if(v<middle)-5f else 5f
        } else v+if(i<4)-5f else 5f }
    }
    private fun p(x: Int,y: Int)=Offset(x.toFloat(),y.toFloat())
    private fun q(vararg n: Int)=n.map(Int::toFloat)
    private fun rect(x: Int,y: Int,w: Int,h: Int)=q(x,y,x+w,y,x+w,y+h,x,y+h)
    private fun leg(hx: Int,hy: Int,kx: Int,ky: Int,ax: Int,ay: Int)=listOf(p(hx,hy),p(kx,ky),p(ax,ay))
    private fun fit(head: List<Float>,torso: List<Float>,belt: List<Float>,hips: List<Float>,
        left: List<Offset>,right: List<Offset>,bootLeft: List<Float>,bootRight: List<Float>) =
        Fit(head,torso,belt,hips,listOf(left,right),listOf(bootLeft,bootRight))
    private val pushRight=fit(q(82,28,57,50,12),q(72,70,119,82,108,124,57,114),rect(64,101,48,13),
        q(67,121,112,128,118,140,59,137),leg(77,134,53,154,29,178),leg(109,136,124,151,126,178),
        q(44,150,61,161,35,188,15,185),q(115,151,134,151,148,188,115,188))
    private fun mirror(f: Fit): Fit {
        fun points(a: List<Float>)=a.mapIndexed { i,v -> if(i%2==0)192-v else v }
        return Fit(listOf(192-f.head[0]-f.head[2],f.head[1],f.head[2],f.head[3],-f.head[4]),
            points(f.torso),points(f.belt),points(f.hips),f.legs.map { leg -> leg.map { Offset(192-it.x,it.y) } },f.boots.map(::points))
    }
    private val fits=listOf(
        fit(q(66,12,62,52,0),rect(65,68,61,70),rect(72,101,50,14),rect(72,133,49,15),
            leg(82,144,80,162,80,181),leg(112,144,114,162,115,181),rect(68,153,25,35),rect(101,153,34,35)),
        fit(q(53,21,61,51,-8),q(61,70,110,63,134,133,70,141),q(71,101,122,97,125,111,73,115),q(77,134,132,126,137,142,76,149),
            leg(84,145,66,160,46,180),leg(126,143,137,157,138,179),q(58,155,76,165,53,188,31,185),rect(126,153,31,35)),
        fit(q(87,38,57,49,12),q(67,77,116,85,112,124,57,114),q(62,103,112,108,112,120,60,116),q(62,119,111,126,116,137,62,137),
            leg(75,131,97,151,95,177),leg(106,129,124,143,112,166),q(87,149,102,148,109,188,91,188),q(113,143,130,150,118,174,102,167)),
        fit(q(96,90,49,44,14),q(67,115,113,116,114,148,49,146),rect(60,136,48,12),rect(54,146,63,11),
            leg(69,152,60,165,65,181),leg(103,152,117,166,125,181),q(53,164,71,163,80,188,46,188),q(109,165,128,166,141,188,111,188)),
        pushRight,mirror(pushRight),
        fit(q(65,12,60,51,0),rect(65,68,62,66),rect(70,101,52,14),rect(72,133,49,13),
            leg(82,142,82,162,82,181),leg(111,142,112,162,113,181),rect(69,153,27,35),rect(101,153,31,35)),
        fit(q(97,21,58,51,10),q(76,70,123,81,120,130,59,119),q(68,104,118,111,116,124,65,116),q(66,126,116,134,124,147,60,139),
            leg(77,137,56,157,46,177),leg(110,141,127,151,111,176),q(47,156,65,162,50,188,28,185),q(116,150,134,159,120,187,101,180)),
        fit(q(84,58,60,49,12),q(66,106,113,111,110,149,55,141),q(63,126,110,130,109,142,60,138),rect(61,143,48,13),
            leg(73,151,62,165,77,180),leg(103,151,121,166,113,181),q(62,166,80,162,97,188,66,188),q(110,165,130,168,133,188,104,188)),
        fit(q(54,18,61,51,-17),q(66,75,111,63,128,129,74,141),q(76,104,123,99,125,113,77,118),q(77,135,125,131,133,146,73,149),
            leg(82,145,66,160,48,180),leg(122,143,136,158,137,181),q(55,155,74,165,56,188,32,184),rect(124,154,35,34)),
        fit(q(65,12,59,51,0),q(67,68,100,71,111,134,66,137),rect(68,103,44,13),rect(70,134,41,12),
            leg(79,143,80,163,82,181),leg(103,143,106,163,108,181),rect(68,153,27,35),rect(98,153,33,35)),
        fit(q(84,48,56,50,11),q(65,96,109,99,111,142,51,136),q(59,121,107,124,106,136,56,133),rect(57,139,52,15),
            leg(69,148,57,165,63,181),leg(103,149,116,165,111,182),q(51,164,70,165,82,188,49,188),q(104,166,122,166,136,188,102,188)),
    )
    fun forPose(pose: String)=fits[ObstacleAtlas.poses.indexOf(pose).coerceAtLeast(0)]
    private fun bone(a: Offset,b: Offset,widthA: Float,widthB: Float): List<Float> {
        val d=b-a;val normal=Offset(-d.y,d.x)/d.getDistance().coerceAtLeast(.01f)
        val p=listOf(a+normal*widthA/2,a-normal*widthA/2,b-normal*widthB/2,b+normal*widthB/2)
        return p.flatMap { listOf(it.x,it.y) }
    }
    fun patches(kind: Kind,pose: String): List<EquipmentFit.Patch> {
        val f=forPose(pose)
        return when(kind) {
            Kind.PANTS -> listOf(EquipmentFit.Patch(listOf(0f,0f,1f,.30f),f.hips))+f.legs.flatMapIndexed { i,l ->
                val x=i*.5f
                listOf(EquipmentFit.Patch(listOf(x,.27f,x+.5f,.65f),bone(l[0],l[1],18f,16f)),
                    EquipmentFit.Patch(listOf(x,.62f,x+.5f,1f),bone(l[1],l[2],15f,12f)))
            }
            Kind.BOOTS -> f.boots.mapIndexed { i,p -> EquipmentFit.Patch(listOf(i*.5f,0f,(i+1)*.5f,1f),p) }
            else -> emptyList()
        }
    }
}
