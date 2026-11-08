package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class ArmKinematicsTest {
    private fun close(expected: Float, actual: Float) = assertEquals(expected.toDouble(), actual.toDouble(), .002)
    @Test fun bonesKeepTheirLengthThroughEveryWeaponSwing() {
        for (kind in listOf(Kind.SWORD, Kind.SPEAR, Kind.HAMMER, Kind.CLAW, Kind.WHIP)) for (strong in listOf(false,true)) {
            for (ms in 0L..760L step 4) {
                val p = BattleAnimation.pose(kind, ms, strong)
                val target = ArmKinematics.Point(4 + p.reach * 7, 10 - p.advance * 5 - p.armLift)
                val arm = ArmKinematics.solve(ArmKinematics.Point(0f,0f), target, 6.5f, 6.5f)
                close(6.5f,(arm.elbow-arm.shoulder).length())
                close(6.5f,(arm.wrist-arm.elbow).length())
                assertTrue(arm.wrist.x.isFinite() && arm.wrist.y.isFinite())
            }
        }
    }
    @Test fun unreachableAndCoincidentTargetsRemainFinite() {
        for (target in listOf(ArmKinematics.Point(0f,0f),ArmKinematics.Point(100f,-100f))) {
            val p=ArmKinematics.solve(ArmKinematics.Point(0f,0f),target,6f,7f)
            close(6f,(p.elbow-p.shoulder).length());close(7f,(p.wrist-p.elbow).length())
        }
    }
    @Test fun bothHandsReachOneTwoHandedHandleThroughoutSwing() {
        val right=ArmKinematics.Point(23f,23f);val left=ArmKinematics.Point(12f,22f)
        for (kind in listOf(Kind.SPEAR,Kind.HAMMER)) for (ms in 0L..760L step 4) {
            val p=BattleAnimation.pose(kind,ms,true)
            val rad=Math.toRadians(p.angle.toDouble())
            val support=ArmKinematics.Point(kotlin.math.sin(rad).toFloat()*4,-kotlin.math.cos(rad).toFloat()*4)
            val wanted=right+ArmKinematics.Point(4+p.reach*7,10-p.advance*5-p.armLift)
            val hand=ArmKinematics.sharedTarget(right,left,wanted,support,12.8f)
            assertTrue((hand-right).length()<=12.802f)
            assertTrue((hand+support-left).length()<=12.802f)
        }
    }
}
