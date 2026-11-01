package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class LevelMilestoneTest {
    private fun win(e: GameEngine) {
        e.enterFloor(1); e.startBattle(false)
        e.state.enemy!!.hp = .01; e.state.rng = 0; e.attack(false)
        assertEquals(Mode.VICTORY,e.state.mode)
    }

    @Test fun everyFifthLevelGrantsFivePointsOnceAndNextLevelReturnsToThree() {
        for (target in listOf(5,10,15,20)) {
            val e = GameEngine(State(level=target-1,experience=(target-1)*80-1,points=7))
            win(e)
            assertEquals(target,e.state.level); assertEquals(12,e.state.points)
            assertEquals(LevelUpNotice(target-1,target,5),e.state.levelUpNotice)
            val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
            assertEquals(12,cold.state.points)
            cold.state.experience = target*80-1; win(cold)
            assertEquals(target+1,cold.state.level); assertEquals(15,cold.state.points)
            assertEquals(LevelUpNotice(target-1,target+1,8),cold.state.levelUpNotice)
        }
    }

    @Test fun oneRewardCrossingTwoMilestonesIncludesBothBonusesInPendingBanner() {
        // LV4 to LV11 costs 4*80 + ... + 10*80 = 3920 XP.
        val e = GameEngine(State(level=4,experience=3919,points=2,levelUpNotice=LevelUpNotice(3,4,3)))
        win(e)
        assertEquals(11,e.state.level)
        assertEquals(27,e.state.points) // seven levels ×3P, plus LV5/LV10 ×2P.
        assertEquals(LevelUpNotice(3,11,28),e.state.levelUpNotice)
        assertEquals(27,GameEngine(SaveCodec.decode(SaveCodec.encode(e.state))).state.points)
    }
}
