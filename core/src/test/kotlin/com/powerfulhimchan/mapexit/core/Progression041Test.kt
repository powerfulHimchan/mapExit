package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class Progression041Test {
    @Test fun clearedBossDevicesResumeOnTheNextFloorAfterReturnAndReload() {
        for (floor in listOf(10, 20, 30, 40)) {
            val e = GameEngine()
            e.enterFloor(floor)
            e.startBattle(true)
            e.state.enemy!!.hp = .01
            e.state.rng = 0
            e.attack(false)
            assertTrue(floor in e.state.devices)
            e.choose(e.choices().first { it.action == "battleContinue" })
            e.choose(e.choices().first { it.action == "return" })
            val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
            val device = cold.choices().first { it.action == "enter" && it.argument == floor + 1L }
            assertTrue(device.label.contains("${floor + 1}층"))
            cold.choose(device)
            assertEquals(floor + 1, cold.state.floor)
            assertEquals(Mode.EXPLORE, cold.state.mode)
        }
        val completed = GameEngine(State(bootstrapped = true, devices = mutableSetOf(50)))
        assertTrue(completed.choices().filter { it.action == "enter" }.all { it.argument in 1L..50L })
    }

    @Test fun donationLabelValidationChargeAndReloadUseTheSameDepthAmount() {
        for (floor in listOf(1, 10, 11, 20, 21, 30, 31, 40, 41, 50)) {
            val e = GameEngine()
            e.enterFloor(floor)
            val cost = 10 + MonsterCatalog.band(floor) * 5
            e.state.dungeon!!.here().apply { event = Event.NPC; npcRole = NpcRole.BEGGAR; resolved = false; npcGift = false }
            e.state.gold = cost - 1
            e.choose(Choice("방랑자", "encounter", Event.NPC.ordinal.toLong()))
            val action = e.choices().first { it.action == "npcDonate" }
            assertTrue(action.label.contains("${cost}G"))
            assertTrue(NpcInteractions.description(e.state).contains("${cost}G"))
            e.choose(action)
            assertEquals(cost - 1, e.state.gold)
            val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
            cold.state.gold = cost
            cold.choose(action)
            assertEquals(0, cold.state.gold)
            cold.choose(action)
            assertEquals(0, cold.state.gold)
        }
    }

    @Test fun everyTenFloorBoundaryAddsMonsterStrengthAndTrapDifficulty() {
        for (boundary in listOf(10, 20, 30, 40)) {
            val before = GameEngine().also { it.enterFloor(boundary); it.startBattle(false) }.state
            val after = GameEngine().also { it.enterFloor(boundary + 1); it.startBattle(false) }.state
            assertTrue(after.enemy!!.maxHp > before.enemy!!.maxHp * 1.07)
            assertTrue(after.enemy!!.attack > before.enemy!!.attack * 1.05)
            assertTrue(after.enemy!!.defense > before.enemy!!.defense)
            for (type in 0..4) {
                val old = Traps.create(before, type)
                val next = Traps.create(after, type)
                assertTrue(next.limitMs < old.limitMs - 10)
                if (type in listOf(0, 3)) assertEquals(old.pattern.size + 1, next.pattern.size)
                if (type == 2) assertEquals(old.required + 1, next.required)
            }
        }
    }

    @Test fun deepModifiersAreOnlyRolledAfterThirtyAndPersistThroughUpgrades() {
        for (floor in listOf(30, 31, 41, 50)) {
            val s = State(bootstrapped = true, floor = floor, rng = 14)
            val seen = mutableSetOf<PrefixIntensity>()
            repeat(500) {
                val g = ItemFactory.create(s, Kind.SWORD, floor, "longsword", "sharp")
                seen.add(g.prefixIntensity)
                if (g.prefixIntensity != PrefixIntensity.NONE) {
                    assertTrue(g.name.startsWith(g.prefixIntensity.label + " 예리한"))
                    assertEquals(g.flat.attack + 2 * g.prefixRank * g.prefixIntensity.multiplier, g.flatValue(Stat.ATTACK), .00001)
                }
                val upgraded = Forge.upgraded(g)
                assertEquals(g.prefixIntensity, upgraded.prefixIntensity)
                assertEquals(g.prefixRank + 1, upgraded.prefixRank)
                s.inventory.clear()
                s.inventory.add(g)
                assertEquals(g, SaveCodec.decode(SaveCodec.encode(s)).inventory.single())
            }
            assertEquals(if (floor <= 30) setOf(PrefixIntensity.NONE) else PrefixIntensity.entries.toSet(), seen)
        }
        val legacy = SaveCodec.decode("""{"inventory":[{"id":1,"kind":"SWORD","name":"예리한 장검","prefixId":"sharp","prefixRank":2}]}""")
        assertEquals(PrefixIntensity.NONE, legacy.inventory.single().prefixIntensity)
        assertEquals(4.0, legacy.inventory.single().flatValue(Stat.ATTACK), .00001)
    }

    @Test fun summariesIncludeCombinedPrefixBossAndPercentageStatsExactlyOnce() {
        val base = Gear(1, Kind.SWORD, "장검", flat = Stats.zero().copy(attack = 10.0, crit = .05),
            percent = Stats.zero().copy(attack = .10), affixes = listOf(WeaponAffix(Stat.ATTACK, 3.0)))
        val g = PrefixCatalog.apply(base, "sharp", PrefixIntensity.VERY)
        val summary = g.baseSummary()
        assertTrue(summary.contains("공격력 +17 · +10%"))
        assertTrue(summary.contains("치명타 확률 +5%p"))
        assertFalse(summary.contains("접두"))
        val rusty = PrefixCatalog.apply(base, "rusty", PrefixIntensity.VERY)
        assertEquals(11.0, rusty.flatValue(Stat.ATTACK), .00001)
        assertEquals(.45, rusty.flatValue(Stat.CRIT), .00001)
    }

    @Test fun herbalistOffersThePotionWithoutExplainingItsHealingEffect() {
        val e = GameEngine()
        e.enterFloor(5)
        e.state.dungeon!!.here().npcRole = NpcRole.HERBALIST
        val description = NpcInteractions.description(e.state)
        assertTrue(description.contains("회복약 1개"))
        assertFalse(description.contains("35%"))
        assertFalse(description.contains("최대 체력"))
    }
}
