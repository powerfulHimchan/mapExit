package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class TrapProgressionTest {
    @Test
    fun reactionTimeDecreasesGraduallyAcrossAllFloorsAndNeverFallsBelowItsFloor50Value() {
        for (type in 0..4) {
            var previous = Long.MAX_VALUE
            for (floor in 1..50) {
                val t = Traps.create(State(bootstrapped = true, floor = floor), type)
                assertEquals(2000L - (floor - 1) * 10, t.limitMs)
                assertTrue(t.limitMs < previous)
                previous = t.limitMs
            }
            assertEquals(1510L, Traps.create(State(floor = 1000), type).limitMs)
        }
    }

    @Test
    fun higherFloorsAddInputsWithoutMakingMemoryFlashesUnreadablyShort() {
        for (floor in 1..50) {
            val s = State(bootstrapped = true, floor = floor)
            val pot = Traps.create(s, 0)
            val wall = Traps.create(s, 2)
            val memory = Traps.create(s, 3)
            assertEquals(2 + (floor - 1) / 20, pot.pattern.size)
            assertEquals(4 + (floor - 1) / 20, wall.required)
            assertEquals(3 + (floor - 1) / 20, memory.pattern.size)
            assertTrue(memory.revealMs / memory.pattern.size >= 300)
        }
    }

    @Test
    fun trapNecklacePreservesItsTimeAdvantageAtEveryDepth() {
        for (floor in listOf(1, 10, 25, 50)) {
            val s = State(bootstrapped = true, floor = floor)
            val normal = Traps.create(s.copy(), 3)
            s.inventory.add(Gear(99, Kind.NECKLACE, "함정의 목걸이", charm = Charm.TRAP))
            Equipment.equip(s, 99, Slot.NECK)
            val assisted = Traps.create(s, 3)
            assertEquals((normal.limitMs * 1.25).toLong(), assisted.limitMs)
            assertTrue(assisted.revealMs > normal.revealMs)
        }
    }

    @Test
    fun everyObstacleStillUsesTheSameSuccessfulActionAtLowAndHighFloors() {
        for (floor in listOf(1, 10, 25, 50)) for (type in 0..4) {
            val e = GameEngine()
            e.enterFloor(floor)
            e.state.mode = Mode.TRAP
            val t = Traps.create(e.state, type).copy(phase = TrapPhase.ACTIVE)
            e.state.trap = t
            when (type) {
                0,
                3 -> t.pattern.forEach { e.press(it) }
                1 -> {
                    t.elapsedMs = t.limitMs / 2
                    e.press(0)
                }
                2 -> repeat(t.required) { e.press(t.pattern[0]) }
                4 -> {
                    t.elapsedMs = t.limitMs / 2
                    e.press(1)
                }
            }
            assertTrue("floor $floor trap $type", t.success)
            val restored = SaveCodec.decode(SaveCodec.encode(e.state))
            assertEquals(t, restored.trap)
        }
    }

    @Test
    fun eventAllocationMatchesTheDocumentedCountsAndDoesNotOverlapStartOrExit() {
        for (floor in listOf(1, 14, 15, 29, 30, 44, 45, 50)) {
            val s = State(bootstrapped = true, rng = floor.toLong())
            val d = MapGenerator.generate(s, floor)
            assertEquals(97, d.cells.count { !it.wall })
            assertEquals(7 + floor / 15, d.cells.count { it.event == Event.MONSTER })
            for ((event, count) in
                listOf(
                    Event.CHEST to 2,
                    Event.MERCHANT to
                        if (floor in VisitorSchedule.floors(s.visitorSeed!!, Event.MERCHANT)) 1
                        else 0,
                    Event.NPC to
                        if (floor in VisitorSchedule.floors(s.visitorSeed!!, Event.NPC)) 1 else 0,
                    Event.HEAL to 2,
                    Event.TRAP to 3,
                    Event.EXIT to 1,
                )) {
                assertEquals("floor $floor $event", count, d.cells.count { it.event == event })
            }
            assertEquals(Event.NONE, d.here().event)
            assertTrue(d.cells.filter { it.wall }.all { it.event == Event.NONE })
        }
    }
}
