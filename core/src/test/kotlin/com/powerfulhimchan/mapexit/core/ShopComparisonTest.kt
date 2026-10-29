package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class ShopComparisonTest {
    @Test
    fun shopPreviewMatchesRealEquipWithoutChangingWalletInventoryOrSave() {
        val e = GameEngine(State(gold = 1000))
        for (kind in Kind.entries.filter { it != Kind.POTION }) {
            val candidate = ItemFactory.create(e.state, kind)
            val saved = SaveCodec.encode(e.state)
            val previews = Equipment.compare(e.state, candidate)
            assertEquals(Equipment.allowedSlots(candidate), previews.map { it.slot })
            assertEquals(saved, SaveCodec.encode(e.state))
            for (preview in previews) {
                val actual = SaveCodec.decode(saved)
                actual.inventory.add(candidate)
                assertTrue(Equipment.equip(actual, candidate.id, preview.slot))
                assertEquals(Equipment.stats(actual), preview.after)
                assertEquals(Equipment.stats(e.state), preview.before)
                assertEquals(
                    Equipment.equipped(e.state).filter { it.id !in actual.equipment.values },
                    preview.removed,
                )
            }
        }
    }

    @Test
    fun twoHandedShopWeaponShowsBothDisplacedPieces() {
        val s = State()
        val sword = ItemFactory.create(s, Kind.SWORD)
        val shield = ItemFactory.create(s, Kind.SHIELD)
        s.inventory.addAll(listOf(sword, shield))
        Equipment.equip(s, sword.id, Slot.RIGHT)
        Equipment.equip(s, shield.id, Slot.LEFT)
        val hammer = ItemFactory.create(s, Kind.HAMMER)
        Equipment.compare(s, hammer).forEach {
            assertEquals(setOf(sword.id, shield.id), it.removed.map { gear -> gear.id }.toSet())
            assertNotNull(it.current)
        }
    }

    @Test
    fun ringsCompareEachSlotAndEmptyArmorIsExplicit() {
        val s = State()
        val first = ItemFactory.create(s, Kind.RING)
        val second = ItemFactory.create(s, Kind.RING)
        s.inventory.addAll(listOf(first, second))
        Equipment.equip(s, first.id, Slot.RING1)
        Equipment.equip(s, second.id, Slot.RING2)
        val ring = ItemFactory.create(s, Kind.RING)
        val previews = Equipment.compare(s, ring)
        assertEquals(listOf(first, second), previews.map { it.current })
        assertTrue(previews.all { it.removed.size == 1 })
        assertNull(Equipment.compare(s, ItemFactory.create(s, Kind.HEAD)).single().current)
        assertTrue(Equipment.compare(s, ItemFactory.create(s, Kind.POTION)).isEmpty())
    }

    @Test
    fun recordsOnlyExposeBackAndBattleEntryHasNoCameraDescription() {
        val e = GameEngine()
        e.choose(e.choices().first { it.action == "help" })
        assertEquals(listOf("back"), e.choices().map { it.action })
        e.navigateBack()
        e.enterFloor(1)
        e.startBattle(false)
        assertFalse(e.state.message.contains("3인칭 전투 시작"))
    }

    @Test
    fun fourActionsHaveDistinctPosesAndAllRecover() {
        for (kind in listOf(Kind.SWORD, Kind.SPEAR, Kind.HAMMER, Kind.CLAW, Kind.WHIP)) {
            val normal = BattleAnimation.pose(kind, 180)
            val strong = BattleAnimation.pose(kind, 180, true)
            assertTrue(strong.advance < normal.advance)
            assertTrue(strong.lean < normal.lean)
            assertTrue(
                BattleAnimation.pose(kind, 320, true).advance >
                    BattleAnimation.pose(kind, 320).advance
            )
            assertEquals(1f, BattleAnimation.pose(kind, 320, true).impact, .001f)
        }
        val guard = BattleAnimation.defense("guard", 320)
        val dodge = BattleAnimation.defense("dodge", 320)
        assertTrue(guard.guard > 0)
        assertEquals(0f, dodge.guard, .001f)
        assertTrue(dodge.retreat > guard.retreat)
        assertTrue(dodge.crouch > guard.crouch)
        for (action in listOf("guard", "dodge")) assertEquals(
            0f,
            BattleAnimation.defense(action, 760).retreat,
            .001f,
        )
    }
}
