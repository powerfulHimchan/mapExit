package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class InventoryStacksTest {
    private fun choose(e: GameEngine, action: String, id: Long? = null) =
        e.choose(e.choices().first { it.action == action && (id == null || it.argument == id) })

    @Test fun identicalItemsShareOneDisplayGroupWithoutChangingTheirSavedIds() {
        val item = Gear(1, Kind.BODY, "철 갑옷", flat = Stats.zero().copy(defense = 10.0))
        val copies = (1L..3L).map { item.copy(id = it) }
        val s = State(bootstrapped = true, inventory = copies.toMutableList())
        val before = SaveCodec.encode(s)
        val group = InventoryStacks.groups(s.inventory).single()
        assertEquals(3, group.count); assertEquals("철 갑옷 ×3", group.displayName)
        assertEquals(listOf(1L, 2L, 3L), group.items.map { it.id })
        assertEquals(before, SaveCodec.encode(s))
    }

    @Test fun sameNameWithDifferentStatsGradeVariantOrInheritanceIsNotMerged() {
        val item = Gear(1, Kind.SWORD, "같은 이름", flat = Stats.zero().copy(attack = 10.0), variantId = "longsword")
        val variants = listOf(item, item.copy(id = 2, flat = item.flat.copy(attack = 10.01)),
            item.copy(id = 3, variantId = "rapier"), item.copy(id = 4, inherited = true), item.copy(id = 5, tier = 2))
        assertEquals(5, InventoryStacks.groups(variants).size)
        val potion = Gear(6, Kind.POTION, "회복약")
        // Old item generation assigned tiers to potions without changing their effect or icon.
        assertEquals(2, InventoryStacks.groups(listOf(potion, potion.copy(id = 8, tier = 7))).single().count)
        assertEquals(2, InventoryStacks.groups(listOf(potion, potion.copy(id = 7, potionGrade = PotionGrade.GREATER))).size)
        assertTrue(InventoryStacks.inventoryLabel(InventoryStacks.groups(listOf(variants[3])).single()).startsWith("◆"))
    }

    @Test fun identicalAffixesGroupRegardlessOfTheirDisplayOrder() {
        val a = WeaponAffix(Stat.ATTACK, 5.0); val b = WeaponAffix(Stat.HP, 20.0)
        val g = Gear(1, Kind.SWORD, "보스 검", affixes = listOf(a,b), bossOriginFloor = 10)
        assertEquals(2, InventoryStacks.groups(listOf(g, g.copy(id = 2, affixes = listOf(b,a)))).single().count)
        assertEquals(2, InventoryStacks.groups(listOf(g, g.copy(id = 2, affixes = listOf(a.copy(amount = 6.0),b)))).size)
    }

    @Test fun salesPreferUnusedCopiesAndStaleIdsCannotSellAnotherCopy() {
        val g = Gear(1, Kind.BODY, "갑옷", price = 90)
        val s = State(bootstrapped = true, inventory = mutableListOf(g, g.copy(id = 2)), equipment = mutableMapOf(Slot.BODY to 1L))
        val e = GameEngine(s); choose(e, "shop"); choose(e, "sellMenu")
        val choice = e.choices().single { it.action == "sell" }
        assertEquals(2L, choice.argument); assertTrue(choice.label.contains("×2"))
        e.choose(choice)
        assertEquals(30, s.gold); assertEquals(listOf(1L), s.inventory.map { it.id }); assertEquals(1L, s.equipment[Slot.BODY])
        e.choose(choice); assertEquals(30, s.gold); assertEquals(1, s.inventory.size)
        choose(e, "sell", 1); assertEquals(60, s.gold); assertTrue(s.equipment.isEmpty()); assertTrue(s.inventory.isEmpty())
    }

    @Test fun consumingOnePotionUpdatesOnlyItsGroupAfterSaveAndReopen() {
        val p = Gear(1, Kind.POTION, "회복약")
        val s = State(bootstrapped = true, hp = 10.0, inventory = mutableListOf(p, p.copy(id = 2), p.copy(id = 3),
            Gear(4, Kind.POTION, "상급 회복약", potionGrade = PotionGrade.GREATER)))
        val e = GameEngine(s); choose(e, "inventory")
        assertEquals(2, e.choices().count { it.action == "item" })
        choose(e, "item", 1); choose(e, "useItem", 1)
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(s))); choose(cold, "inventory")
        assertEquals(2, cold.choices().count { it.action == "item" })
        assertTrue(cold.choices().any { it.label == "회복약 ×2" })
        assertEquals(1, cold.state.inventory.count { it.potionGrade == PotionGrade.GREATER })
        assertEquals(listOf(2L,3L,4L), cold.state.inventory.map { it.id })
    }

    @Test fun twoIdenticalRingsCanStillBeEquippedIntoDifferentSlotsFromTheBag() {
        val g = Gear(1, Kind.RING, "동일 반지")
        val s = State(bootstrapped = true, inventory = mutableListOf(g, g.copy(id = 2)))
        val e = GameEngine(s); choose(e, "inventory")
        choose(e, "item", 1); choose(e, "equip", Slot.RING1.ordinal.toLong())
        assertEquals("inventory", s.menu!!.type)
        val next = e.choices().single { it.action == "item" }
        assertEquals(2L, next.argument); assertTrue(next.label.contains("×2")); assertTrue(next.label.contains("착용중 1개"))
        e.choose(next); choose(e, "equip", Slot.RING2.ordinal.toLong())
        assertEquals(1L, s.equipment[Slot.RING1]); assertEquals(2L, s.equipment[Slot.RING2])
        assertEquals(2, InventoryStacks.groups(s.inventory, s.equipment.values).single().equippedCount)
    }
}
