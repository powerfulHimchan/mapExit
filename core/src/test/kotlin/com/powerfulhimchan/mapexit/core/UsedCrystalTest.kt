package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class UsedCrystalTest {
    private fun map(): Dungeon = Dungeon(9,9,MutableList(81) { Cell() },4,4,0,arrivalDirection=0).also { m ->
        for (y in 1..7) m.cell(4,y).wall = false
        m.here().event = Event.HEAL
    }

    @Test fun usingAndReloadingCrystalKeepsItsCellAndCannotGrantAnotherHeal() {
        val m = map(); val e = GameEngine(State(bootstrapped = true, dungeon = m, floor = 1, mode = Mode.EXPLORE, hp = 100.0))
        val heal = e.choices().first { it.action == "heal" }; e.choose(heal)
        assertTrue(m.here().resolved); assertEquals(Event.HEAL, m.here().event); assertEquals(262.0, e.state.hp, .001)
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        cold.choose(heal); assertEquals(262.0, cold.state.hp, .001)
        assertFalse(cold.choices().any { it.action == "heal" })
        assertEquals(Event.HEAL, cold.state.dungeon!!.here().event)
    }

    @Test fun depletedCrystalRemainsVisibleAndItsNearCameraPoseDoesNotJump() {
        val m = map(); val fallback = DungeonViewPose(4.5f,4.5f,(-PI/2).toFloat())
        val ready = DungeonFixtures.camera(m, fallback)
        m.here().resolved = true
        assertEquals(ready, DungeonFixtures.camera(m, fallback))
        assertEquals(listOf(Event.HEAL), DungeonFixtures.visible(m, ready).map { it.event })
        m.cell(4,5).wall = true
        assertTrue(DungeonFixtures.visible(m, DungeonViewPose(4.5f,6.5f,(-PI/2).toFloat())).isEmpty())
    }
}
