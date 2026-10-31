package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class LootInventoryTest {
    private fun fixture(mode: Mode, equipment: Boolean): GameEngine {
        val e = GameEngine(); e.enterFloor(1)
        val g = ItemFactory.create(e.state, if (equipment) Kind.BODY else Kind.POTION)
        e.state.inventory.add(g)
        e.state.quickEquipIds = if (equipment) listOf(g.id) else emptyList()
        if (mode == Mode.VICTORY) {
            e.state.battleResult = BattleResult(Enemy("시험 몬스터", 1.0, 0.0, 1.0, 1.0, 1.0), 10, 10, listOf(g.name), Mode.FLOOR_CLEAR, listOf(g))
        } else e.state.chestLoot = ChestLootNotice(listOf(g), 10)
        e.state.mode = mode
        return e
    }

    @Test fun bagEntryRequiresEquipmentAndARealChestOrVictoryReceipt() {
        for (mode in listOf(Mode.EXPLORE, Mode.VICTORY)) for (gear in listOf(false, true)) {
            val e = fixture(mode, gear)
            assertEquals(gear, e.canOpenLootInventory())
            assertEquals(gear, e.openLootInventory())
            assertEquals(if (gear) Mode.MENU else mode, e.state.mode)
        }
        val e = GameEngine(); e.enterFloor(1)
        assertFalse(e.canOpenLootInventory()); assertFalse(e.openLootInventory())
        e.state.mode = Mode.COMBAT_ATTACK
        assertFalse(e.openLootInventory())
    }

    @Test fun bagEquipBackAndReloadPreserveTheRewardAndVictoryReturnTarget() {
        for (mode in listOf(Mode.EXPLORE, Mode.VICTORY)) {
            val e = fixture(mode, true)
            val gear = e.state.inventory.last()
            val inventorySize = e.state.inventory.size; val gold = e.state.gold
            assertTrue(e.openLootInventory())
            assertEquals("inventory", e.state.menu!!.type)
            assertTrue(Equipment.equip(e.state, gear.id, Slot.BODY))
            val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
            cold.navigateBack()
            assertEquals(mode, cold.state.mode)
            assertEquals(gear.id, cold.state.equipment[Slot.BODY])
            assertEquals(inventorySize, cold.state.inventory.size); assertEquals(gold, cold.state.gold)
            assertTrue(cold.canOpenLootInventory())
            if (mode == Mode.VICTORY) {
                cold.choose(cold.choices().first { it.action == "battleContinue" })
                assertEquals(Mode.FLOOR_CLEAR, cold.state.mode)
            } else {
                assertNotNull(cold.state.chestLoot)
                cold.navigateBack(); assertNull(cold.state.chestLoot)
                assertEquals(Mode.EXPLORE, cold.state.mode)
            }
        }
    }

    @Test fun occupiedGearComparisonsUseBothRingSlotsAndDisplacedTwoHandedLoadouts() {
        val e = GameEngine(); val s = e.state
        val hammer = ItemFactory.create(s, Kind.HAMMER)
        s.inventory.add(hammer); Equipment.equip(s, hammer.id, Slot.RIGHT)
        val shield = ItemFactory.create(s, Kind.SHIELD)
        val saved = SaveCodec.encode(s)
        Equipment.compare(s, shield).forEach { c ->
            assertEquals(hammer.id, c.current!!.id)
            assertEquals(listOf(hammer.id), c.removed.map { it.id })
        }
        assertEquals(saved, SaveCodec.encode(s))
        val rings = List(2) { ItemFactory.create(s, Kind.RING).also { s.inventory.add(it) } }
        Equipment.equip(s, rings[0].id, Slot.RING1); Equipment.equip(s, rings[1].id, Slot.RING2)
        assertEquals(rings, Equipment.compare(s, ItemFactory.create(s, Kind.RING)).map { it.current })
    }
}
