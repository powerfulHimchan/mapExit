package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RunResultTest {
    private fun choose(e: GameEngine, action: String) =
        e.choose(e.choices().first { it.action == action })

    @Test
    fun deathSnapshotsProgressAndLossBeforeResetAndSurvivesReload() {
        val e = GameEngine(State(gold = 90, bank = 700, level = 7, points = 2))
        e.enterFloor(8)
        e.state.devices.add(10)
        e.state.runKills = 12
        e.state.runChests = 3
        e.state.runTraps = 2
        repeat(8) { e.tick(250) }
        val itemCount = e.state.inventory.size
        e.die()
        val r = e.state.runResult!!
        assertEquals(Mode.RUN_RESULT, e.state.mode)
        assertFalse(r.retreated)
        assertEquals(8, r.floor)
        assertEquals(8, r.deepestFloor)
        assertEquals(12, r.kills)
        assertEquals(3, r.chests)
        assertEquals(2, r.traps)
        assertEquals(2000L, r.elapsedMs)
        assertEquals(90, r.lostGold)
        assertEquals(itemCount, r.lostItems)
        assertTrue(e.state.inventory.isEmpty())
        assertTrue(e.state.equipment.isEmpty())
        assertEquals(0, e.state.gold)
        assertEquals(700, e.state.bank)
        assertEquals(7, e.state.level)
        assertEquals(2, e.state.points)
        assertTrue(10 in e.state.devices)
        assertNull(e.state.dungeon)
        val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        repeat(40) {
            restored.tick(250)
            restored.press(0)
            restored.die()
        }
        assertEquals(r, restored.state.runResult)
        assertEquals(Mode.RUN_RESULT, restored.state.mode)
        choose(restored, "runContinue")
        assertEquals(Mode.SURFACE, restored.state.mode)
        assertNull(restored.state.runResult)
        assertEquals(700, restored.state.bank)
        restored.enterFloor(10)
        assertEquals(10, restored.state.runDeepestFloor)
        assertEquals(0, restored.state.runKills)
        assertEquals(0L, restored.state.runElapsedMs)
    }

    @Test
    fun defeatCountsExactlyOnceAcrossFloorsAndSaves() {
        val e = GameEngine()
        e.enterFloor(1)
        e.startBattle(false)
        e.state.enemy!!.hp = 1.0
        e.state.rng = 0L
        e.attack(false)
        assertEquals(1, e.state.runKills)
        e.attack(false)
        assertEquals(1, e.state.runKills)
        choose(e, "battleContinue")
        e.enterFloor(2)
        assertEquals(1, e.state.runKills)
        val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertEquals(1, restored.state.runKills)
        choose(restored, "retreat")
        assertEquals(2, restored.state.runResult!!.floor)
        assertEquals(1, restored.state.runResult!!.kills)
        assertTrue(restored.state.runResult!!.retreated)
    }

    @Test
    fun openedChestsAndSuccessfulTrapsAreCountedOnlyOnce() {
        val e = GameEngine()
        e.enterFloor(1)
        e.state.dungeon!!.here().event = Event.CHEST
        e.state.dungeon!!.here().resolved = false
        choose(e, "encounter")
        choose(e, "chest")
        assertEquals(1, e.state.runChests)
        e.choose(Choice("stale chest", "chest"))
        assertEquals(1, e.state.runChests)
        e.state.enemy = null
        e.state.dungeon!!.here().event = Event.TRAP
        e.state.mode = Mode.TRAP
        e.state.trap = Traps.create(e.state, 0).copy(phase = TrapPhase.RESULT, success = true)
        repeat(3) { e.tick(250) }
        assertEquals(1, e.state.runTraps)
        e.tick(250)
        assertEquals(Mode.EXPLORE, e.state.mode)
        assertEquals(1, e.state.runTraps)
        e.state.mode = Mode.TRAP
        e.state.trap = Traps.create(e.state, 0).copy(phase = TrapPhase.RESULT, success = false)
        e.tick(250)
        assertEquals(1, e.state.runTraps)
    }

    @Test
    fun retreatHasSameLossAsDeathAndStaleActionsCannotSkipResults() {
        val dead = GameEngine(State(gold = 999, bank = 200))
        dead.enterFloor(3)
        val retreat = GameEngine(SaveCodec.decode(SaveCodec.encode(dead.state)))
        dead.die()
        choose(retreat, "retreat")
        assertEquals(dead.state.runResult!!.copy(retreated = true), retreat.state.runResult)
        assertEquals(dead.state.inventory, retreat.state.inventory)
        assertEquals(dead.state.equipment, retreat.state.equipment)
        assertEquals(dead.state.hp, retreat.state.hp, .001)
        assertEquals(dead.state.gold, retreat.state.gold)
        assertEquals(200, retreat.state.bank)
        retreat.choose(Choice("stale movement", "move", 0))
        retreat.choose(Choice("stale retreat", "retreat"))
        assertEquals(Mode.RUN_RESULT, retreat.state.mode)
        assertEquals(999, retreat.state.runResult!!.lostGold)
    }

    @Test
    fun retreatCannotInterruptCombatTrapOrSurfaceAndSafeExitStillKeepsItems() {
        val e = GameEngine(State(gold = 100))
        e.choose(Choice("retreat", "retreat"))
        assertEquals(Mode.SURFACE, e.state.mode)
        e.enterFloor(1)
        e.startBattle(false)
        e.choose(Choice("retreat", "retreat"))
        assertEquals(Mode.COMBAT_ATTACK, e.state.mode)
        e.state.trap = Traps.create(e.state, 1)
        e.state.mode = Mode.TRAP
        e.choose(Choice("retreat", "retreat"))
        assertEquals(Mode.TRAP, e.state.mode)
        e.state.mode = Mode.FLOOR_CLEAR
        val items = e.state.inventory.toList()
        choose(e, "return")
        assertEquals(Mode.SURFACE, e.state.mode)
        assertEquals(items, e.state.inventory)
        assertEquals(100, e.state.gold)
        assertNull(e.state.runResult)
    }

    @Test
    fun oldDungeonSaveGetsCurrentDepthWithoutInventingOldKills() {
        val e = GameEngine()
        e.enterFloor(17)
        val json = Json.parseToJsonElement(SaveCodec.encode(e.state)).jsonObject
        val old = JsonObject(json.filterKeys { !it.startsWith("run") }).toString()
        val restored = GameEngine(SaveCodec.decode(old))
        assertEquals(17, restored.state.runDeepestFloor)
        assertEquals(0, restored.state.runKills)
        restored.die()
        assertEquals(17, restored.state.runResult!!.deepestFloor)
    }

    @Test
    fun emptyEquipmentSlotOpensBagAndTwoHandedOccupiesBothHands() {
        val e = GameEngine()
        choose(e, "equipment")
        e.choose(
            e.choices().first {
                it.action == "slotItem" && it.argument == Slot.HEAD.ordinal.toLong()
            }
        )
        assertEquals("inventory", e.state.menu!!.type)
        e.navigateBack()
        assertEquals("equipment", e.state.menu!!.type)
        val spear = ItemFactory.create(e.state, Kind.SPEAR)
        e.state.inventory.add(spear)
        Equipment.equip(e.state, spear.id, Slot.RIGHT)
        assertEquals(spear.id, e.state.equipment[Slot.RIGHT])
        assertEquals(spear.id, e.state.equipment[Slot.LEFT])
        assertEquals(1, Equipment.equipped(e.state).size)
    }
}
