package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CombatTacticsTest {
    private fun battle(
        intent: EnemyIntent,
        name: String = "동굴 슬라임",
        mode: Mode = Mode.COMBAT_ATTACK,
    ): GameEngine =
        GameEngine(
                State(
                    bootstrapped = true,
                    rng = 0,
                    hp = 10000.0,
                    base =
                        Stats(
                            hp = 10000.0,
                            attack = 100.0,
                            defense = 100.0,
                            agility = 20.0,
                            crit = 0.0,
                        ),
                    mode = mode,
                    enemy = Enemy(name, 10000.0, 10000.0, 100.0, 0.0, 0.0, intent = intent),
                )
            )
            .also { engine ->
                val enemy = engine.state.enemy!!
                enemy.round = CombatTactics.pattern(enemy).indexOf(intent).coerceAtLeast(0)
            }

    @Test
    fun eachSpeciesHasAVisibleRepeatablePatternWithoutRandomDraws() {
        val expected =
            mapOf(
                "동굴 슬라임" to listOf(EnemyIntent.STRIKE, EnemyIntent.HEAVY, EnemyIntent.RECOVER),
                "상자 속 녹슨 기사" to listOf(EnemyIntent.GUARD, EnemyIntent.HEAVY, EnemyIntent.FLURRY),
                "심연 박쥐" to listOf(EnemyIntent.FLURRY, EnemyIntent.HEAVY, EnemyIntent.RECOVER),
            )
        for ((name, pattern) in expected) {
            val e = battle(pattern.first(), name)
            val rng = e.state.rng
            repeat(12) { turn ->
                assertEquals(pattern[turn % pattern.size], e.state.enemy!!.intent)
                assertTrue(CombatTactics.cue(e.state.enemy!!).isNotBlank())
                assertTrue(CombatTactics.hint(e.state.enemy!!).isNotBlank())
                CombatTactics.advance(e.state.enemy!!)
            }
            assertEquals(rng, e.state.rng)
        }
        val e = GameEngine()
        e.startBattle(true)
        assertEquals(EnemyIntent.GUARD, e.state.enemy!!.intent)
        assertEquals(5, CombatTactics.pattern(e.state.enemy!!).size)
        repeat(50) {
            e.startBattle(false)
            assertEquals(CombatTactics.pattern(e.state.enemy!!).first(), e.state.enemy!!.intent)
        }
    }

    @Test
    fun ordinaryAttacksAreReducedByTheShieldButStrongHitsBreakItAndCancelRetaliation() {
        val normal = battle(EnemyIntent.GUARD, "녹슨 기사")
        normal.attack(false)
        assertEquals(55.0, 10000 - normal.state.enemy!!.hp, .0001)
        assertEquals(Mode.COMBAT_DEFEND, normal.state.mode)
        assertEquals(EnemyIntent.GUARD, normal.state.enemy!!.intent)
        val strong = battle(EnemyIntent.GUARD, "녹슨 기사")
        val hp = strong.state.hp
        strong.attack(true)
        assertEquals(140.0, 10000 - strong.state.enemy!!.hp, .0001)
        assertEquals(Mode.COMBAT_ATTACK, strong.state.mode)
        assertEquals(EnemyIntent.HEAVY, strong.state.enemy!!.intent)
        assertTrue(strong.state.enemy!!.opening)
        assertEquals("방패 파괴", strong.state.lastImpact!!.tactic)
        strong.defend(false)
        assertEquals(hp, strong.state.hp, .0001)
    }

    @Test
    fun aMissCannotBreakTheShieldOrGenerateAnOpening() {
        val e = battle(EnemyIntent.GUARD, "녹슨 기사")
        e.state.rng =
            (0L..1000).first { seed ->
                val s = State(rng = seed)
                s.random()
                s.random() >= .70
            }
        e.attack(true)
        assertEquals(ImpactOutcome.MISS, e.state.lastImpact!!.outcome)
        assertFalse(e.state.enemy!!.opening)
        assertEquals(EnemyIntent.GUARD, e.state.enemy!!.intent)
        assertEquals(Mode.COMBAT_DEFEND, e.state.mode)
    }

    @Test
    fun dodgingHeavyAttackCreatesOneOpeningAndOnlyTheNextAttackConsumesIt() {
        val e = battle(EnemyIntent.HEAVY, "녹슨 기사", Mode.COMBAT_DEFEND)
        e.defend(true)
        assertEquals(ImpactOutcome.DODGE, e.state.lastImpact!!.outcome)
        assertEquals(10000.0, e.state.hp, .0001)
        assertTrue(e.state.enemy!!.opening)
        assertEquals(EnemyIntent.FLURRY, e.state.enemy!!.intent)
        e.state.rng = 0
        e.attack(false)
        assertEquals(135.0, 10000 - e.state.enemy!!.hp, .0001)
        assertEquals("빈틈 공격", e.state.lastImpact!!.tactic)
        assertFalse(e.state.enemy!!.opening)
        val damage = e.state.enemy!!.hp
        e.attack(false)
        assertEquals(damage, e.state.enemy!!.hp, .0001)
    }

    @Test
    fun failedDodgeAndGuardingAHeavyAttackDoNotGrantOpening() {
        val guarded = battle(EnemyIntent.HEAVY, mode = Mode.COMBAT_DEFEND)
        guarded.defend(false)
        assertFalse(guarded.state.enemy!!.opening)
        assertTrue(guarded.state.lastImpact!!.amount > 0)
        val failed = battle(EnemyIntent.HEAVY, mode = Mode.COMBAT_DEFEND)
        failed.state.rng =
            (0L..1000).first { seed ->
                val s = State(rng = seed)
                s.random()
                s.random() > .80
            }
        failed.defend(true)
        assertEquals(ImpactOutcome.DAMAGE, failed.state.lastImpact!!.outcome)
        assertFalse(failed.state.enemy!!.opening)
    }

    @Test
    fun guardingFlurryReducesDamageAndCreatesOpeningWhileDodgingDoesNot() {
        val guarded = battle(EnemyIntent.FLURRY, "심연 박쥐", Mode.COMBAT_DEFEND)
        val ordinary = battle(EnemyIntent.STRIKE, mode = Mode.COMBAT_DEFEND)
        guarded.defend(false)
        ordinary.defend(false)
        assertTrue(guarded.state.lastImpact!!.amount < ordinary.state.lastImpact!!.amount)
        assertTrue(guarded.state.enemy!!.opening)
        assertEquals(EnemyIntent.HEAVY, guarded.state.enemy!!.intent)
        val dodge = battle(EnemyIntent.FLURRY, "심연 박쥐", Mode.COMBAT_DEFEND)
        dodge.defend(true)
        assertEquals(ImpactOutcome.DODGE, dodge.state.lastImpact!!.outcome)
        assertFalse(dodge.state.enemy!!.opening)
    }

    @Test
    fun openingImprovesStrongAccuracyAndIsConsumedEvenOnMissWithoutChangingStats() {
        val seed =
            (0L..1000).first {
                val s = State(rng = it)
                s.random()
                s.random().let { roll -> roll >= 0.70 && roll < 0.90 }
            }
        val plain = battle(EnemyIntent.STRIKE)
        plain.state.rng = seed
        plain.attack(true)
        assertEquals(ImpactOutcome.MISS, plain.state.lastImpact!!.outcome)
        val boosted = battle(EnemyIntent.STRIKE)
        boosted.state.rng = seed
        boosted.state.enemy!!.opening = true
        val stats = boosted.stats()
        boosted.attack(true)
        assertEquals(189.0, 10000 - boosted.state.enemy!!.hp, .0001)
        assertEquals(stats, boosted.stats())
        assertFalse(boosted.state.enemy!!.opening)
        val missed = battle(EnemyIntent.STRIKE)
        missed.state.enemy!!.opening = true
        missed.state.rng =
            (0L..1000).first {
                val s = State(rng = it)
                s.random()
                s.random() >= .90
            }
        missed.attack(true)
        assertEquals(ImpactOutcome.MISS, missed.state.lastImpact!!.outcome)
        assertFalse(missed.state.enemy!!.opening)
    }

    @Test
    fun recoveringEnemiesTakeBonusDamageAndSkipTheirAttackButCannotStackBonuses() {
        val e = battle(EnemyIntent.RECOVER)
        e.state.enemy!!.round = 2
        e.state.enemy!!.opening = true
        e.attack(false)
        assertEquals(135.0, 10000 - e.state.enemy!!.hp, .0001)
        assertEquals(10000.0, e.state.hp, .0001)
        assertEquals(Mode.COMBAT_ATTACK, e.state.mode)
        assertEquals(EnemyIntent.STRIKE, e.state.enemy!!.intent)
        assertFalse(e.state.enemy!!.opening)
        val item = battle(EnemyIntent.RECOVER, mode = Mode.COMBAT_DEFEND)
        item.defend(false)
        assertEquals(10000.0, item.state.hp, .0001)
        assertEquals(Mode.COMBAT_ATTACK, item.state.mode)
    }

    @Test
    fun savedPlanOpeningAndImpactSurviveReloadWithoutRerollOrDoubleBonus() {
        val e = battle(EnemyIntent.GUARD, "녹슨 기사")
        e.attack(true)
        val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertEquals(e.state.enemy, restored.state.enemy)
        assertEquals(e.state.rng, restored.state.rng)
        assertEquals("방패 파괴", restored.state.lastImpact!!.tactic)
        for (engine in listOf(e, restored)) {
            engine.state.rng = 0
            engine.attack(true)
        }
        assertEquals(e.state.enemy, restored.state.enemy)
        assertEquals(e.state.lastImpact, restored.state.lastImpact)
        assertFalse(restored.state.enemy!!.opening)
        val json = Json.parseToJsonElement(SaveCodec.encode(e.state)).jsonObject
        val oldEnemy =
            JsonObject(
                json.getValue("enemy").jsonObject.filterKeys {
                    it !in setOf("intent", "round", "opening")
                }
            )
        val oldImpact =
            JsonObject(json.getValue("lastImpact").jsonObject.filterKeys { it != "tactic" })
        val old =
            SaveCodec.decode(
                JsonObject(json + ("enemy" to oldEnemy) + ("lastImpact" to oldImpact)).toString()
            )
        assertEquals(EnemyIntent.STRIKE, old.enemy!!.intent)
        assertFalse(old.enemy!!.opening)
        assertEquals("", old.lastImpact!!.tactic)
    }

    @Test
    fun lethalOpeningAttackWinsOnceWithoutCreatingAnotherTurn() {
        val e = battle(EnemyIntent.GUARD, "녹슨 기사")
        e.state.enemy!!.hp = 1.0
        e.attack(true)
        assertEquals(Mode.VICTORY, e.state.mode)
        assertNull(e.state.enemy)
        val gold = e.state.gold
        repeat(5) {
            e.attack(true)
            e.defend(false)
        }
        assertEquals(gold, e.state.gold)
    }

    @Test
    fun differentTellPosesCommunicateIntentWithoutChangingTheState() {
        val poses = EnemyIntent.entries.map { BattleAnimation.tell(it, 1000) }
        assertEquals(5, poses.distinct().size)
        assertTrue(poses[EnemyIntent.HEAVY.ordinal].weaponAngle < -90)
        assertEquals(1f, poses[EnemyIntent.GUARD.ordinal].guard, 0f)
        assertTrue(poses[EnemyIntent.RECOVER.ordinal].lean > 0)
        assertNotEquals(
            BattleAnimation.tell(EnemyIntent.FLURRY, 100),
            BattleAnimation.tell(EnemyIntent.FLURRY, 300),
        )
    }
}
