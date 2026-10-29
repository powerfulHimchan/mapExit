package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LuckDefenseTest {
    private fun battle(seed: Long, luck: Double = 100.0, strong: Boolean = false): GameEngine {
        val e =
            GameEngine(
                State(
                    bootstrapped = true,
                    rng = seed,
                    base = Stats(luck = luck),
                    mode = Mode.COMBAT_ATTACK,
                    enemy = Enemy("연습 적", 100000.0, 100000.0, 100.0, 0.0, 0.0),
                )
            )
        e.attack(strong)
        return e
    }

    @Test
    fun luckChangesActivationChanceButNeverPermanentlyAddsAttackOrCriticalStats() {
        for (luck in listOf(0.0, 10.0, 100.0, 10000.0)) {
            val e = GameEngine(State(bootstrapped = true, base = Stats(luck = luck), points = 1))
            assertEquals(100.0, e.stats().attack, .0001)
            assertEquals(.2, e.stats().crit, .0001)
            val preview = Growth.preview(e.state, Stat.LUCK)
            assertEquals(e.stats().attack, preview.attack, .0001)
            assertEquals(e.stats().crit, preview.crit, .0001)
        }
        assertEquals(0.0, Luck.activationChance(-20.0), .0001)
        assertEquals(.02, Luck.activationChance(10.0), .0001)
        assertEquals(.20, Luck.activationChance(100.0), .0001)
        assertEquals(.35, Luck.activationChance(1e9), .0001)
    }

    @Test
    fun bothBonusesCanActivateIndependentlyOrFailAndTheirDamageUsesOnlyThatHit() {
        val cases =
            listOf(
                0L to (false to false),
                10L to (true to false),
                11L to (true to true),
                15L to (false to true),
            )
        val expected = mapOf(0L to 150.0, 10L to 120.0, 11L to 120.0, 15L to 150.0)
        for ((seed, flags) in cases) for (strong in listOf(false, true)) {
            val e = battle(seed, strong = strong)
            val impact = e.state.lastImpact!!
            assertEquals(flags.first, impact.luckyAttack)
            assertEquals(flags.second, impact.luckyCritical)
            assertEquals(
                expected.getValue(seed) * if (strong) 1.4 else 1.0,
                100000 - e.state.enemy!!.hp,
                .0001,
            )
            assertEquals(100.0, e.stats().attack, .0001)
            assertEquals(.2, e.stats().crit, .0001)
            val rng = e.state.rng
            e.attack(strong)
            assertEquals(rng, e.state.rng)
        }
        assertFalse(battle(11).state.lastImpact!!.critical)
        assertTrue(battle(15).state.lastImpact!!.critical)
        assertFalse(battle(15, luck = 0.0).state.lastImpact!!.critical)
    }

    @Test
    fun zeroLuckAndMissesCannotActivateABonusAndCriticalBonusHonorsTheCap() {
        for (seed in 0L..200L) {
            val e = battle(seed, luck = 0.0)
            assertFalse(e.state.lastImpact!!.luckyAttack || e.state.lastImpact!!.luckyCritical)
        }
        val missed =
            (0L..200L)
                .map { battle(it, luck = 1e6) }
                .first { it.state.lastImpact!!.outcome == ImpactOutcome.MISS }
        assertFalse(
            missed.state.lastImpact!!.luckyAttack || missed.state.lastImpact!!.luckyCritical
        )
        val capped = Stats(crit = .65, luck = 100.0)
        assertFalse(Luck.roll(State(rng = 10L), capped).criticalBoost)
        assertEquals(.65, Luck.criticalChance(.64, AttackLuck(criticalBoost = true)), .0001)
    }

    @Test
    fun seededRollFrequencyIncreasesWithLuckWithoutEverBecomingGuaranteed() {
        for (luck in listOf(10.0, 100.0, 10000.0)) {
            val s = State(rng = 123L)
            var attack = 0
            var critical = 0
            var both = 0
            repeat(10000) {
                val r = Luck.roll(s, Stats(luck = luck))
                if (r.attackBoost) attack++
                if (r.criticalBoost) critical++
                if (r.attackBoost && r.criticalBoost) both++
            }
            val chance = Luck.activationChance(luck)
            assertEquals(chance, attack / 10000.0, .02)
            assertEquals(chance, critical / 10000.0, .02)
            assertEquals(chance * chance, both / 10000.0, .02)
            assertTrue(attack in 1..9999 && critical in 1..9999)
        }
    }

    @Test
    fun impactAndRngPersistWithoutRerollAndOldImpactsDefaultToNoBonus() {
        val e = battle(11)
        val saved = SaveCodec.encode(e.state)
        val restored = GameEngine(SaveCodec.decode(saved))
        assertEquals(e.state.lastImpact, restored.state.lastImpact)
        assertEquals(e.state.rng, restored.state.rng)
        val root = Json.parseToJsonElement(saved).jsonObject
        val impact =
            JsonObject(
                root.getValue("lastImpact").jsonObject.filterKeys {
                    it !in setOf("luckyAttack", "luckyCritical")
                }
            )
        val old =
            GameEngine(SaveCodec.decode(JsonObject(root + ("lastImpact" to impact)).toString()))
        assertFalse(old.state.lastImpact!!.luckyAttack || old.state.lastImpact!!.luckyCritical)
    }

    @Test
    fun sameArmorStopsMoreOfWeakAttacksAndMatchingArmorAttackRatiosScaleConsistently() {
        val weak = Defense.damageAfterArmor(50.0, 100.0)
        val equal = Defense.damageAfterArmor(100.0, 100.0)
        val strong = Defense.damageAfterArmor(200.0, 100.0)
        assertEquals(1.0 / 3, weak / 50, .0001)
        assertEquals(.5, equal / 100, .0001)
        assertEquals(2.0 / 3, strong / 200, .0001)
        assertEquals(equal * 2, Defense.damageAfterArmor(200.0, 200.0), .0001)
        assertEquals(50.0, Defense.damageAfterArmor(50.0, 0.0), .0001)
        assertEquals(0.0, Defense.damageAfterArmor(0.0, 100.0), .0001)
    }

    @Test
    fun actualGuardDamageUsesMonsterAttackForArmorThenAppliesCriticalAndGuard() {
        for ((attack, expected) in listOf(50.0 to 20.0, 100.0 to 60.0, 200.0 to 160.0)) {
            val e =
                GameEngine(
                    State(
                        bootstrapped = true,
                        base = Stats(hp = 10000.0, defense = 100.0),
                        hp = 10000.0,
                        rng = 0L,
                        mode = Mode.COMBAT_DEFEND,
                        enemy = Enemy("공격 시험", 1000.0, 1000.0, attack, 0.0, 0.0),
                    )
                )
            e.defend(false)
            assertEquals(expected, 10000.0 - e.state.hp, .0001)
            assertEquals(ImpactOutcome.GUARD, e.state.lastImpact!!.outcome)
        }
        val stats = Stats(defense = 100.0)
        assertTrue(Defense.zeroDamageChance(stats, 50.0) > Defense.zeroDamageChance(stats, 200.0))
    }
}
