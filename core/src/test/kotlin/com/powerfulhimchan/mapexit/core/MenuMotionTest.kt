package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class MenuMotionTest {
    private fun choose(e: GameEngine, action: String, arg: Long? = null) =
        e.choose(e.choices().first { it.action == action && (arg == null || it.argument == arg) })

    @Test
    fun equipmentAllowsConsecutiveEquipsAndUnequipWithoutLeavingSlots() {
        val e = GameEngine()
        val helmet = ItemFactory.create(e.state, Kind.HEAD)
        e.state.inventory.add(helmet)
        choose(e, "equipment")
        choose(e, "slotItem", Slot.RIGHT.ordinal.toLong())
        choose(e, "equip", Slot.LEFT.ordinal.toLong())
        assertEquals(Mode.MENU, e.state.mode)
        assertEquals("equipment", e.state.menu!!.type)
        assertTrue(e.state.menuStack.isEmpty())
        choose(e, "slotItem", Slot.HEAD.ordinal.toLong())
        choose(e, "item", helmet.id)
        choose(e, "equip", Slot.HEAD.ordinal.toLong())
        assertEquals("equipment", e.state.menu!!.type)
        assertEquals(helmet.id, e.state.equipment[Slot.HEAD])
        choose(e, "slotItem", Slot.HEAD.ordinal.toLong())
        choose(e, "unequip")
        assertEquals("equipment", e.state.menu!!.type)
        assertNull(e.state.equipment[Slot.HEAD])
        e.navigateBack()
        assertEquals(Mode.SURFACE, e.state.mode)
    }

    @Test
    fun dungeonEquipmentReturnAndReloadKeepTheCorrectBackDestination() {
        val e = GameEngine()
        e.enterFloor(3)
        choose(e, "equipment")
        choose(e, "slotItem", Slot.RIGHT.ordinal.toLong())
        choose(e, "equip", Slot.LEFT.ordinal.toLong())
        val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertEquals("equipment", restored.state.menu!!.type)
        restored.navigateBack()
        assertEquals(Mode.EXPLORE, restored.state.mode)
        assertEquals(3, restored.state.floor)
    }

    @Test
    fun equippingFromBagKeepsTheBagAvailable() {
        val e = GameEngine()
        choose(e, "inventory")
        choose(e, "item", e.state.equipment[Slot.RIGHT])
        choose(e, "equip", Slot.LEFT.ordinal.toLong())
        assertEquals("inventory", e.state.menu!!.type)
        e.navigateBack()
        assertEquals(Mode.SURFACE, e.state.mode)
    }

    @Test
    fun weaponMotionHasWindupContactRecoveryAndDifferentWeaponPaths() {
        val kinds = listOf(Kind.SWORD, Kind.SPEAR, Kind.HAMMER, Kind.CLAW, Kind.WHIP)
        val winds = kinds.map { BattleAnimation.pose(it, 130).angle }.toSet()
        assertTrue(winds.size >= 4)
        for (kind in kinds) {
            val windup = BattleAnimation.pose(kind, 120)
            val contact = BattleAnimation.pose(kind, BattleAnimation.HIT_MS)
            val recovery = BattleAnimation.pose(kind, 700)
            assertTrue(contact.advance > windup.advance)
            assertTrue(contact.advance > recovery.advance)
            assertEquals(1f, contact.impact, .001f)
            assertEquals(0f, BattleAnimation.pose(kind, BattleAnimation.DURATION_MS).advance, .001f)
            for (ms in 0L..BattleAnimation.DURATION_MS step 10) {
                val p = BattleAnimation.pose(kind, ms)
                assertTrue(p.angle.isFinite() && p.advance.isFinite() && p.lift.isFinite())
            }
        }
        assertTrue(
            BattleAnimation.pose(Kind.SPEAR, 320).reach >
                BattleAnimation.pose(Kind.SWORD, 320).reach
        )
        assertTrue(BattleAnimation.pose(Kind.HAMMER, 320).lift > 0)
    }

    @Test
    fun strongAttackIsLargerAndAnimationSamplingCannotModifyTheRun() {
        val e = GameEngine()
        e.enterFloor(1)
        val saved = SaveCodec.encode(e.state)
        assertTrue(
            BattleAnimation.pose(Kind.SWORD, 320, true).advance >
                BattleAnimation.pose(Kind.SWORD, 320).advance
        )
        repeat(100) { BattleAnimation.pose(Kind.CLAW, it * 8L) }
        assertEquals(saved, SaveCodec.encode(e.state))
    }
}
