package com.powerfulhimchan.mapexit.core

import kotlin.math.roundToInt
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BattleFeedbackTest {
    private fun choose(e: GameEngine, action: String) {
        e.choose(e.choices().first { it.action == action })
    }

    private fun kill(e: GameEngine) {
        e.state.enemy!!.hp = 1.0
        e.state.rng = 0L
        e.attack(false)
        assertEquals(Mode.VICTORY, e.state.mode)
    }

    @Test
    fun victoryWaitsForConfirmationAndCannotPayRewardsTwiceAfterReload() {
        val e = GameEngine()
        e.enterFloor(1)
        e.startBattle(false)
        val gold = e.state.gold
        val experience = e.state.experience
        kill(e)
        assertEquals(gold + 24, e.state.gold)
        assertEquals(experience + 30, e.state.experience)
        assertNotNull(e.state.battleResult)
        assertNull(e.state.enemy)
        val saved = SaveCodec.encode(e.state)
        val restored = GameEngine(SaveCodec.decode(saved))
        repeat(100) {
            restored.tick(250)
            restored.attack(true)
            restored.press(0)
        }
        assertEquals(Mode.VICTORY, restored.state.mode)
        assertEquals(e.state.gold, restored.state.gold)
        restored.press(1)
        assertEquals(Mode.EXPLORE, restored.state.mode)
        assertNull(restored.state.battleResult)
        assertEquals(e.state.gold, restored.state.gold)
        restored.choose(Choice("old confirmation", "battleContinue"))
        assertEquals(e.state.gold, restored.state.gold)
    }

    @Test
    fun bossAndFinalFloorVictoryRetainTheirCorrectContinuation() {
        for (floor in listOf(10, 50)) {
            val e = GameEngine()
            e.enterFloor(floor)
            e.startBattle(true)
            kill(e)
            assertTrue(floor in e.state.devices)
            assertTrue(e.state.battleResult!!.enemy.boss)
            assertTrue(e.state.battleResult!!.items.isNotEmpty())
            assertTrue(e.state.dungeon!!.bossCleared)
            e.press(1)
            assertEquals(Mode.FLOOR_CLEAR, e.state.mode)
            if (floor == 50) {
                choose(e, "ending")
                assertEquals(Mode.INHERIT, e.state.mode)
            }
        }
    }

    @Test
    fun lethalDamageReportsTheActualRemainingHealthWithNoRetaliation() {
        val e = GameEngine()
        e.enterFloor(1)
        e.startBattle(false)
        val hp = e.state.hp
        kill(e)
        val impact = e.state.lastImpact!!
        assertEquals(ImpactTarget.ENEMY, impact.target)
        assertEquals(1, impact.amount)
        assertEquals(hp, e.state.hp, .001)
        assertEquals(0.0, e.state.battleResult!!.enemy.hp, .001)
    }

    @Test
    fun normalCriticalAndMissHaveDistinctStructuredFeedback() {
        val outcomes = mutableSetOf<ImpactOutcome>()
        var critical = false
        for (seed in 0L..100L) {
            val e = GameEngine()
            e.startBattle(false)
            e.state.enemy = e.state.enemy!!.copy(maxHp = 100000.0, hp = 100000.0)
            e.state.base = e.state.base.copy(crit = 1.0)
            e.state.rng = seed
            e.attack(false)
            val event = e.state.lastImpact!!
            assertEquals(ImpactTarget.ENEMY, event.target)
            outcomes.add(event.outcome)
            critical = critical || event.critical
            if (event.outcome == ImpactOutcome.MISS) {
                assertEquals(0, event.amount)
                assertEquals("빗나감", event.label())
            } else assertTrue(event.amount > 0)
        }
        assertTrue(critical)
        assertEquals(setOf(ImpactOutcome.MISS, ImpactOutcome.DAMAGE), outcomes)
    }

    @Test
    fun guardReportsPlayerHealthLossAndCannotRepeatAnAttackOutOfTurn() {
        val e = GameEngine()
        e.startBattle(false)
        e.state.rng = 0L
        e.attack(false)
        val seq = e.state.impactSequence
        e.attack(true)
        assertEquals(seq, e.state.impactSequence)
        val hp = e.state.hp
        e.defend(false)
        val event = e.state.lastImpact!!
        assertEquals(ImpactTarget.PLAYER, event.target)
        assertEquals(ImpactOutcome.GUARD, event.outcome)
        assertEquals((hp - e.state.hp).roundToInt().coerceAtLeast(1), event.amount)
        assertEquals(seq + 1, event.serial)
    }

    @Test
    fun dodgeReportsZeroDamageAndFailuresReportActualDamage() {
        val outcomes = mutableSetOf<ImpactOutcome>()
        for (seed in 0L..100L) {
            val e = GameEngine()
            e.startBattle(false)
            e.state.mode = Mode.COMBAT_DEFEND
            e.state.base = e.state.base.copy(agility = 1000.0)
            e.state.rng = seed
            val hp = e.state.hp
            e.defend(true)
            val event = e.state.lastImpact!!
            outcomes.add(event.outcome)
            if (event.outcome == ImpactOutcome.DODGE) {
                assertEquals(0, event.amount)
                assertEquals(hp, e.state.hp, .001)
            } else assertTrue(event.amount > 0)
        }
        assertEquals(setOf(ImpactOutcome.DODGE, ImpactOutcome.DAMAGE), outcomes)
    }

    @Test
    fun fatalPlayerDamageDoesNotBecomeAFalseVictory() {
        val e = GameEngine()
        e.enterFloor(1)
        e.startBattle(false)
        e.state.mode = Mode.COMBAT_DEFEND
        e.state.hp = 1.0
        e.defend(false)
        assertEquals(Mode.SURFACE, e.state.mode)
        assertNull(e.state.battleResult)
        assertEquals(ImpactTarget.PLAYER, e.state.lastImpact!!.target)
        assertEquals(1, e.state.lastImpact!!.amount)
    }

    @Test
    fun allFiveTrapCuesStartAutomaticallyAfterOneSecondAndSurviveSave() {
        for (type in 0..4) {
            val e = GameEngine(debugTools = true)
            choose(e, "debug")
            e.choose(e.choices().first { it.action == "testTrap" && it.argument == type.toLong() })
            assertEquals(Mode.TRAP, e.state.mode)
            assertTrue(e.state.trap!!.cue().length <= 10)
            repeat(3) {
                e.tick(250)
                e.press(0)
                e.press(1)
            }
            assertEquals(TrapPhase.PREPARE, e.state.trap!!.phase)
            assertEquals(0, e.state.trap!!.progress)
            val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
            restored.tick(250)
            assertEquals(
                if (type == 3) TrapPhase.REVEAL else TrapPhase.ACTIVE,
                restored.state.trap!!.phase,
            )
            assertEquals(0L, restored.state.trap!!.elapsedMs)
            assertTrue(restored.choices().isEmpty())
        }
    }

    @Test
    fun realDungeonTrapEncounterStartsWithAnAutomaticCue() {
        val d = Dungeon(7, 7, MutableList(49) { Cell() }, x = 3, y = 3, direction = 0)
        d.here().wall = false
        d.cell(3, 2).wall = false
        d.cell(3, 2).event = Event.TRAP
        val e = GameEngine(State(bootstrapped = true, floor = 1, dungeon = d, mode = Mode.EXPLORE))
        e.press(0)
        assertEquals(Mode.TRAP, e.state.mode)
        assertEquals(3, d.x)
        assertEquals(2, d.y)
        assertFalse(d.here().resolved)
    }

    @Test
    fun oldSaveWithoutNewFieldsLoadsAndRunningTrapsDoNotRestartTheirGuide() {
        val s = State(bootstrapped = true, mode = Mode.TRAP, trap = Traps.create(State(), 1))
        s.trap!!.elapsedMs = 400
        val json = Json.parseToJsonElement(SaveCodec.encode(s)).jsonObject
        val old =
            JsonObject(
                    json.filterKeys {
                        it !in setOf("battleId", "battleResult", "impactSequence", "lastImpact")
                    }
                )
                .toString()
        val e = GameEngine(SaveCodec.decode(old))
        assertNull(e.state.battleResult)
        assertEquals(0L, e.state.impactSequence)
        e.tick(250)
        assertEquals(Mode.TRAP, e.state.mode)
        assertEquals(650L, e.state.trap!!.elapsedMs)
    }
}
