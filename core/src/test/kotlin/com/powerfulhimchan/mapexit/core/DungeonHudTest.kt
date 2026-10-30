package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class DungeonHudTest {
    private fun corridor(): GameEngine {
        val d = Dungeon(9, 9, MutableList(81) { Cell() }, x = 4, y = 4, direction = 0)
        for (y in 2..6) d.cell(4, y).wall = false
        return GameEngine(State(bootstrapped = true, dungeon = d, floor = 1, mode = Mode.EXPLORE))
    }

    private fun win(e: GameEngine) {
        e.startBattle(false)
        e.state.enemy!!.hp = 1.0
        e.state.rng = 0L
        e.attack(false)
        assertEquals(Mode.VICTORY, e.state.mode)
    }

    @Test
    fun levelUpNoticeSurvivesSaveAndReportsAllEarnedPoints() {
        val e = GameEngine(State(experience = 79))
        e.enterFloor(1)
        win(e)
        assertEquals(LevelUpNotice(1, 2, 3), e.state.levelUpNotice)
        assertEquals(3, e.state.points)
        val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertEquals(e.state.levelUpNotice, restored.state.levelUpNotice)
        restored.state.experience = 159
        win(restored)
        assertEquals(LevelUpNotice(1, 3, 6), restored.state.levelUpNotice)
        assertEquals(6, restored.state.points)
    }

    @Test
    fun multiLevelRewardAndOldSaveDefaultsDoNotDuplicateGrowthPoints() {
        val e = GameEngine(State(experience = 239))
        e.enterFloor(1)
        win(e)
        assertEquals(LevelUpNotice(1, 3, 6), e.state.levelUpNotice)
        val encoded = SaveCodec.encode(e.state)
        assertEquals(6, GameEngine(SaveCodec.decode(encoded)).state.points)
        val oldSave = SaveCodec.encode(State()).replace("\"levelUpNotice\":null,", "")
        assertNull(SaveCodec.decode(oldSave).levelUpNotice)
    }

    @Test
    fun headerMenusStopWalkingAndReturnToSameTile() {
        for (panel in listOf("equipment", "growth")) {
            val e = corridor()
            e.choose(e.choices().first { it.action == "move" && it.argument == 0L })
            assertEquals(Mode.MOVING, e.state.mode)
            val position = e.state.dungeon!!.y
            assertTrue(e.openDungeonPanel(panel))
            repeat(8) { e.tick(250) }
            assertEquals(position, e.state.dungeon!!.y)
            val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
            assertEquals(panel, restored.state.menu!!.type)
            restored.navigateBack()
            assertEquals(Mode.EXPLORE, restored.state.mode)
            repeat(8) { restored.tick(250) }
            assertEquals(position, restored.state.dungeon!!.y)
        }
    }

    @Test
    fun headerCannotInterruptCombatOrTrapAndGrowthAcknowledgesNotice() {
        val e = corridor()
        e.startBattle(false)
        val saved = SaveCodec.encode(e.state)
        assertFalse(e.openDungeonPanel("equipment"))
        assertFalse(e.openDungeonPanel("growth"))
        assertEquals(saved, SaveCodec.encode(e.state))
        e.state.mode = Mode.TRAP
        assertFalse(e.openDungeonPanel("growth"))
        e.state.mode = Mode.EXPLORE
        e.state.levelUpNotice = LevelUpNotice(1, 2, 3)
        assertTrue(e.openDungeonPanel("growth"))
        assertNull(e.state.levelUpNotice)
        assertFalse(e.openDungeonPanel("bank"))
    }

    @Test
    fun deadEndHintClearsOnForwardAndBackwardDepartureButStaysAgainstWall() {
        for (backwards in listOf(false, true)) {
            val e = corridor()
            val d = e.state.dungeon!!
            d.y = 3
            e.choose(e.choices().first { it.action == "move" && it.argument == 0L })
            assertEquals(2, d.y)
            assertEquals("막다른 길입니다", e.state.message)
            if (!backwards) d.direction = 2
            e.choose(e.choices().first { it.action == "move" && it.argument == 2L })
            assertEquals(3, d.y)
            assertFalse(e.state.message.contains("막다른 길"))
        }
        val e = corridor()
        e.state.dungeon!!.y = 2
        e.state.message = "막다른 길입니다"
        e.state.mode = Mode.MOVING
        repeat(3) { e.tick(250) }
        assertEquals(2, e.state.dungeon!!.y)
        assertEquals("막다른 길입니다", e.state.message)
    }

    @Test
    fun leavingDeadEndPreservesNewTileEventMessage() {
        val e = corridor()
        val d = e.state.dungeon!!
        d.y = 2
        e.state.message = "막다른 길입니다"
        d.cell(4, 3).event = Event.CHEST
        e.choose(e.choices().first { it.action == "move" && it.argument == 2L })
        assertEquals(Mode.ENCOUNTER, e.state.mode)
        assertEquals("상자를 열까요?", e.state.message)
    }
}
