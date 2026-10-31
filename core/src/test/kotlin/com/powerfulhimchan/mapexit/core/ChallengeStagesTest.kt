package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ChallengeStagesTest {
    private fun choose(e: GameEngine, action: String, arg: Long? = null) {
        e.choose(e.choices().first { it.action == action && (arg == null || it.argument == arg) })
        // Cell-by-cell fixtures use the same stop action as the player; auto-walk has separate tests.
        if (action == "move" && e.state.challenge?.autoMoving == true)
            e.choose(e.choices().first { it.action == "challengeStop" })
    }

    private fun fixture(kind: ChallengeKind): GameEngine {
        val seed = (1L..1000L).first { ChallengeStages.kindFor(it, ChallengeStages.floors(it).first()) == kind }
        val e = GameEngine(State(visitorSeed = seed, challengeProgress = ChallengeProgress(seed = seed), gold = 70, hp = 100.0))
        e.enterFloor(ChallengeStages.floors(seed).first())
        val m = e.state.dungeon!!; val portal = m.cells.indexOfFirst { it.event == Event.PORTAL }
        assertTrue(portal >= 0); m.x = portal % m.width; m.y = portal / m.width
        choose(e, "challengeEnter")
        assertEquals(kind, e.state.challenge!!.kind)
        return e
    }

    private fun start(e: GameEngine) {
        choose(e, "challengeStart")
        if (e.state.challenge!!.phase == ChallengePhase.PREVIEW) choose(e, "challengeRemember")
    }

    private fun tick(e: GameEngine, ms: Long) {
        var left = ms
        while (left > 0) { val step = minOf(250L, left); e.tick(step); left -= step }
    }

    private fun walk(e: GameEngine, target: Int) {
        var guard = 0
        while (e.state.challenge!!.phase != ChallengePhase.RESULT &&
            e.state.dungeon!!.let { it.y * it.width + it.x } != target) {
            check(guard++ < 300)
            val s = e.state; val c = s.challenge!!; val m = s.dungeon!!
            if (c.phase == ChallengePhase.HAZARD) {
                while (!ChallengeStages.hazardSafe(m.here().challengeMark, c.hazardMs)) choose(e, "challengeWait")
                choose(e, "challengeCross")
            }
            if (c.kind == ChallengeKind.TORCH && m.here().event == Event.CHALLENGE && !m.here().resolved) choose(e, "challengeUse")
            val next = ChallengeStages.path(m, target).getOrNull(1) ?: error("No path")
            val d = (0..3).first { m.x + MapGenerator.dx[it] == next % m.width && m.y + MapGenerator.dy[it] == next / m.width }
            choose(e, "move", d.toLong()); tick(e, 250)
        }
        if (e.state.challenge!!.phase == ChallengePhase.HAZARD) {
            while (!ChallengeStages.hazardSafe(e.state.dungeon!!.here().challengeMark, e.state.challenge!!.hazardMs)) choose(e, "challengeWait")
            choose(e, "challengeCross")
        }
    }

    private fun solve(e: GameEngine) {
        val c = e.state.challenge!!
        if (c.kind == ChallengeKind.SEALS) for (mark in c.sealOrder) {
            val index = e.state.dungeon!!.cells.indexOfFirst { it.challengeMark == mark }
            walk(e, index); choose(e, "challengeUse")
        }
        walk(e, e.state.dungeon!!.cells.indexOfFirst { it.event == Event.EXIT })
        assertEquals(ChallengePhase.RESULT, e.state.challenge!!.phase)
        assertTrue(e.state.challenge!!.success)
    }

    @Test fun schedulesHaveThreeStableNonBossWindowsAndAllSevenKindsAppear() {
        val counts = mutableMapOf<ChallengeKind, Int>()
        for (seed in 1L..1000L) {
            val floors = ChallengeStages.floors(seed)
            assertEquals(3, floors.distinct().size)
            floors.forEachIndexed { i, floor ->
                assertTrue(floor in listOf(11..14, 26..29, 41..44)[i])
                assertNotEquals(0, floor % 5)
                val k = ChallengeStages.kindFor(seed, floor); counts[k] = (counts[k] ?: 0) + 1
            }
            assertTrue(floors.zipWithNext().all { (a, b) -> b - a in 12..18 })
            assertEquals(floors, ChallengeStages.floors(seed))
        }
        assertEquals(7, counts.size); assertTrue(counts.values.all { it in 300..550 })
    }

    @Test fun portalsAreReachableAndPreserveOrdinaryFloorEvents() {
        for (seed in 1L..40L) for (floor in 1..50) {
            val s = State(bootstrapped = true, visitorSeed = seed, challengeProgress = ChallengeProgress(seed))
            val map = MapGenerator.generate(s, floor)
            assertEquals(if (floor in ChallengeStages.floors(seed)) 1 else 0, map.cells.count { it.event == Event.PORTAL })
            assertEquals(2, map.cells.count { it.event == Event.CHEST })
            assertEquals(1, map.cells.count { it.event == Event.EXIT })
            assertEquals(3, map.cells.count { it.event == Event.TRAP })
            assertEquals(if (floor % 5 == 0) 1 else 0, map.cells.count { it.event == Event.ELITE })
            val dist = MapGenerator.distances(map, 1, 1)
            map.cells.indices.filter { map.cells[it].event == Event.PORTAL }.forEach { assertTrue(dist[it] > 0) }
        }
    }

    @Test fun everyKindHasOneReachableExitAndNoMonstersOrOrdinaryServices() {
        for (kind in ChallengeKind.entries) {
            val e = fixture(kind); val m = e.state.dungeon!!
            assertEquals(1, m.cells.count { it.event == Event.EXIT })
            assertTrue(m.cells.all { it.event in listOf(Event.NONE, Event.EXIT, Event.CHALLENGE) })
            assertTrue(ChallengeStages.path(m).size > 10)
            assertEquals(ChallengePhase.INTRO, e.state.challenge!!.phase)
            tick(e, 10000); assertEquals(0L, e.state.challenge!!.elapsedMs)
        }
    }

    @Test fun allSevenCanBeCompletedAndRewardsAreAppliedOnceAcrossSaveAndReturn() {
        for (kind in ChallengeKind.entries) {
            val e = fixture(kind); val hostCells = e.state.challenge!!.hostDungeon.cells.map { it.wall to it.event }
            val hp = e.state.hp; val floor = e.state.floor; val gold = e.state.gold
            start(e); solve(e)
            val c = e.state.challenge!!
            assertTrue(c.rewardApplied); assertEquals(gold + c.rewardGold, e.state.gold)
            assertTrue(c.rewards.any { it.potionGrade == PotionGrade.GREATER })
            val items = e.state.inventory.size; val afterGold = e.state.gold
            val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
            ChallengeStages.finish(cold.state, true, "stale")
            assertEquals(items, cold.state.inventory.size); assertEquals(afterGold, cold.state.gold)
            assertEquals(1, cold.state.challengeProgress.records.getValue(kind).clears)
            choose(cold, "challengeLeave")
            assertEquals(Mode.EXPLORE, cold.state.mode); assertEquals(floor, cold.state.floor)
            assertEquals(hp, cold.state.hp, .0)
            assertTrue(cold.state.dungeon!!.here().resolved)
            assertEquals(Event.PORTAL, cold.state.dungeon!!.here().event)
            assertTrue(cold.state.challengeProgress.records.getValue(kind).bestTimeMs != null)
            assertFalse(cold.choices().any { it.action == "challengeEnter" })
            assertEquals(hostCells, cold.state.dungeon!!.cells.map { it.wall to it.event })
        }
    }

    @Test fun hostPositionQuestEquipmentAndRunCountersSurviveChallengeExactly() {
        val e = fixture(ChallengeKind.LOST); val host = e.state.challenge!!.hostDungeon
        val position = Triple(host.x, host.y, host.direction)
        e.state.quest = Quest("keep", "floor", 20, 7); e.state.runKills = 6; e.state.runChests = 4; e.state.runTraps = 3
        val equipment = e.state.equipment.toMap(); val bestiary = e.state.bestiary.toMap(); val experience = e.state.experience
        start(e); solve(e); choose(e, "challengeLeave")
        assertEquals(position, e.state.dungeon!!.let { Triple(it.x, it.y, it.direction) })
        assertEquals(7, e.state.quest!!.progress); assertEquals(6, e.state.runKills)
        assertEquals(4, e.state.runChests); assertEquals(3, e.state.runTraps)
        assertEquals(experience, e.state.experience); assertEquals(equipment, e.state.equipment)
        assertEquals(bestiary, e.state.bestiary)
    }

    @Test fun lookingAtIntroCanBeCancelledWithoutConsumingPortalOrRecordingAttempt() {
        val e = fixture(ChallengeKind.LOST); val map = SaveCodec.encode(e.state)
        val cold = GameEngine(SaveCodec.decode(map)); choose(cold, "challengeLeave")
        assertFalse(cold.state.dungeon!!.here().resolved)
        assertTrue(cold.state.challengeProgress.usedFloors.isEmpty())
        assertTrue(cold.state.challengeProgress.records.isEmpty())
        choose(cold, "challengeEnter"); assertEquals(ChallengeKind.LOST, cold.state.challenge!!.kind)
    }

    @Test fun memoryPreviewLastsSevenSecondsAndDoesNotSpendEscapeTime() {
        val e = fixture(ChallengeKind.MEMORY); choose(e, "challengeStart")
        tick(e, 6750); assertEquals(ChallengePhase.PREVIEW, e.state.challenge!!.phase)
        assertEquals(0L, e.state.challenge!!.elapsedMs)
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        tick(cold, 250); assertEquals(ChallengePhase.ACTIVE, cold.state.challenge!!.phase)
        assertEquals(0L, cold.state.challenge!!.elapsedMs)
        tick(cold, 250); assertEquals(250L, cold.state.challenge!!.elapsedMs)
    }

    @Test fun pauseFreezesEveryClockAndHardwareBackResumesWithoutRevealingMap() {
        for (kind in listOf(ChallengeKind.MEMORY, ChallengeKind.COLLAPSE, ChallengeKind.TORCH, ChallengeKind.HAZARDS)) {
            val e = fixture(kind); choose(e, "challengeStart")
            val phase = e.state.challenge!!.phase
            e.pause(); val before = SaveCodec.encode(e.state)
            tick(e, 5000)
            val c = e.state.challenge!!
            assertEquals(ChallengePhase.PAUSED, c.phase); assertEquals(0L, c.elapsedMs)
            assertEquals(22000L, c.torchMs); assertEquals(7000L, c.previewMs)
            val cold = GameEngine(SaveCodec.decode(before)); cold.navigateBack()
            assertEquals(phase, cold.state.challenge!!.phase)
        }
    }

    @Test fun timeoutAndGiveUpNeverDamageOrRemoveItemsAndCannotBeRetriedThisCycle() {
        for (giveUp in listOf(false, true)) {
            val e = fixture(ChallengeKind.LOST); val hp = e.state.hp; val gold = e.state.gold; val inventory = e.state.inventory.toList()
            start(e)
            if (giveUp) { e.navigateBack(); choose(e, "challengeGiveUp") } else tick(e, 90000)
            assertFalse(e.state.challenge!!.success); assertTrue(e.state.challenge!!.rewards.isEmpty())
            choose(e, "challengeLeave")
            assertEquals(hp, e.state.hp, .0); assertEquals(gold, e.state.gold); assertEquals(inventory, e.state.inventory)
            val floor = e.state.floor; e.enterFloor(floor)
            assertTrue(e.state.dungeon!!.cells.single { it.event == Event.PORTAL }.resolved)
            assertEquals(1, e.state.challengeProgress.records.getValue(ChallengeKind.LOST).attempts)
        }
    }

    @Test fun collapsingFloorsRespectThreeSecondsAndPersistAcrossReload() {
        val e = fixture(ChallengeKind.COLLAPSE); start(e)
        val m = e.state.dungeon!!; val old = m.y * m.width + m.x
        val next = ChallengeStages.path(m)[1]
        val dir = (0..3).first { m.x + MapGenerator.dx[it] == next % m.width && m.y + MapGenerator.dy[it] == next / m.width }
        choose(e, "move", dir.toLong()); tick(e, 2750)
        assertFalse(m.cells[old].wall)
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state))); tick(cold, 250)
        assertTrue(cold.state.dungeon!!.cells[old].wall)
        assertTrue(ChallengeStages.path(cold.state.dungeon!!).isNotEmpty())
        assertEquals(ChallengePhase.ACTIVE, cold.state.challenge!!.phase)
    }

    @Test fun returningToAnExpiringTileFailsWithoutSoftLockingHostMap() {
        val e = fixture(ChallengeKind.COLLAPSE); start(e)
        val start = e.state.dungeon!!.let { it.y * it.width + it.x }
        val route = ChallengeStages.path(e.state.dungeon!!)
        walk(e, route[1]); walk(e, start); tick(e, 3000)
        assertEquals(ChallengePhase.RESULT, e.state.challenge!!.phase)
        assertFalse(e.state.challenge!!.success)
        choose(e, "challengeLeave"); assertFalse(e.state.dungeon!!.here().wall)
    }

    @Test fun collapsingRouteCluesMatchAnActuallyTraversableExitDirection() {
        val e = fixture(ChallengeKind.COLLAPSE); start(e)
        repeat(5) {
            val m = e.state.dungeon!!; val next = ChallengeStages.path(m)[1]
            assertTrue(ChallengeStages.clue(e.state).contains(ChallengeStages.rune(next)))
            val dir = (0..3).first { m.x + MapGenerator.dx[it] == next % m.width && m.y + MapGenerator.dy[it] == next / m.width }
            assertTrue(e.choices().any { it.action == "move" && it.argument == dir.toLong() })
            choose(e, "move", dir.toLong()); tick(e, 250)
        }
    }

    @Test fun sealsRequireCorrectOrderWrongInputsCostTenSecondsAndExitStaysLocked() {
        val e = fixture(ChallengeKind.SEALS); start(e); val c = e.state.challenge!!
        val exit = e.state.dungeon!!.cells.indexOfFirst { it.event == Event.EXIT }
        walk(e, exit); assertEquals(ChallengePhase.ACTIVE, c.phase); assertFalse(c.success)
        val wrong = (1..3).first { it != c.sealOrder[0] }
        walk(e, e.state.dungeon!!.cells.indexOfFirst { it.challengeMark == wrong })
        val before = c.elapsedMs; choose(e, "challengeUse")
        assertEquals(before + 10000, c.elapsedMs); assertEquals(0, c.seals)
        assertFalse(e.state.dungeon!!.here().resolved)
        solve(e); assertEquals(3, c.seals)
    }

    @Test fun torchRequiresRefillingAndPreservesFuelThroughPauseAndSave() {
        val e = fixture(ChallengeKind.TORCH); start(e)
        tick(e, 15000); assertEquals(7000L, e.state.challenge!!.torchMs)
        val brazier = e.state.dungeon!!.cells.indexOfFirst { it.challengeMark == ChallengeStages.BRAZIER }
        walk(e, brazier); choose(e, "challengeUse")
        assertEquals(22000L, e.state.challenge!!.torchMs)
        e.choose(Choice("stale", "challengeUse")); assertEquals(22000L, e.state.challenge!!.torchMs)
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        tick(cold, 22000); assertEquals(ChallengePhase.RESULT, cold.state.challenge!!.phase)
        assertEquals("횃불이 꺼졌습니다", cold.state.challenge!!.result)
    }

    @Test fun hazardWaitAndCrossUseDifferentPhysicalWindowsAndBlockWalking() {
        for (mark in listOf(ChallengeStages.BLADE, ChallengeStages.FALLING, ChallengeStages.WALL)) {
            val e = fixture(ChallengeKind.HAZARDS); start(e)
            val cell = e.state.dungeon!!.cells.indexOfFirst { it.challengeMark == mark }
            walk(e, cell)
            // walk resolves a gate when its target is reached, so restore it for the boundary test.
            e.state.dungeon!!.here().resolved = false; e.state.challenge!!.phase = ChallengePhase.HAZARD
            e.state.challenge!!.hazardMs = if (mark == ChallengeStages.WALL) 2000 else 0
            val before = e.state.dungeon!!.let { it.x to it.y }
            e.choose(Choice("stale move", "move", 0)); assertEquals(before, e.state.dungeon!!.let { it.x to it.y })
            choose(e, "challengeCross"); assertEquals(1, e.state.challenge!!.mistakes)
            assertFalse(e.state.dungeon!!.here().resolved)
            tick(e, 1000)
            while (!ChallengeStages.hazardSafe(mark, e.state.challenge!!.hazardMs)) choose(e, "challengeWait")
            choose(e, "challengeCross")
            assertTrue(e.state.dungeon!!.here().resolved)
            assertEquals(ChallengePhase.ACTIVE, e.state.challenge!!.phase)
            e.choose(Choice("stale", "challengeCross")); assertEquals(1, e.state.challenge!!.mistakes)
        }
    }

    @Test fun threeHazardMistakesFailOnceAndPersistThroughSave() {
        val e = fixture(ChallengeKind.HAZARDS); start(e)
        walk(e, e.state.dungeon!!.cells.indexOfFirst { it.challengeMark == ChallengeStages.BLADE })
        e.state.dungeon!!.here().resolved = false; e.state.challenge!!.phase = ChallengePhase.HAZARD
        repeat(3) {
            e.state.challenge!!.hazardMs = 0; e.state.challenge!!.hazardCooldownMs = 0
            choose(e, "challengeCross")
        }
        assertEquals(ChallengePhase.RESULT, e.state.challenge!!.phase); assertFalse(e.state.challenge!!.success)
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertEquals(3, cold.state.challenge!!.mistakes); assertEquals(0, cold.state.challengeProgress.records.getValue(ChallengeKind.HAZARDS).clears)
    }

    @Test fun stepBudgetIsFiniteAndHasNoWallClockTimeout() {
        val e = fixture(ChallengeKind.STEPS); start(e)
        tick(e, 120000); assertEquals(ChallengePhase.ACTIVE, e.state.challenge!!.phase)
        val m = e.state.dungeon!!; val first = ChallengeStages.path(m)[1]; val spawn = m.y * m.width + m.x
        while (e.state.challenge!!.phase == ChallengePhase.ACTIVE) {
            val target = if (m.y * m.width + m.x == spawn) first else spawn
            val d = (0..3).first { m.x + MapGenerator.dx[it] == target % m.width && m.y + MapGenerator.dy[it] == target / m.width }
            choose(e, "move", d.toLong())
        }
        assertFalse(e.state.challenge!!.success); assertEquals(0, e.state.challenge!!.remainingSteps)
    }

    @Test fun exactLastStepCanWinAndWallsAndTurningDoNotSpendSteps() {
        val e = fixture(ChallengeKind.STEPS); start(e)
        e.state.challenge = e.state.challenge!!.copy(stepLimit = ChallengeStages.path(e.state.dungeon!!).size - 1)
        val map = e.state.dungeon!!
        val wallDir = (0..3).first { map.cell(map.x + MapGenerator.dx[it], map.y + MapGenerator.dy[it]).wall }
        e.choose(Choice("wall", "move", wallDir.toLong()))
        e.turnBack(); assertEquals(0, e.state.challenge!!.steps)
        solve(e); assertEquals(0, e.state.challenge!!.remainingSteps)
    }

    @Test fun saveRestoresActiveMapAndHostAsIndependentMapsWithNoRerolls() {
        val e = fixture(ChallengeKind.LOST); start(e)
        val next = ChallengeStages.path(e.state.dungeon!!)[1]; walk(e, next)
        val before = e.state.dungeon!!.cells.map { it.wall to it.event }
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertEquals(before, cold.state.dungeon!!.cells.map { it.wall to it.event })
        assertEquals(e.state.challenge!!.seed, cold.state.challenge!!.seed)
        assertNotSame(cold.state.dungeon, cold.state.challenge!!.hostDungeon)
        assertEquals(ChallengePhase.ACTIVE, cold.state.challenge!!.phase)
        solve(cold); choose(cold, "challengeLeave")
        val floor = cold.state.floor; cold.enterFloor(floor)
        assertTrue(cold.state.dungeon!!.cells.single { it.event == Event.PORTAL }.resolved)
    }

    @Test fun usedPortalsSurviveDeathAndResetOnInheritanceWhileBestRecordsRemain() {
        val e = fixture(ChallengeKind.LOST); start(e); solve(e); choose(e, "challengeLeave")
        val used = e.state.challengeProgress.usedFloors.toSet()
        e.die(); assertEquals(used, e.state.challengeProgress.usedFloors)
        choose(e, "runContinue")
        val candidate = ItemFactory.create(e.state, Kind.SWORD).also { e.state.inventory.add(it) }
        e.newCycle(candidate.id)
        assertTrue(e.state.challengeProgress.usedFloors.isEmpty()); assertNull(e.state.challengeProgress.seed)
        assertEquals(1, e.state.challengeProgress.records.getValue(ChallengeKind.LOST).clears)
    }

    @Test fun allSevenPracticeEntriesRestoreNormalStateAndNeverGrantRewardsOrRecords() {
        val e = GameEngine(State(gold = 57)); val normal = SaveCodec.encode(e.state)
        choose(e, "training"); choose(e, "trainingChallenges")
        assertEquals(7, e.choices().count { it.action == "trainChallenge" })
        for (kind in ChallengeKind.entries) {
            if (e.state.menu?.type != "trainingChallenges") { e.trainingMenu(); choose(e, "trainingChallenges") }
            choose(e, "trainChallenge", kind.ordinal.toLong()); start(e); solve(e)
            assertTrue(e.state.challenge!!.rewards.isEmpty()); assertEquals(1000, e.state.gold)
            assertTrue(e.state.challengeProgress.records.isEmpty()); assertTrue(e.state.challengeProgress.usedFloors.isEmpty())
        }
        e.exitTraining(); assertEquals(normal, SaveCodec.encode(e.state))
    }

    @Test fun oldSavesDefaultToNoActiveChallengeAndExistingEligibleMapGetsAPortal() {
        val seed = 33L; val floor = ChallengeStages.floors(seed).first()
        val s = State(bootstrapped = true, floor = floor, visitorSeed = seed, mode = Mode.EXPLORE)
        s.dungeon = MapGenerator.generate(s, floor)
        s.dungeon!!.cells.filter { it.event == Event.PORTAL }.forEach { it.event = Event.NONE }
        val json = JsonObject(Json.parseToJsonElement(SaveCodec.encode(s)).jsonObject.filterKeys { it !in listOf("challenge", "challengeProgress") }).toString()
        val e = GameEngine(SaveCodec.decode(json))
        assertNull(e.state.challenge)
        assertEquals(1, e.state.dungeon!!.cells.count { it.event == Event.PORTAL })
        assertTrue(e.state.challengeProgress.records.isEmpty())
    }

    @Test fun malformedOrStalePortalActionsCannotStartASecondChallengeOrReward() {
        val e = fixture(ChallengeKind.LOST)
        assertFalse(ChallengeStages.open(e.state)); e.choose(Choice("stale", "challengeEnter"))
        start(e); e.choose(Choice("stale", "challengeStart"))
        assertEquals(1, e.state.challengeProgress.records.getValue(ChallengeKind.LOST).attempts)
        solve(e); val gold = e.state.gold
        tick(e, 10000); assertEquals(gold, e.state.gold)
        assertFalse(e.openDungeonPanel("help")); assertFalse(e.openLevelUpGrowth())
    }
    @Test fun challengeUsesRealTwoButtonNavigationAndTurningDoesNotConsumeAStep() {
        for (kind in ChallengeKind.entries) {
            val e = fixture(kind); start(e)
            val before = e.state.dungeon!!.let { it.x to it.y }
            val button = e.controls.buttons().indexOfFirst { it.choice?.action == "move" }
            assertTrue(button >= 0); e.press(button)
            assertNotEquals(before, e.state.dungeon!!.let { it.x to it.y })
            assertEquals(1, e.state.challenge!!.steps)
            if (e.state.challenge!!.autoMoving) choose(e, "challengeStop")
            val direction = e.state.dungeon!!.direction
            e.turnBack()
            assertEquals((direction + 2) % 4, e.state.dungeon!!.direction)
            assertEquals(1, e.state.challenge!!.steps)
            assertEquals(Mode.CHALLENGE, e.state.mode)
        }
    }

    @Test fun ordinaryTransitionsCannotReplaceAnActiveChallengeAndDeathFailsItSafely() {
        val e = fixture(ChallengeKind.LOST); start(e)
        val map = e.state.dungeon; val floor = e.state.floor; val inventory = e.state.inventory.toList()
        e.enterFloor(50); e.startBattle(true); e.beginInheritance(); e.newCycle(e.state.inventory.first().id)
        assertSame(map, e.state.dungeon); assertEquals(floor, e.state.floor); assertNull(e.state.enemy)
        assertEquals(1, e.state.cycle); assertEquals(Mode.CHALLENGE, e.state.mode)
        e.die(); assertEquals(ChallengePhase.RESULT, e.state.challenge!!.phase)
        assertFalse(e.state.challenge!!.success); assertEquals(inventory, e.state.inventory)
        choose(e, "challengeLeave"); assertNull(e.state.challenge)
        assertEquals(Mode.EXPLORE, e.state.mode); assertEquals(inventory, e.state.inventory)
    }

    private fun corridor(kind: ChallengeKind = ChallengeKind.LOST, marker: Int? = null): GameEngine {
        val e = fixture(kind); start(e)
        val m = Dungeon(9, 9, MutableList(81) { Cell() }, x = 1, y = 1, direction = 1)
        for ((x,y) in listOf(1 to 1, 2 to 1, 3 to 1, 3 to 2, 3 to 3, 2 to 3, 1 to 3, 4 to 3, 5 to 3))
            m.cell(x,y).wall = false
        m.cell(5,3).event = Event.EXIT
        if (marker != null) { m.cell(3,1).event = Event.CHALLENGE; m.cell(3,1).challengeMark = marker }
        e.state.dungeon = m; e.controls.reset()
        return e
    }

    private fun go(e: GameEngine, direction: Int = 1) =
        e.choose(e.choices().first { it.action == "move" && it.argument == direction.toLong() })

    @Test fun everyChallengeAutoFollowsCornersAndStopsAtTheFirstJunction() {
        for (kind in ChallengeKind.entries) {
            val e = corridor(kind); val m = e.state.dungeon!!
            go(e); assertEquals(1, e.state.challenge!!.steps)
            tick(e, 599); assertEquals(2, m.x); assertEquals(1, m.y)
            tick(e, 1); assertEquals(3, m.x); assertEquals(1, m.y); assertEquals(2, m.direction)
            tick(e, 1200); assertEquals(3, m.x); assertEquals(3, m.y)
            assertFalse(e.state.challenge!!.autoMoving); assertEquals(4, e.state.challenge!!.steps)
            tick(e, 600); assertEquals(4, e.state.challenge!!.steps)
            assertEquals(Mode.CHALLENGE, e.state.mode)
        }
    }

    @Test fun autoMovementStopsAtAltarsBraziersAndAllThreeHazards() {
        for ((kind, marker) in listOf(ChallengeKind.SEALS to 1, ChallengeKind.TORCH to ChallengeStages.BRAZIER,
            ChallengeKind.HAZARDS to ChallengeStages.BLADE, ChallengeKind.HAZARDS to ChallengeStages.FALLING,
            ChallengeKind.HAZARDS to ChallengeStages.WALL)) {
            val e = corridor(kind, marker); go(e); tick(e, 600)
            assertEquals(3, e.state.dungeon!!.x); assertEquals(1, e.state.dungeon!!.y)
            assertFalse(e.state.challenge!!.autoMoving)
            assertEquals(if (kind == ChallengeKind.HAZARDS) ChallengePhase.HAZARD else ChallengePhase.ACTIVE, e.state.challenge!!.phase)
            assertTrue(e.choices().any { it.action == if (kind == ChallengeKind.HAZARDS) "challengeCross" else "challengeUse" })
        }
    }

    @Test fun stopButtonKeepsItsInputTokenAcrossTilesAndRejectsStaleMoves() {
        val e = corridor(); go(e)
        assertEquals(listOf("이동 중", "멈추기"), e.controls.buttons().map { it.label })
        val token = e.controls.inputToken(); tick(e, 600)
        assertEquals(token, e.controls.inputToken())
        val before = e.state.challenge!!.steps
        e.choose(Choice("stale", "move", 2)); e.turnBack()
        assertEquals(before, e.state.challenge!!.steps); assertEquals(2, e.state.dungeon!!.direction)
        e.press(1); assertFalse(e.state.challenge!!.autoMoving)
        tick(e, 1200); assertEquals(before, e.state.challenge!!.steps)
    }

    @Test fun pauseStopsAutoMovementAndRestoredMovementRetainsItsStepClock() {
        val e = corridor(); go(e); tick(e, 200)
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertTrue(cold.state.challenge!!.autoMoving)
        tick(cold, 399); assertEquals(1, cold.state.challenge!!.steps)
        tick(cold, 1); assertEquals(2, cold.state.challenge!!.steps)
        cold.pause(); val position = cold.state.dungeon!!.let { it.x to it.y }
        tick(cold, 1000); choose(cold, "challengeResume"); tick(cold, 1000)
        assertEquals(position, cold.state.dungeon!!.let { it.x to it.y })
        assertFalse(cold.state.challenge!!.autoMoving)
    }

    @Test fun automaticStepsRespectExactBudgetAndExitWinsOnTheLastStep() {
        for (exit in listOf(false, true)) {
            val e = corridor(ChallengeKind.STEPS)
            e.state.challenge = e.state.challenge!!.copy(stepLimit = 2)
            if (exit) e.state.dungeon!!.cell(3,1).event = Event.EXIT
            go(e); tick(e, 600)
            assertEquals(ChallengePhase.RESULT, e.state.challenge!!.phase)
            assertEquals(exit, e.state.challenge!!.success); assertEquals(2, e.state.challenge!!.steps)
            assertFalse(e.state.challenge!!.autoMoving)
        }
    }

    @Test fun failureFreezesTheActualLastTileAndDirectionAcrossSaveAndReload() {
        val e = corridor(); go(e)
        e.state.challenge!!.elapsedMs = 89900
        tick(e, 100)
        val m = e.state.dungeon!!
        assertEquals(2 to 1, m.x to m.y); assertEquals(1, m.direction)
        assertEquals(ChallengePhase.RESULT, e.state.challenge!!.phase)
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        val frozen = cold.state.dungeon!!.copy(cells = cold.state.dungeon!!.cells.map { it.copy() }.toMutableList())
        tick(cold, 5000); cold.choose(Choice("stale", "move", 1))
        assertEquals(frozen, cold.state.dungeon!!)
        assertFalse(cold.state.challenge!!.autoMoving)
    }

    @Test fun collapsedCorridorsRemainAvailableForTheFailureMapAfterReload() {
        val e = corridor(ChallengeKind.COLLAPSE); go(e); tick(e, 1800)
        val spawn = 10
        tick(e, 1200)
        assertTrue(spawn in e.state.challenge!!.collapsedCells)
        assertTrue(e.state.dungeon!!.cells[spawn].wall)
        e.navigateBack(); choose(e, "challengeGiveUp")
        val cold = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertTrue(spawn in cold.state.challenge!!.collapsedCells)
        assertEquals(3 to 3, cold.state.dungeon!!.let { it.x to it.y })
        assertEquals(ChallengePhase.RESULT, cold.state.challenge!!.phase)
    }

}
