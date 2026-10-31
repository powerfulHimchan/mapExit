package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class PhysicalTrapTest {
    private fun trap(type: Int, time: Long, pattern: Int = 1) =
        Traps.create(State(), type)
            .copy(pattern = listOf(pattern), phase = TrapPhase.ACTIVE, elapsedMs = time)

    @Test
    fun bladeSafetyMatchesFullRetractionAtBothEdgesOfTheVisibleOpening() {
        for (time in listOf(0L, 699L, 700L, 1000L, 1300L, 1301L, 1999L)) {
            val t = trap(1, time)
            val safe = time in 700L..1300L
            assertEquals("visible opening at $time", safe, t.bladesRetracted())
            Traps.tap(t, 0)
            assertEquals("jump at $time", safe, t.success)
            assertEquals(time, t.actionAtMs)
            assertEquals(0, t.action)
        }
        val duck = trap(1, 1000)
        Traps.tap(duck, 1)
        assertFalse(duck.success)
    }

    @Test
    fun bladeMotionAndBeamDescentDoNotDependOnTickGranularity() {
        for (type in listOf(1, 4)) {
            val small = trap(type, 0)
            val large = trap(type, 0)
            repeat(20) { Traps.tick(small, 50) }
            repeat(4) { Traps.tick(large, 250) }
            assertEquals(small.bladeExtension(), large.bladeExtension(), .0001f)
            assertEquals(small.beamDescent(), large.beamDescent(), .0001f)
            Traps.tap(small, if (type == 1) 0 else 1)
            Traps.tap(large, if (type == 1) 0 else 1)
            assertTrue(small.success && large.success)
        }
    }

    @Test
    fun fixedActionButtonsNeverDependOnHiddenRandomPattern() {
        for (type in listOf(1, 4)) for (pattern in 0..1) {
            val e = GameEngine()
            e.state.mode = Mode.TRAP
            e.state.trap = trap(type, 1000, pattern)
            assertEquals(listOf("점프", "몸 숙이기"), e.controls.buttons().map { it.label })
            assertNull(e.state.trap!!.visualSide())
            e.press(if (type == 1) 0 else 1)
            assertTrue(e.state.trap!!.success)
        }
    }

    @Test
    fun earlyCrouchFailsWhileApproachingCrouchLetsTheBeamPass() {
        val t = trap(4, 0)
        Traps.tick(t, 250)
        assertTrue(t.beamDescent() > 0f)
        Traps.tap(t, 1)
        assertFalse(t.success)
        assertTrue(t.result.contains("너무 일찍"))
        val safe = trap(4, 1000)
        Traps.tap(safe, 1)
        assertTrue(safe.success)
        val before = safe.beamDescent()
        Traps.tick(safe, 650)
        assertTrue(safe.beamDescent() > before)
        assertEquals(1f, safe.beamDescent(), .0001f)
        assertEquals(TrapPhase.RESULT, safe.phase)
    }

    @Test
    fun legacyHeldBeamSaveUsesNewSingleCrouchWithoutFreezingOrResettingItsTimer() {
        val s =
            State(
                bootstrapped = true,
                mode = Mode.TRAP,
                trap = trap(4, 750).copy(held = 0, holdStarted = 0, rehold = true),
            )
        val root = Json.parseToJsonElement(SaveCodec.encode(s)).jsonObject
        val oldTrap =
            JsonObject(
                root.getValue("trap").jsonObject.filterKeys { it !in setOf("action", "actionAtMs") }
            )
        val old = JsonObject(root + ("trap" to oldTrap)).toString()
        val e = GameEngine(SaveCodec.decode(old))
        e.tick(100)
        assertEquals(850L, e.state.trap!!.elapsedMs)
        e.press(1)
        assertTrue(e.state.trap!!.success)
        assertEquals(850L, e.state.trap!!.actionAtMs)
    }

    @Test
    fun resultPoseSurvivesReloadAndNeitherInputNorReplayDuplicatesDamage() {
        for (type in listOf(1, 4)) {
            val e = GameEngine()
            e.enterFloor(1)
            e.state.mode = Mode.TRAP
            e.state.trap = trap(type, 1000)
            e.press(if (type == 1) 1 else 0)
            val hp = e.state.hp
            val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
            assertEquals(e.state.trap, restored.state.trap)
            restored.press(0)
            restored.press(1)
            repeat(4) { restored.tick(250) }
            assertEquals(hp, restored.state.hp, .0001)
            assertEquals(Mode.EXPLORE, restored.state.mode)
        }
    }
    @Test fun beamTimingScalesWithDepthAndCharmAndChecksBothBoundaries() {
        for (floor in listOf(1, 11, 30, 50)) for (charm in listOf(false, true)) {
            val s = State(floor = floor)
            if (charm) {
                val necklace = ItemFactory.create(s, Kind.NECKLACE).copy(charm = Charm.TRAP)
                s.inventory.add(necklace); Equipment.equip(s, necklace.id, Slot.NECK)
            }
            val template = Traps.create(s, 4).copy(phase = TrapPhase.ACTIVE)
            val first = template.beamCrouchStartMs
            for (time in listOf(0L, first - 1, first, template.limitMs - 1, template.limitMs)) {
                val t = template.copy(elapsedMs = time)
                Traps.tap(t, 1)
                assertEquals("floor=$floor charm=$charm time=$time", time >= first && time < t.limitMs, t.success)
                assertEquals(time, t.actionAtMs)
            }
        }
    }

    @Test fun earlyBeamFailureAppliesDamageOnlyOnceAndRestoresItsFailurePose() {
        val e = GameEngine(); e.enterFloor(1)
        e.state.mode = Mode.TRAP; e.state.trap = trap(4, 100)
        val before = e.state.hp
        e.press(1)
        assertFalse(e.state.trap!!.success); assertTrue(e.state.hp < before)
        assertTrue(e.state.message.contains("너무 일찍"))
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        val after = cold.state.hp
        cold.press(1); cold.press(0)
        repeat(4) { cold.tick(250) }
        assertEquals(after, cold.state.hp, .001)
        assertEquals(Mode.EXPLORE, cold.state.mode)
    }

}
