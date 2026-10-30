package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class GreaterPotionTest {
    @Test fun combatSelectsEachGradeWithoutConsumingTheOtherAndCostsAnAttackTurn() {
        for (grade in PotionGrade.entries) {
            val e = GameEngine(); e.enterFloor(1); e.startBattle(false)
            e.state.inventory.removeAll { it.kind == Kind.POTION }
            e.state.inventory.addAll(PotionGrade.entries.map { ItemFactory.potion(e.state, it) })
            e.state.hp = 10.0
            val before = e.stats().hp
            val action = if (grade == PotionGrade.BASIC) "potion" else "strongPotion"
            e.choose(Choice("use", action))
            assertEquals(10.0 + before * grade.healRatio, e.state.hp, .0001)
            assertEquals(Mode.COMBAT_DEFEND, e.state.mode)
            assertEquals(listOf(PotionGrade.entries.first { it != grade }), e.state.inventory.filter { it.kind == Kind.POTION }.map { it.potionGrade })
            val hp = e.state.hp
            e.choose(Choice("stale", action)); assertEquals(hp, e.state.hp, .0)
        }
    }

    @Test fun missingGradeDoesNotConsumeAnotherPotionOrATurn() {
        val e = GameEngine(); e.enterFloor(1); e.startBattle(false); e.state.hp = 10.0
        val count = e.state.inventory.size
        e.choose(Choice("use", "strongPotion"))
        assertEquals(count, e.state.inventory.size); assertEquals(10.0, e.state.hp, .0)
        assertEquals(Mode.COMBAT_ATTACK, e.state.mode)
    }

    @Test fun bagUsesExactItemAndCapsHealingAtEquippedMaximum() {
        val e = GameEngine(); val ring = Achievements.specialRing(e.state.nextId++)
        e.state.inventory.add(ring); Equipment.equip(e.state, ring.id, Slot.RING1)
        val potion = ItemFactory.potion(e.state, PotionGrade.GREATER); e.state.inventory.add(potion)
        e.state.hp = e.stats().hp - 1
        e.choose(Choice("bag", "inventory")); e.choose(Choice("item", "item", potion.id))
        e.choose(Choice("use", "useItem", potion.id))
        assertEquals(e.stats().hp, e.state.hp, .0)
        assertFalse(e.state.inventory.any { it.id == potion.id })
        assertEquals(2, e.state.inventory.count { it.kind == Kind.POTION })
    }

    @Test fun greaterPotionIsDistinctAndNormalNpcsKeepTheirBasicPotion() {
        val s = State()
        val basic = ItemFactory.create(s, Kind.POTION, 50)
        val greater = ItemFactory.potion(s, PotionGrade.GREATER)
        assertEquals(PotionGrade.BASIC, basic.potionGrade)
        assertEquals("최대 HP의 35% 회복", basic.summary())
        assertEquals("상급 회복약", greater.name); assertEquals("최대 HP의 70% 회복", greater.summary())
        assertNotEquals(GearAppearance.base(basic), GearAppearance.base(greater))
        assertEquals(75, greater.price)
    }

    @Test fun bulkPurchaseKeepsGradeDiscountAndUniqueIdsAfterRestore() {
        val e = GameEngine(State(gold = 1000))
        val necklace = Gear(e.state.nextId++, Kind.NECKLACE, "discount", charm = Charm.MERCHANT)
        e.state.inventory.add(necklace); Equipment.equip(e.state, necklace.id, Slot.NECK)
        val potion = e.state.shop.first { it.potionGrade == PotionGrade.GREATER }
        assertEquals(60, Equipment.buyPrice(e.state, potion))
        e.choose(Choice("shop", "shop")); e.choose(Choice("buy", "buy", potion.id))
        assertTrue(e.purchase(potion.id, 3)); assertEquals(820, e.state.gold)
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertEquals(3, cold.state.inventory.count { it.potionGrade == PotionGrade.GREATER })
        assertEquals(cold.state.inventory.size, cold.state.inventory.map { it.id }.distinct().size)
        assertTrue(cold.state.shop.any { it.id == potion.id })
    }

    @Test fun oldPotionDefaultsToBasicAndShopsStockBothGradesInEveryLocation() {
        val old = SaveCodec.decode("""{"bootstrapped":true,"nextId":91,"inventory":[{"id":90,"kind":"POTION","name":"회복약"}]}""")
        assertEquals(PotionGrade.BASIC, old.inventory.single().potionGrade)
        val e = GameEngine(old)
        assertTrue(e.state.shop.any { it.potionGrade == PotionGrade.GREATER })
        e.enterFloor(15)
        assertTrue(PotionGrade.entries.all { grade -> e.state.shop.any { it.kind == Kind.POTION && it.potionGrade == grade } })
        e.newCycle(ItemFactory.create(e.state, Kind.SWORD).also { e.state.inventory.add(it) }.id)
        assertTrue(PotionGrade.entries.all { grade -> e.state.shop.any { it.kind == Kind.POTION && it.potionGrade == grade } })
    }

    @Test fun randomLootCanIncludeGreaterPotionsOnlyFromFloorEleven() {
        for (floor in listOf(1, 10, 11, 50)) {
            val s = State(rng = 1234)
            val drops = List(2000) { ItemFactory.create(s, floor = floor) }
            assertEquals(floor >= 11, drops.any { it.potionGrade == PotionGrade.GREATER })
        }
    }
}
