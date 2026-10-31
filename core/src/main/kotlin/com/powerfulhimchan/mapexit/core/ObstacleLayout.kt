package com.powerfulhimchan.mapexit.core

import kotlin.math.*

data class BladeStage(val heroX: Float,val heroWidth: Float,val bladeLeft: Float,val bladeRight: Float,val jump: Float)

object ObstacleLayout {
    /** Finish the last side step, hold the landing, then return to the middle. */
    fun sequenceHeroCenter(t: TrapState): Float {
        fun center(side: Int?) = when (side) { 0 -> .27f; 1 -> .75f; else -> .5f }
        val last = t.pattern.getOrNull(t.progress - 1)
        if (t.type !in listOf(0, 3)) return .5f
        if (t.phase != TrapPhase.RESULT) return center(last)
        val final = center(t.action.takeIf { it in 0..1 } ?: last)
        val from = if (t.action !in 0..1) final else center(if (t.success) t.pattern.getOrNull(t.progress - 2) else last)
        val land = BattleAnimation.smooth(t.elapsedMs / 250f)
        val landed = from + (final - from) * land
        val returnToCenter = BattleAnimation.smooth((t.elapsedMs - 400) / 350f)
        return landed + (.5f - landed) * returnToCenter
    }

    fun blade(width: Float,height: Float,t: TrapState): BladeStage {
        val heroWidth=(min(width*.23f,height*.28f)/32).toInt().coerceAtLeast(1)*32f
        val progress=if(t.phase==TrapPhase.RESULT) BattleAnimation.smooth(t.elapsedMs/650f) else 0f
        val center=width*(.18f+.32f*progress)
        val jump=if(t.phase==TrapPhase.RESULT && t.action==0) sin(progress*PI.toFloat()).coerceAtLeast(0f)*height*.27f else 0f
        return BladeStage(center-heroWidth/2,heroWidth,width*.30f,width*.39f,jump)
    }
}
