package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class StoryTest {
    private fun drain(s: State) { while(Story.current(s) != null) Story.skip(s) }
    private fun confirmBoss(e: GameEngine, floor: Int) {
        e.enterFloor(floor)
        e.state.dungeon!!.bossCleared = true
        e.state.mode = Mode.VICTORY
        e.state.battleResult = BattleResult(Enemy("문지기",1.0,0.0,0.0,0.0,0.0,boss=true),0,0,emptyList(),Mode.FLOOR_CLEAR)
        e.choose(Choice("계속","battleContinue"))
    }

    @Test fun freshGameGetsOnePrologueAndNoFutureSpoilers() {
        val s=GameEngine().state
        assertEquals(listOf("prologue"),s.story.unlocked)
        assertEquals("prologue",Story.current(s)?.id)
        assertFalse(Story.history(s).any { it.id == "revelation" })
        assertTrue(Story.objective(s).contains("10층"))
        Story.initialize(s,false)
        assertEquals(1,s.story.queue.size)
    }
    @Test fun floorCadenceAlternatesTwoAndThreeThroughFifty() {
        val rungs=listOf(0)+Story.cadence
        assertTrue(rungs.zipWithNext().all { (a,b) -> b-a in 2..3 })
        assertEquals(50,rungs.last())
        val e=GameEngine();drain(e.state)
        for(floor in Story.cadence) {
            if(floor%10==0) { confirmBoss(e,floor) }
            else e.enterFloor(floor)
            assertTrue("Narrative missing at $floor",e.state.story.queue.isNotEmpty())
            drain(e.state)
        }
    }
    @Test fun secondJourneyAlsoHasOneReflectionAtEveryCadenceFloor() {
        val e=GameEngine(State(cycle=2));drain(e.state)
        for(floor in Story.cadence) {
            if(floor%10==0) confirmBoss(e,floor) else e.enterFloor(floor)
            assertTrue("Second journey missing $floor",e.state.story.queue.isNotEmpty())
            drain(e.state)
        }
    }
    @Test fun revisitsDoNotRepeatReadScenesAndJournalOnlyContainsUnlockedStories() {
        val e=GameEngine();drain(e.state);e.enterFloor(2);drain(e.state)
        val history=Story.history(e.state).map { it.id }
        e.enterFloor(2)
        assertNull(Story.current(e.state))
        assertEquals(history,Story.history(e.state).map { it.id })
        assertFalse(history.contains("contract"))
    }
    @Test fun bossStoryArrivesOnlyAfterRewardConfirmation() {
        val e=GameEngine();drain(e.state);e.enterFloor(10)
        e.startBattle(true)
        assertFalse("contract" in e.state.story.unlocked)
        confirmBoss(e,10)
        assertEquals("contract",Story.current(e.state)?.id)
        assertEquals(Mode.FLOOR_CLEAR,e.state.mode)
        assertTrue(Story.objective(e.state).contains("50층"))
    }
    @Test fun firstAndSecondFinalBossHaveSeparateSetupsAndReveals() {
        val e=GameEngine();drain(e.state);e.enterFloor(50);e.startBattle(true)
        assertEquals("마지막 문지기",e.state.enemy!!.name)
        assertEquals("guardian",Story.current(e.state)?.id)
        drain(e.state);confirmBoss(e,50)
        assertEquals("revelation",Story.current(e.state)?.id)
        e.state.cycle=2;drain(e.state);e.enterFloor(50);e.startBattle(true)
        assertEquals("회수자의 문지기",e.state.enemy!!.name)
        assertEquals("possessed",Story.current(e.state)?.id)
        assertEquals("possessed-4",MonsterCatalog.describe(e.state.enemy!!,50).id)
        drain(e.state);confirmBoss(e,50)
        assertEquals("second_victory",Story.current(e.state)?.id)
    }
    @Test fun inheritanceContinuesIntoConfessionWithoutLosingPreviousImprints() {
        val e=GameEngine();drain(e.state)
        e.beginInheritance()
        assertEquals("inheritance",Story.current(e.state)?.id)
        val chosen=e.state.inventory.first { it.equippable }
        drain(e.state);e.newCycle(chosen.id)
        assertEquals(2,e.state.cycle)
        assertTrue(e.state.inventory.any { it.id==chosen.id && it.inherited })
        assertEquals("confession",Story.current(e.state)?.id)
        assertTrue(Story.objective(e.state).contains("회수자"))
    }
    @Test fun resurrectionWaitsUntilRunResultsHaveBeenReadAndOnlyAppearsOnce() {
        val e=GameEngine();drain(e.state);e.enterFloor(1);e.die()
        assertNull(Story.current(e.state))
        assertTrue("resurrection" in e.state.story.queue)
        e.choose(Choice("준비","runContinue"))
        assertEquals("resurrection",Story.current(e.state)?.id)
        drain(e.state);e.enterFloor(1);e.die();e.choose(Choice("준비","runContinue"))
        assertFalse("resurrection" in e.state.story.queue)
    }
    @Test fun legacyMidCombatMigrationPreservesItemsAndDefersRecapUntilSurface() {
        val original=GameEngine();original.enterFloor(30);original.startBattle(true)
        val s=SaveCodec.decode(SaveCodec.encode(original.state)).copy(story=StoryProgress())
        val ids=s.inventory.map { it.id };val enemy=s.enemy;val rng=s.rng
        Story.initialize(s,false)
        assertNull(Story.current(s))
        assertEquals(ids,s.inventory.map { it.id });assertEquals(enemy,s.enemy);assertEquals(rng,s.rng)
        assertFalse("warning30" in s.story.unlocked)
        assertFalse("revelation" in s.story.unlocked)
        assertEquals("recap_contract",s.story.recap)
        s.mode=Mode.SURFACE;Story.onSurface(s)
        assertEquals("recap_contract",Story.current(s)?.id)
        Story.onSurface(s);assertEquals(1,s.story.queue.size)
    }
    @Test fun legacyClearedSaveGetsTruthRecapAndPreservesAllProgress() {
        val s=State(bootstrapped=true,cycle=3,clears=2,highest=50,level=17,bank=777)
        val e=GameEngine(s)
        assertEquals(17,s.level);assertEquals(777,s.bank);assertEquals(3,s.cycle)
        assertEquals("recap_revealed",Story.current(s)?.id)
        assertTrue(Story.history(s).any { it.id=="confession" })
        assertFalse(Story.history(s).any { it.id=="second_victory" })
    }
    @Test fun automaticScenePageAndQueueSurviveSaveReloadAndUnknownIdsAreRemoved() {
        val e=GameEngine();Story.advance(e.state)
        val restored=GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertEquals(1,restored.state.story.page)
        assertEquals("prologue",Story.current(restored.state)?.id)
        restored.state.story.queue.add(0,"missing");restored.state.story.page=99
        Story.initialize(restored.state,false)
        assertEquals(0,restored.state.story.page)
        assertEquals("prologue",Story.current(restored.state)?.id)
    }
    @Test fun narrativeDoesNotConsumeRandomnessAndTrainingDoesNotUnlockRealStories() {
        val e=GameEngine();val before=e.state.rng
        Story.onDeath(e.state);Story.onFloor(e.state);Story.beforeBoss(e.state)
        assertEquals(before,e.state.rng)
        val test=State(debug=true,training=true,floor=50)
        Story.initialize(test,true);Story.beforeBoss(test);Story.afterBoss(test)
        assertTrue(test.story.unlocked.isEmpty());assertNull(Story.current(test))
    }
}
