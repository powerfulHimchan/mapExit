package com.powerfulhimchan.mapexit.core

import kotlin.math.*

data class BladeStage(val heroX: Float,val heroWidth: Float,val bladeLeft: Float,val bladeRight: Float,val jump: Float)

object ObstacleLayout {
    fun blade(width: Float,height: Float,t: TrapState): BladeStage {
        val heroWidth=(min(width*.23f,height*.28f)/32).toInt().coerceAtLeast(1)*32f
        val progress=if(t.phase==TrapPhase.RESULT) BattleAnimation.smooth(t.elapsedMs/650f) else 0f
        val center=width*(.18f+.32f*progress)
        val jump=if(t.phase==TrapPhase.RESULT && t.action==0) sin(progress*PI.toFloat()).coerceAtLeast(0f)*height*.27f else 0f
        return BladeStage(center-heroWidth/2,heroWidth,width*.30f,width*.39f,jump)
    }
}
