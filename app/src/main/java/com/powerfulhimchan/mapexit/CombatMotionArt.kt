package com.powerfulhimchan.mapexit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.powerfulhimchan.mapexit.core.*
import kotlin.math.*

internal fun DrawScope.combatBody(type:String,x:Float,y:Float,width:Float,tint:Color?,articulated:Boolean,stride:Float,opacity:Float=1f) {
    val u=spriteUnit(width)
    val height=spriteHeight(type,width)
    if(type.substringAfterLast('_') !in listOf("hero","heroBack","knight","boss")) { sprite(type,x,y,width,flash=tint,opacity=opacity);return }
    val split=y+height-u*11
    clipRect(x,y,x+32*u,split) { sprite(type,x,y,width,flash=tint,articulated=articulated,opacity=opacity) }
    for(side in 0..1) {
        val shift=if(side==0)stride else -stride
        withTransform({translate(shift*u*3,-abs(shift)*u)}) {
            clipRect(x+side*16*u,split,x+(side+1)*16*u,y+height) { sprite(type,x,y,width,flash=tint,opacity=opacity) }
        }
    }
}

internal fun DrawScope.batBody(x:Float,y:Float,width:Float,clock:Long,tint:Color?,opacity:Float,type:String="bat") {
    val u=spriteUnit(width);val h=spriteHeight(type,width)
    clipRect(x+11*u,y,x+21*u,y+h) { sprite(type,x,y,width,flash=tint,opacity=opacity) }
    for(side in 0..1) withTransform({scale(1f,1+sin(clock*.022f)*.20f,Offset(x+16*u,y+h*.45f))}) {
        clipRect(x+side*21*u,y,x+(if(side==0)11 else 32)*u,y+h) { sprite(type,x,y,width,flash=tint,opacity=opacity) }
    }
}

internal fun DrawScope.weaponTrail(kind:Kind,hand:Offset,unit:Float,elapsed:Long,strong:Boolean,direction:Float=1f) {
    if(elapsed !in 190..450) return
    val fade=if(elapsed<320)(elapsed-190)/130f else 1-(elapsed-320)/130f
    val radius=unit*(if(kind==Kind.SPEAR)29 else if(kind==Kind.CLAW)17 else 24)
    val color=if(strong || kind==Kind.HAMMER)amber else pale
    var previous:Offset?=null
    for(i in 7 downTo 0) {
        val p=BattleAnimation.pose(kind,(elapsed-i*9).coerceAtLeast(0),strong)
        val a=(p.angle*direction-90)*PI/180
        val end=hand+Offset(cos(a).toFloat(),sin(a).toFloat())*radius
        previous?.let { drawLine(color.copy(alpha=(1-i/8f)*fade*.42f),it,end,(unit*.9f).coerceAtLeast(1f)) }
        previous=end
    }
}

internal fun DrawScope.impactParticles(at:Offset,elapsed:Long,impact:BattleImpact,heavy:Boolean,floor:Float) {
    val age=elapsed-BattleAnimation.HIT_MS
    if(age !in 0..350 || impact.outcome in listOf(ImpactOutcome.MISS,ImpactOutcome.DODGE))return
    val t=age/350f;val fade=(1-t)*(1-t)
    val color=when { impact.outcome in listOf(ImpactOutcome.GUARD,ImpactOutcome.BLOCK)->mint;impact.target==ImpactTarget.PLAYER->red;heavy || impact.critical->amber;else->pale }
    val n=if(heavy || impact.critical)12 else 7
    for(i in 0 until n) {
        val a=i*PI*2/n+.27
        val speed=if(heavy)58f else 35f
        val offset=Offset(cos(a).toFloat(),sin(a).toFloat())*(5+t*speed)+Offset(0f,t*t*18)
        val tail=Offset(cos(a).toFloat(),sin(a).toFloat())*(3+6*(1-t))
        drawLine(color.copy(alpha=fade),at+offset,at+offset+tail,if(impact.critical)3f else 2f)
    }
    if(age<80) drawCircle(Color.White.copy(alpha=(1-age/80f)*.8f),3f,at)
    if(impact.outcome==ImpactOutcome.GUARD || impact.outcome==ImpactOutcome.BLOCK)
        drawCircle(mint.copy(alpha=fade),12+t*18,at,style=Stroke(2f))
    if(heavy) {
        drawOval(amber.copy(alpha=fade*.4f),Offset(at.x-12-t*35,floor-3-t*4),androidx.compose.ui.geometry.Size(24+t*70,6+t*8),style=Stroke(2f))
        for(i in 0..4) pixelRect(muted.copy(alpha=fade*.5f),at.x+(i-2)*(5+t*18),floor-5-t*(8+i%2*8),3f,3f)
    }
}
