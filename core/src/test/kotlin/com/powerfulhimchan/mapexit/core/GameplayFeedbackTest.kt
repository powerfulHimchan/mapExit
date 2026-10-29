package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class GameplayFeedbackTest {
    private fun choose(e: GameEngine, action: String, arg: Long? = null) =
        e.choose(e.choices().first { it.action == action && (arg == null || it.argument == arg) })

    @Test
    fun mapUsesFreshEntrySeedsButResumeKeepsExistingMap() {
        var calls = 0L
        val e = GameEngine(mapSeed = { ++calls })
        e.enterFloor(1)
        val first = e.state.dungeon!!.cells.map { it.wall }
        val saved = SaveCodec.encode(e.state)
        val restored = GameEngine(SaveCodec.decode(saved), mapSeed = { ++calls })
        assertEquals(1L, calls)
        assertEquals(e.state.dungeon, restored.state.dungeon)
        restored.enterFloor(1)
        assertEquals(2L, calls)
        assertNotEquals(first, restored.state.dungeon!!.cells.map { it.wall })
        restored.enterFloor(2)
        assertEquals(3L, calls)
    }

    @Test
    fun junctionHintClearsAfterChoosingEitherDirection() {
        for (direction in listOf(0, 2)) {
            val d = Dungeon(9, 9, MutableList(81) { Cell() }, x = 4, y = 4, direction = 0)
            for (y in 2..6) d.cell(4, y).wall = false
            d.cell(5, 4).wall = false
            val e =
                GameEngine(
                    State(
                        bootstrapped = true,
                        floor = 1,
                        dungeon = d,
                        mode = Mode.EXPLORE,
                        message = "갈림길: 방향을 선택하세요",
                    )
                )
            choose(e, "move", direction.toLong())
            assertFalse(e.state.message.contains("갈림길:"))
            assertNotEquals(4, d.y)
        }
    }

    @Test
    fun chestNoticeMatchesRealRewardsAndDoesNotGrantAgainAfterReload() {
        val e = GameEngine()
        e.enterFloor(1)
        val cell = e.state.dungeon!!.here()
        cell.event = Event.CHEST
        e.state.rng = 1L
        val count = e.state.inventory.size
        choose(e, "encounter", Event.CHEST.ordinal.toLong())
        choose(e, "chest")
        val loot = requireNotNull(e.state.chestLoot)
        assertEquals(count + loot.items.size, e.state.inventory.size)
        assertTrue(loot.items.all { item -> e.state.inventory.any { it.id == item.id } })
        assertEquals(loot.gold, e.state.gold)
        val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertEquals(loot, restored.state.chestLoot)
        assertEquals(e.state.inventory, restored.state.inventory)
        assertEquals(e.state.gold, restored.state.gold)
        assertTrue(restored.state.dungeon!!.here().resolved)
    }

    @Test
    fun guardChanceIncreasesWithBothStatsAndCannotReachInvulnerability() {
        val base = Defense.zeroDamageChance(Stats(defense = 0.0, luck = 0.0))
        val armor = Defense.zeroDamageChance(Stats(defense = 300.0, luck = 0.0))
        val lucky = Defense.zeroDamageChance(Stats(defense = 300.0, luck = 150.0))
        assertTrue(base < armor && armor < lucky)
        assertTrue(Defense.zeroDamageChance(Stats(defense = 1e12, luck = 1e12)) <= .24)
    }

    @Test
    fun fullGuardKeepsHealthAndHasItsOwnZeroDamageOutcome() {
        val e = GameEngine(State(base = Stats(defense = 400.0, luck = 200.0)))
        e.enterFloor(1)
        e.startBattle(false)
        e.state.mode = Mode.COMBAT_DEFEND
        e.state.rng = 0L
        val hp = e.state.hp
        e.defend(false)
        assertEquals(hp, e.state.hp, .0001)
        assertEquals(ImpactOutcome.BLOCK, e.state.lastImpact!!.outcome)
        assertEquals(0, e.state.lastImpact!!.amount)
        assertFalse(e.state.lastImpact!!.critical)
        assertEquals(Mode.COMBAT_ATTACK, e.state.mode)
    }

    @Test
    fun beamChallengeUsesSeparateTapsInsteadOfHolding() {
        val t = Traps.create(State(), 4).copy(phase = TrapPhase.ACTIVE, signalMs = 500)
        Traps.tap(t, t.pattern[0])
        assertEquals(TrapPhase.ACTIVE, t.phase)
        assertEquals(-1, t.held)
        Traps.tick(t, 550)
        Traps.tap(t, t.pattern[0])
        assertTrue(t.success)
        assertEquals(TrapPhase.RESULT, t.phase)
    }

    @Test
    fun trainingHasOnlyFourCategoriesAndRestoresNormalProgress() {
        val e = GameEngine(State(gold = 57, points = 8))
        val original = SaveCodec.encode(e.state)
        choose(e, "training")
        assertEquals(
            listOf("trainingTraps", "trainBattle", "trainMerchant", "trainNpc", "back"),
            e.choices().map { it.action },
        )
        for (action in listOf("trainBattle", "trainMerchant", "trainNpc")) {
            choose(e, action)
            assertTrue(e.state.training && e.state.debug)
            assertEquals(1000, e.state.gold)
            if (action == "trainMerchant") assertEquals(Event.MERCHANT, e.state.encounter)
            if (action == "trainNpc") assertNotNull(e.state.dungeon!!.here().questOffer)
            e.state.gold = 0
            e.trainingMenu()
        }
        choose(e, "trainingTraps")
        choose(e, "trainTrap", 4)
        assertEquals(4, e.state.trap!!.type)
        e.exitTraining()
        assertEquals(original, SaveCodec.encode(e.state))
    }
}
