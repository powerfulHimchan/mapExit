package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AchievementsTest {
    private fun choose(e: GameEngine, action: String, arg: Long? = null) =
        e.choose(e.choices().first { it.action == action && (arg == null || it.argument == arg) })

    private fun records(e: GameEngine) { choose(e, "help"); choose(e, "achievements") }
    private fun index(id: String) = Achievements.all.indexOfFirst { it.id == id }
    private fun win(e: GameEngine, floor: Int = 1, boss: Boolean = false) {
        e.enterFloor(floor); e.startBattle(boss)
        e.state.enemy!!.hp = .01; e.state.rng = 0; e.attack(false)
        assertEquals(Mode.VICTORY, e.state.mode)
    }

    @Test fun realVictoryUnlocksOnceAndClaimSurvivesColdLoadWithoutDuplicateGold() {
        val e = GameEngine(); win(e)
        assertTrue("first-victory" in e.state.achievements.unlocked)
        assertEquals(listOf("first-victory"), e.state.achievements.notices)
        e.attack(false)
        assertEquals(1, e.state.bestiary.values.sumOf { it.defeats })
        choose(e, "battleContinue"); choose(e, "retreat"); choose(e, "runContinue")
        records(e)
        val before = e.state.gold
        val action = e.choices().first { it.action == "claimAchievement" && it.argument == index("first-victory").toLong() }
        e.choose(action); assertEquals(before + 30, e.state.gold)
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        cold.choose(action)
        assertFalse(Achievements.claim(cold.state, index("first-victory")))
        assertEquals(before + 30, cold.state.gold)
    }

    @Test fun dungeonRewardsAreLockedUntilReturningToSurfaceAndParentMenuRestores() {
        val e = GameEngine(); win(e)
        choose(e, "battleContinue"); records(e)
        assertEquals(Mode.EXPLORE, e.state.menu?.returnMode)
        assertFalse(Achievements.claim(e.state, index("first-victory")))
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        cold.navigateBack(); assertEquals("help", cold.state.menu?.type)
        cold.navigateBack(); assertEquals(Mode.EXPLORE, cold.state.mode)
        assertTrue(cold.state.achievements.claimed.isEmpty())
    }

    @Test fun confirmedChestAndTrapEventsCountExactlyOnceIncludingMimics() {
        val e = GameEngine(); e.enterFloor(1)
        val cell = e.state.dungeon!!.here(); cell.event = Event.CHEST; cell.resolved = false
        choose(e, "encounter", Event.CHEST.ordinal.toLong()); e.state.rng = 0
        choose(e, "chest")
        assertEquals(Mode.COMBAT_ATTACK, e.state.mode) // A mimic still opened a chest.
        assertEquals(1, e.state.achievements.chests)
        e.choose(Choice("stale", "chest")); assertEquals(1, e.state.achievements.chests)
        e.state.enemy = null; e.state.mode = Mode.TRAP
        cell.event = Event.TRAP; cell.resolved = false
        e.state.trap = Traps.create(e.state, 0).copy(phase = TrapPhase.RESULT, success = true)
        e.tick(250)
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        repeat(4) { cold.tick(250) }
        assertEquals(1, cold.state.achievements.traps)
        cold.state.mode = Mode.TRAP
        cold.state.trap = Traps.create(cold.state, 0).copy(phase = TrapPhase.RESULT, success = false)
        cold.tick(250)
        assertEquals(1, cold.state.achievements.traps)
    }

    @Test fun onlySuccessfulDonationAndClaimedQuestRewardsCount() {
        val e = GameEngine(State(gold = 9)); e.enterFloor(4)
        val c = e.state.dungeon!!.here(); c.event = Event.NPC; c.npcRole = NpcRole.BEGGAR; c.resolved = false; c.npcGift = false
        choose(e, "encounter", Event.NPC.ordinal.toLong()); choose(e, "npcDonate")
        assertEquals(0, e.state.achievements.donations)
        e.state.gold = 10; val donate = e.choices().first { it.action == "npcDonate" }
        e.choose(donate); e.choose(donate)
        assertEquals(1, e.state.achievements.donations)
        c.npcRole = NpcRole.QUEST; c.resolved = false
        e.state.quest = Quest("test", "kill", 1, progress = 0)
        choose(e, "encounter", Event.NPC.ordinal.toLong())
        e.choose(Choice("stale", "claim")); assertEquals(0, e.state.achievements.quests)
        e.state.quest!!.progress = 1; val claim = e.choices().first { it.action == "claim" }
        e.choose(claim); e.choose(claim)
        assertEquals(1, e.state.achievements.quests)
    }

    @Test fun secondFinalBossRequiresTwoDistinctCyclesAndSpecialGearIsOneTime() {
        val e = GameEngine(); win(e, 50, true)
        assertEquals(setOf(1), e.state.achievements.finalBossCycles)
        assertFalse("boss-50-twice" in e.state.achievements.unlocked)
        win(e, 50, true) // Regenerating and refighting the same cycle cannot farm this.
        assertEquals(setOf(1), e.state.achievements.finalBossCycles)
        val selected = e.state.inventory.first { it.kind != Kind.POTION }
        e.newCycle(selected.id)
        win(e, 50, true)
        assertEquals(setOf(1, 2), e.state.achievements.finalBossCycles)
        assertTrue("boss-50-twice" in e.state.achievements.unlocked)
        e.newCycle(e.state.inventory.first { !it.inherited && it.kind != Kind.POTION }.id)
        records(e); choose(e, "claimAchievement", index("boss-50-twice").toLong())
        val ring = e.state.inventory.single { it.variantId == "exit-seal" }
        assertEquals("귀환자의 인장", ring.name)
        assertEquals("ring_exit_seal", GearAppearance.base(ring))
        assertEquals(.25, ring.percent.attack, .0); assertEquals(.20, ring.percent.hp, .0)
        assertEquals(-.15, ring.percent.agility, .0)
        e.choose(Choice("equip", "equipLoot", ring.id))
        assertTrue(ring.id in e.state.equipment.values)
        assertEquals(432.0, e.stats().hp, .0001)
        assertEquals(360.0, e.state.hp, .0001) // Equipping max HP must not heal.
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertFalse(Achievements.claim(cold.state, index("boss-50-twice")))
        assertEquals(1, cold.state.inventory.count { it.variantId == "exit-seal" })
    }

    @Test fun specialRingUsesOrdinaryDeathAndInheritanceRulesWithoutReissuing() {
        val e = GameEngine()
        e.state.achievements.finalBossCycles.addAll(listOf(1, 2)); Achievements.evaluate(e.state)
        records(e); assertTrue(Achievements.claim(e.state, index("boss-50-twice")))
        val ring = e.state.inventory.single { it.variantId == "exit-seal" }
        e.newCycle(ring.id)
        assertTrue(e.state.inventory.single().inherited)
        e.enterFloor(1); e.state.hp = 0.0; e.die()
        assertTrue(e.state.inventory.isEmpty())
        choose(e, "runContinue"); records(e)
        assertFalse(Achievements.claim(e.state, index("boss-50-twice")))
    }

    @Test fun lowHealthVictoryUsesEquippedMaximumAndStrictTenPercentBoundary() {
        for (fraction in listOf(.10, .1001)) {
            val e = GameEngine()
            val ring = Achievements.specialRing(e.state.nextId++); e.state.inventory.add(ring)
            Equipment.equip(e.state, ring.id, Slot.RING1)
            e.state.hp = e.stats().hp * fraction
            win(e)
            assertEquals(fraction <= .10, "clutch-victory" in e.state.achievements.unlocked)
        }
    }

    @Test fun discoveryAndKillsUseRealBestiaryRecordsAndThresholds() {
        val e = GameEngine()
        MonsterCatalog.all.forEach { m ->
            e.state.bestiary[m.id] = MonsterRecord(m.id, m.name, m.sprite, m.traits, 1, 1, 1, if (m == MonsterCatalog.all.first()) 199 else 0)
        }
        Achievements.evaluate(e.state)
        assertTrue("discover-30" in e.state.achievements.unlocked)
        assertTrue("hunter-50" in e.state.achievements.unlocked)
        assertFalse("hunter-200" in e.state.achievements.unlocked)
        win(e)
        assertTrue("hunter-200" in e.state.achievements.unlocked)
    }

    @Test fun everyCounterThresholdUnlocksItsSpecifiedReward() {
        val e = GameEngine(); val p = e.state.achievements
        repeat(20) { Achievements.event(e.state, "chests"); Achievements.event(e.state, "traps") }
        repeat(5) { Achievements.event(e.state, "quests") }
        repeat(3) { Achievements.event(e.state, "donations") }
        assertTrue(p.unlocked.containsAll(listOf("chests-20", "traps-20", "quests-5", "donations-3")))
        records(e); assertTrue(Achievements.claim(e.state, index("chests-20")))
        assertEquals(2, e.state.inventory.count { it.potionGrade == PotionGrade.GREATER })
        assertTrue(Achievements.claim(e.state, index("traps-20"))); assertEquals(1, e.state.points)
    }

    @Test fun claimedTitlesPersistThroughDeathAndCycleWhileStatRewardsResetNormally() {
        val e = GameEngine(); e.state.achievements.traps = 20; Achievements.evaluate(e.state)
        records(e)
        assertFalse(Achievements.selectTitle(e.state, index("traps-20")))
        Achievements.claim(e.state, index("traps-20")); assertEquals(1, e.state.points)
        assertTrue(Achievements.selectTitle(e.state, index("traps-20")))
        e.newCycle(e.state.inventory.first { it.kind != Kind.POTION }.id)
        assertEquals(0, e.state.points); assertEquals("함정 전문가", Achievements.title(e.state))
        assertTrue("next-cycle" in e.state.achievements.unlocked)
        e.enterFloor(1); e.die(); choose(e, "runContinue"); records(e)
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertEquals("함정 전문가", Achievements.title(cold.state))
        assertTrue(Achievements.selectTitle(cold.state, -1)); assertNull(Achievements.title(cold.state))
    }

    @Test fun legacyMigrationUsesConfirmedEvidenceAndNeverInfersBossKillsFromDepth() {
        val old = State(bootstrapped = true, highest = 50, runChests = 8, runTraps = 2)
        val json = JsonObject(Json.parseToJsonElement(SaveCodec.encode(old)).jsonObject.filterKeys { it != "achievements" }).toString()
        val e = GameEngine(SaveCodec.decode(json))
        assertEquals(8, e.state.achievements.chests); assertEquals(2, e.state.achievements.traps)
        assertTrue(e.state.achievements.bossFloors.isEmpty())
        assertTrue(e.state.achievements.finalBossCycles.isEmpty())
        assertEquals(0, e.state.achievements.quests); assertEquals(0, e.state.achievements.donations)
        e.state.runChests = 19
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertEquals(8, cold.state.achievements.chests) // Migration only once.
        val completed = GameEngine(State(bootstrapped = true, cycle = 2, clears = 1, devices = mutableSetOf(50)))
        assertEquals(setOf(1, 2), completed.state.achievements.finalBossCycles)
        assertTrue("boss-50-twice" in completed.state.achievements.unlocked)
        assertTrue(completed.state.achievements.notices.isEmpty())
    }

    @Test fun trainingAndDebugCannotProgressClaimOrEquipTitles() {
        for (debug in listOf(false, true)) {
            val e = GameEngine(State(training = !debug, debug = debug))
            win(e, 50, true); Achievements.event(e.state, "chests"); Achievements.event(e.state, "donations")
            assertTrue(e.state.achievements.unlocked.isEmpty())
            assertTrue(e.state.achievements.finalBossCycles.isEmpty())
            assertEquals(0, e.state.achievements.chests)
            e.state.achievements.unlocked.add("first-victory")
            e.state.mode = Mode.MENU; e.state.menu = Menu("achievements")
            e.state.dungeon = null
            assertFalse(Achievements.claim(e.state, index("first-victory")))
            assertFalse(Achievements.selectTitle(e.state, -1))
        }
    }
}
