package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class WeaponVarietyTest {
    @Test
    fun twentyVariantsHaveDistinctStatsAndRetainTheirWeaponFamilyRules() {
        assertEquals(20, WeaponCatalog.all.size)
        assertEquals(20, WeaponCatalog.all.map { it.id }.distinct().size)
        for (kind in listOf(Kind.SWORD, Kind.SPEAR, Kind.HAMMER, Kind.CLAW, Kind.WHIP)) {
            val variants = WeaponCatalog.of(kind)
            assertEquals(4, variants.size)
            assertEquals(4, variants.map { it.stats(1) }.distinct().size)
            for (v in variants) {
                val f = v.stats(1)
                assertTrue(f.attack > 0)
                when (kind) {
                    Kind.SWORD -> assertTrue(f.critDamage > 0)
                    Kind.SPEAR -> assertTrue(f.defense < 0 && f.crit < 0 && f.critDamage > 0)
                    Kind.HAMMER ->
                        assertTrue(f.defense < 0 && f.agility < 0 && f.crit < 0 && f.critDamage > 0)
                    Kind.CLAW -> assertTrue(f.agility > 0)
                    Kind.WHIP -> assertTrue(f.agility > 0 && f.crit < 0 && f.critDamage > 0)
                    else -> error("not a weapon")
                }
            }
        }
    }

    @Test
    fun allVariantsAreAvailableThroughNormalGenerationAndReplayDeterministically() {
        for (kind in listOf(Kind.SWORD, Kind.SPEAR, Kind.HAMMER, Kind.CLAW, Kind.WHIP)) {
            val a = State(bootstrapped = true, rng = 321L)
            val b = a.copy()
            val found = mutableSetOf<String?>()
            repeat(120) {
                val x = ItemFactory.create(a, kind, 15)
                assertEquals(x, ItemFactory.create(b, kind, 15))
                assertTrue(x.affixes.isEmpty())
                found.add(x.variantId)
            }
            assertEquals(WeaponCatalog.of(kind).map { it.id }.toSet(), found)
        }
    }

    @Test
    fun bossWeaponsAlwaysHavePositiveDistinctOptionsScaledByBossDepth() {
        for ((floor, count) in listOf(10 to 2, 30 to 3, 50 to 4)) {
            for (seed in 1L..40L) {
                val g = ItemFactory.bossWeapon(State(bootstrapped = true, rng = seed), floor)
                assertTrue(g.weapon)
                assertEquals(floor, g.bossOriginFloor)
                assertEquals(count, g.affixes.size)
                assertEquals(count, g.affixes.map { it.stat }.distinct().size)
                assertTrue(g.affixes.all { it.amount > 0 })
                val variant = WeaponCatalog.all.first { it.id == g.variantId }
                assertEquals(variant.stats(g.tier), g.flat)
                assertTrue(g.summary().contains("보스 전리품"))
                assertTrue(g.summary().contains("추가 옵션:"))
            }
        }
    }

    @Test
    fun additiveOptionsApplyOnceToTwoHandedGearBeforePercentageBonusesAndInPreview() {
        val s = State(bootstrapped = true)
        val g =
            ItemFactory.create(s, Kind.SPEAR, 10, "longspear")
                .copy(
                    bossOriginFloor = 10,
                    affixes = listOf(WeaponAffix(Stat.ATTACK, 10.0), WeaponAffix(Stat.LUCK, 7.0)),
                )
        val ring = Gear(s.nextId++, Kind.RING, "공격 반지", percent = Stats.zero().copy(attack = .2))
        s.inventory.add(ring)
        Equipment.equip(s, ring.id, Slot.RING1)
        val preview = Equipment.compare(s, g).first().after
        s.inventory.add(g)
        Equipment.equip(s, g.id, Slot.LEFT)
        assertEquals(g.id, s.equipment[Slot.RIGHT])
        assertEquals((100 + g.flat.attack + 10) * 1.2, Equipment.stats(s).attack, .0001)
        assertEquals(7.0, Equipment.stats(s).luck, .0001)
        assertEquals(preview, Equipment.stats(s))
        assertEquals(GameBalance.weapon(Kind.SPEAR, g.tier.toDouble()), g.flat)
    }

    @Test
    fun bossRewardPersistsWithoutRerollOrDuplicateGrantAfterReload() {
        val e = GameEngine()
        e.enterFloor(10)
        e.startBattle(true)
        e.state.enemy!!.hp = 1.0
        e.state.rng = 0L
        val before = e.state.inventory.size
        e.attack(false)
        assertEquals(Mode.VICTORY, e.state.mode)
        assertEquals(before + 1, e.state.inventory.size)
        val g = e.state.battleResult!!.rewards.single()
        assertEquals(g, e.state.inventory.last())
        assertEquals(2, g.affixes.size)
        val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertEquals(g, restored.state.battleResult!!.rewards.single())
        restored.attack(false)
        restored.press(1)
        assertEquals(before + 1, restored.state.inventory.size)
        assertEquals(g, restored.state.inventory.last())
    }

    @Test
    fun oldGearAndVictorySavesLoadWithoutConvertingOrRerollingTheirStats() {
        val e = GameEngine()
        e.enterFloor(10)
        e.startBattle(true)
        e.state.enemy!!.hp = 1.0
        e.state.rng = 0L
        e.attack(false)
        val root = Json.parseToJsonElement(SaveCodec.encode(e.state)).jsonObject
        val inventory =
            JsonArray(
                root.getValue("inventory").jsonArray.map { value ->
                    JsonObject(
                        value.jsonObject.filterKeys {
                            it !in setOf("variantId", "bossOriginFloor", "affixes")
                        }
                    )
                }
            )
        val result =
            JsonObject(root.getValue("battleResult").jsonObject.filterKeys { it != "rewards" })
        val old = JsonObject(root + mapOf("inventory" to inventory, "battleResult" to result))
        val restored = GameEngine(SaveCodec.decode(old.toString()))
        assertTrue(restored.state.inventory.all { it.variantId == null && it.affixes.isEmpty() })
        assertEquals(e.state.inventory.map { it.flat }, restored.state.inventory.map { it.flat })
        assertEquals(e.state.battleResult!!.items, restored.state.battleResult!!.items)
        assertTrue(restored.state.battleResult!!.rewards.isEmpty())
    }
}
