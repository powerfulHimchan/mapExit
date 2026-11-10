package com.powerfulhimchan.mapexit

import com.powerfulhimchan.mapexit.core.BattleAnimation
import com.powerfulhimchan.mapexit.core.Kind
import com.powerfulhimchan.mapexit.core.SpriteFrames
import kotlin.math.*

/** A presentation-only loop: it never walks the dungeon or starts a combat turn. */
internal object CampMotion056 {
    const val PERIOD = 16000L
    data class Pose(val travel: Float,val stride: Float,val bob: Float,val lean: Float,
        val angle: Float,val reach: Float,val armLift: Float)
    fun sample(clock: Long,kind: Kind?): Pose {
        val t=Math.floorMod(clock,PERIOD)
        fun between(start: Long,end: Long,a: Float,b: Float): Float =
            a+(b-a)*BattleAnimation.smooth((t-start).toFloat()/(end-start))
        val travel=when {
            t<1600 -> 0f
            t<4000 -> between(1600,4000,0f,-.105f)
            t<6400 -> between(4000,6400,-.105f,.105f)
            t<8500 -> between(6400,8500,.105f,0f)
            else -> 0f
        }
        val walking=t in 1600..8499
        val envelope=if(walking)min(BattleAnimation.smooth((t-1600)/260f),BattleAnimation.smooth((8500-t)/260f)) else 0f
        val step=sin((t-1600)*.011f)*envelope
        val practice=t-9000
        val swing=when {
            practice in 0..759 -> BattleAnimation.pose(kind,practice)
            practice in 1600..2359 -> BattleAnimation.pose(kind,practice-1600,true)
            else -> BattleAnimation.pose(kind,BattleAnimation.DURATION_MS)
        }
        val inspect=if(t in 12500..14499)sin((t-12500)/2000f*PI.toFloat()) else 0f
        return Pose(travel,step*.85f,abs(step)*.7f+sin(t*.0026f)*.14f,
            step*.9f-swing.lean*.28f,-swing.angle-inspect*20f,swing.reach,
            swing.armLift*.65f+inspect*2.5f)
    }
}

/** A block can recoil without abandoning the raised guard for a different body cell. */
internal object BattlePose056 {
    fun frame(action: String?,kind: Kind?,elapsed: Long,clock: Long,damaged: Boolean,dead: Boolean): String =
        SpriteFrames.hero(action,kind,elapsed,clock,damaged=damaged && action!="guard",dead=dead)
}
