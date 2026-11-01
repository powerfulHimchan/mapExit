package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class LuckyCombatTest {
    private fun battle(seed:Long, strong:Boolean=false, hp:Double=10000.0):GameEngine {
        val e=GameEngine(State(bootstrapped=true,base=Stats(attack=40.0,agility=220.0,luck=180.0)))
        e.enterFloor(1);e.startBattle(false);e.state.enemy=e.state.enemy!!.copy(hp=hp,maxHp=hp,intent=EnemyIntent.STRIKE)
        e.state.rng=seed;e.attack(strong);return e
    }
    @Test fun normalAttackCanStrikeExactlyTwiceWithSeparateDamageAndOneEnemyTurn() {
        val e=(1L..500L).asSequence().map { battle(it) }.first { it.state.lastImpact!!.strikes.size==2 }
        val impact=e.state.lastImpact!!
        assertEquals(2,impact.strikes.size);assertEquals(1L,impact.serial)
        assertEquals(10000-e.state.enemy!!.hp,impact.strikes.sumOf { it.damage },.00001)
        assertEquals(Mode.COMBAT_DEFEND,e.state.mode);assertTrue(impact.tactic.contains("2회"))
        assertEquals(impact,SaveCodec.decode(SaveCodec.encode(e.state)).lastImpact)
        e.attack(false);assertEquals(impact.serial,e.state.impactSequence)
    }
    @Test fun strongAttacksNeverChainAndFirstHitKillsGrantVictoryOnlyOnce() {
        for(seed in 1L..100L) assertTrue(battle(seed,strong=true).state.lastImpact!!.strikes.isEmpty())
        val e=battle(1,hp=.01)
        assertEquals(Mode.VICTORY,e.state.mode);assertTrue(e.state.lastImpact!!.strikes.isEmpty())
        val gold=e.state.gold;e.attack(false);assertEquals(gold,e.state.gold)
        assertEquals(1,e.state.bestiary.values.sumOf { it.defeats })
    }
    @Test fun lowAgilityAndZeroLuckCannotChainAndChanceRemainsBounded() {
        assertEquals(.0,Luck.doubleAttack(Stats(agility=39.0,luck=1000.0)),.0)
        assertEquals(.0,Luck.doubleAttack(Stats(agility=200.0,luck=0.0)),.0)
        assertTrue(Luck.doubleAttack(Stats(agility=200.0,luck=100.0))>Luck.doubleAttack(Stats(agility=60.0,luck=10.0)))
        for(luck in listOf(.0,10.0,100.0,100000.0)) {
            assertTrue(Luck.good(.3,luck) in .3.. .95);assertTrue(Luck.bad(.1,luck) in .01.. .1)
            assertTrue(Luck.doubleAttack(Stats(agility=100000.0,luck=luck)) in .0.. .30)
        }
    }
    @Test fun rareEnemyFirstTurnAllowsDefenseAndSurvivesSaveWithoutFreeDamage() {
        val e=(1L..2000L).asSequence().map { seed ->
            val game=GameEngine(State(bootstrapped=true,base=Stats(agility=0.0)))
            game.enterFloor(1);game.state.rng=seed;game.startBattle(false,ambush=true);game
        }.first { it.state.mode==Mode.COMBAT_DEFEND }
        assertEquals(listOf("guard","dodge"),e.choices().map { it.action })
        assertEquals(360.0,e.state.hp,.0);assertTrue(e.state.message.contains("선제공격"))
        val cold=GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        cold.defend(false);assertEquals(Mode.COMBAT_ATTACK,cold.state.mode)
        assertTrue(Luck.ambush(Stats(agility=150.0,luck=100.0),10.0)<Luck.ambush(Stats(agility=0.0),10.0))
        assertTrue(Luck.ambush(Stats(agility=100000.0,luck=100000.0),10.0)>0)
    }
    @Test fun luckImprovesGiftOddsAndOldCachedNonGiftCanReceiveLuckyThanksOnce() {
        val baseline=(1L..3000L).count { NpcInteractions.giftFor(it,7) }
        val lucky=(1L..3000L).count { NpcInteractions.giftFor(it,7,200.0) }
        assertTrue(baseline in 700..1100);assertTrue(lucky>baseline)
        val seed=(1L..3000L).first { !NpcInteractions.giftFor(it,7) && NpcInteractions.giftFor(it,7,200.0) }
        val m=Dungeon(9,9,MutableList(81){Cell()},4,4,0)
        m.here().apply { wall=false;event=Event.NPC;npcRole=NpcRole.BEGGAR;npcGift=false }
        val s=State(bootstrapped=true,visitorSeed=seed,floor=7,dungeon=m,gold=20,base=Stats(luck=200.0),mode=Mode.ENCOUNTER,encounter=Event.NPC)
        assertFalse(NpcInteractions.description(s).contains("30%"));assertFalse(NpcInteractions.description(s).contains("답례"))
        assertTrue(NpcInteractions.use(s,"npcDonate").success);assertEquals(10,s.gold)
        assertEquals(1,s.inventory.count { it.kind==Kind.POTION })
        assertFalse(NpcInteractions.use(s,"npcDonate").success);assertEquals(1,s.inventory.size)
    }
    @Test fun waterAlwaysFallsOppositeTheSafeSideIncludingFinalSuccessAndFailure() {
        for(side in 0..1) for(success in listOf(false,true)) {
            val t=TrapState(0,listOf(side),2000,0,.0,.0,1,0,0,phase=TrapPhase.ACTIVE,elapsedMs=500)
            assertEquals(if(side==0) .75f else .27f,ObstacleLayout.cauldronWaterLane(t))
            Traps.tap(t,if(success) side else 1-side)
            assertEquals(success,t.success);assertEquals(if(side==0) .75f else .27f,ObstacleLayout.cauldronWaterLane(t))
            t.elapsedMs=899;assertEquals(.5f,ObstacleLayout.sequenceHeroCenter(t),.001f)
        }
    }
}
