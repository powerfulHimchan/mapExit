package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class ObstaclePresentationTest {
    @Test
    fun trapTextNeverLeaksPatternSideOrReleaseAnswer() {
        val e = GameEngine()
        e.enterFloor(1)
        for (type in 0..4) {
            val t = Traps.create(e.state, type)
            e.state.trap = t
            e.state.mode = Mode.TRAP
            for (phase in listOf(TrapPhase.PREPARE, TrapPhase.REVEAL, TrapPhase.ACTIVE)) {
                t.phase = phase
                for (elapsed in listOf(0L, 250L, 700L, 1000L)) {
                    t.elapsedMs = elapsed
                    for (text in
                        listOf(t.prompt(), t.cue(), t.instruction()) +
                            e.controls.buttons().map { it.label }) {
                        assertFalse(text, text.contains("A") || text.contains("B"))
                        assertFalse(text, text.contains("입력 금지") || text.contains("지금 놓"))
                    }
                    if (phase == TrapPhase.ACTIVE)
                        assertEquals(
                            if (t.physicalAction) listOf("점프", "몸 숙이기")
                            else listOf("왼쪽 동작", "오른쪽 동작"),
                            e.controls.buttons().map { it.label },
                        )
                }
            }
        }
    }

    @Test
    fun memoryFlashesEachSideOnlyDuringRevealThenHidesTheAnswer() {
        val t = Traps.create(State(), 3).copy(pattern = listOf(0, 1, 0), revealMs = 1200)
        t.phase = TrapPhase.REVEAL
        for ((time, side) in listOf(20L to 0, 420L to 1, 820L to 0)) {
            t.elapsedMs = time
            assertEquals(side, t.visualSide())
            assertEquals("기억해라", t.prompt())
        }
        t.phase = TrapPhase.ACTIVE
        assertNull(t.visualSide())
        val saved = t.copy()
        repeat(10) { t.visualSide() }
        assertEquals(saved, t)
    }

    @Test
    fun topInventoryAndRecordsStopMovementAndRestoreTheSameTile() {
        for (menu in listOf("inventory", "help")) {
            val e = GameEngine()
            e.enterFloor(1)
            e.state.mode = Mode.MOVING
            val tile = e.state.dungeon!!.x to e.state.dungeon!!.y
            assertTrue(e.openDungeonPanel(menu))
            assertEquals(menu, e.state.menu!!.type)
            e.navigateBack()
            repeat(8) { e.tick(250) }
            assertEquals(Mode.EXPLORE, e.state.mode)
            assertEquals(tile, e.state.dungeon!!.x to e.state.dungeon!!.y)
        }
    }

    @Test
    fun topDirectionResetDoesNotMoveOrSkipTheCurrentTile() {
        val d = Dungeon(9, 9, MutableList(81) { Cell() }, x = 4, y = 4, direction = 0)
        d.here().wall = false
        for (i in 0..3) d.cell(4 + MapGenerator.dx[i], 4 + MapGenerator.dy[i]).wall = false
        val e = GameEngine(State(bootstrapped = true, dungeon = d, mode = Mode.EXPLORE, floor = 1))
        val before = SaveCodec.encode(e.state)
        e.press(1)
        assertTrue(e.controls.directionSelectionActive())
        e.controls.resetDirections()
        assertFalse(e.controls.directionSelectionActive())
        assertEquals(before, SaveCodec.encode(e.state))
    }
}
