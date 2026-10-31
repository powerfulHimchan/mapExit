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
    @Test fun finalSequenceStepLandsBeforeReturningToTheMiddle() {
        for (type in listOf(0, 3)) for (last in 0..1) {
            val previous = 1 - last
            val t = Traps.create(State(), type).copy(pattern = listOf(previous, last), phase = TrapPhase.ACTIVE, progress = 1)
            assertEquals(if (previous == 0) .27f else .75f, ObstacleLayout.sequenceHeroCenter(t), .001f)
            Traps.tap(t, last)
            assertTrue(t.success); assertEquals(last, t.action)
            val from = ObstacleLayout.sequenceHeroCenter(t)
            t.elapsedMs = 250
            val landed = ObstacleLayout.sequenceHeroCenter(t)
            assertEquals(if (last == 0) .27f else .75f, landed, .001f)
            assertNotEquals(from, landed)
            t.elapsedMs = 400; assertEquals(landed, ObstacleLayout.sequenceHeroCenter(t), .001f)
            t.elapsedMs = 575
            assertTrue(ObstacleLayout.sequenceHeroCenter(t) in minOf(landed, .5f)..maxOf(landed, .5f))
            val restored = SaveCodec.decode(SaveCodec.encode(State(bootstrapped = true, trap = t)))
            assertEquals(ObstacleLayout.sequenceHeroCenter(t), ObstacleLayout.sequenceHeroCenter(restored.trap!!), .001f)
            t.elapsedMs = 750; assertEquals(.5f, ObstacleLayout.sequenceHeroCenter(t), .001f)
            assertTrue(t.elapsedMs < Traps.RESULT_MS)
        }
    }

    @Test fun wrongSequenceInputAndTimeoutReturnSmoothlyFromTheirLastPosition() {
        for (wrongInput in listOf(false, true)) {
            val t = Traps.create(State(), 3).copy(pattern = listOf(0, 1), phase = TrapPhase.ACTIVE, progress = 1)
            if (wrongInput) Traps.tap(t, 0) else Traps.tick(t, t.limitMs)
            assertFalse(t.success)
            assertEquals(.27f, ObstacleLayout.sequenceHeroCenter(t), .001f)
            t.elapsedMs = 750
            assertEquals(.5f, ObstacleLayout.sequenceHeroCenter(t), .001f)
        }
    }

}
