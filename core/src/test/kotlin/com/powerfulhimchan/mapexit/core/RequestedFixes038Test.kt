package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class RequestedFixes038Test {
    private val fighter=Stats(hp=10000.0,attack=100.0,defense=35.0,agility=120.0,luck=100.0)
    private fun battle(seed: Long, intent: EnemyIntent=EnemyIntent.STRIKE, hp: Double=10000.0, enemyHp: Double=10000.0) =
        GameEngine(State(bootstrapped=true,rng=seed,hp=hp,base=fighter,mode=Mode.COMBAT_DEFEND,
            enemy=Enemy("시험 몬스터",10000.0,enemyHp,100.0,20.0,20.0,intent=intent)))
    private fun counterSeed(): Long = (1L..100000L).first { seed ->
        val s=State(rng=seed);s.random();val block=s.random();val counter=s.random()
        block>.5 && counter<Luck.counter(fighter) && run { s.random();s.random()<.9 }
    }

    @Test fun guardCountersUseNormalAttackRulesAndLeaveTheNextPlayerTurnAvailable() {
        val seed=counterSeed()
        val rolls=State(rng=seed);repeat(3){rolls.random()}
        val normal=battle(rolls.rng);normal.state.mode=Mode.COMBAT_ATTACK
        normal.state.base=fighter.copy(agility=0.0) // Only suppress the separately rolled double attack.
        normal.attack(false)
        val e=battle(seed);e.defend(false)
        val counter=e.state.lastImpact!!.counterHit!!
        assertEquals(normal.state.lastImpact!!.amount,counter.amount)
        assertEquals(normal.state.lastImpact!!.critical,counter.critical)
        assertEquals(normal.state.lastImpact!!.luckyAttack,counter.luckyAttack)
        assertEquals(10000.0-counter.damage,e.state.enemy!!.hp,.00001)
        assertEquals(Mode.COMBAT_ATTACK,e.state.mode)
        assertEquals(1,e.state.enemy!!.round)
        assertTrue(e.state.message.contains("자동 반격"))
        val cold=SaveCodec.decode(SaveCodec.encode(e.state))
        assertEquals(e.state.enemy,cold.enemy);assertEquals(counter,cold.lastImpact!!.counterHit)
        val before=SaveCodec.encode(e.state);e.defend(false)
        assertEquals(before,SaveCodec.encode(e.state))
    }

    @Test fun dodgeRestAndLethalIncomingDamageNeverCounterAndCounterKillsRewardOnlyOnce() {
        val seed=counterSeed()
        for(i in 1L..300L) { val e=battle(i);e.defend(true);assertNull(e.state.lastImpact!!.counterHit) }
        val rest=battle(seed,EnemyIntent.RECOVER);rest.defend(false)
        assertNull(rest.state.lastImpact);assertEquals(seed,rest.state.rng)
        val dead=battle(seed,hp=.001);dead.defend(false)
        assertEquals(Mode.RUN_RESULT,dead.state.mode);assertNull(dead.state.lastImpact?.counterHit)
        val win=battle(seed,enemyHp=.1);win.defend(false)
        assertEquals(Mode.VICTORY,win.state.mode);assertEquals(1,win.state.runKills)
        val saved=SaveCodec.encode(win.state);repeat(5){win.defend(false)}
        assertEquals(saved,SaveCodec.encode(win.state))
        assertEquals(0.0,Luck.counter(fighter.copy(luck=0.0)),0.0)
        assertTrue(Luck.counter(fighter)>Luck.counter(fighter.copy(agility=30.0)))
        assertTrue(Luck.counter(fighter)>Luck.counter(fighter.copy(luck=20.0)))
        assertTrue(Luck.counter(fighter.copy(agility=1e9,luck=1e9))<.03)
    }

    @Test fun generatedFloorAveragesMatchRequestedRatiosWithoutRemovingMandatoryEvents() {
        for(floor in listOf(1,30,50)) {
            val totals=mutableMapOf(Event.MONSTER to 0,Event.CHEST to 0,Event.TRAP to 0)
            for(seed in 1L..1500L) {
                val s=State(bootstrapped=true,rng=seed*7919)
                val map=MapGenerator.generate(s,floor)
                totals.keys.toList().forEach { event -> totals[event]=totals.getValue(event)+map.cells.count { it.event==event } }
                assertEquals(1,map.cells.count { it.event==Event.EXIT })
                assertEquals(2,map.cells.count { it.event==Event.HEAL })
                assertEquals(if(floor%5==0)1 else 0,map.cells.count { it.event==Event.ELITE })
                val dist=MapGenerator.distances(map,1,1)
                assertTrue(map.cells.indices.filter { !map.cells[it].wall }.all { dist[it]>=0 })
                val restored=SaveCodec.decode(SaveCodec.encode(s.copy(dungeon=map)))
                assertEquals(map,restored.dungeon)
            }
            assertEquals((7+floor/15)*.8,totals.getValue(Event.MONSTER)/1500.0,.05)
            assertEquals(1.4,totals.getValue(Event.CHEST)/1500.0,.05)
            assertEquals(2.1,totals.getValue(Event.TRAP)/1500.0,.04)
        }
    }

    @Test fun scrollResultSurvivesReloadAndAcknowledgmentCannotSpendAnotherBook() {
        val gear=Gear(1,Kind.SPEAR,"장창 (+2)",2,flat=Stats.zero().copy(attack=40.0,defense=-5.0,crit=.1),inherited=true)
        val s=State(bootstrapped=true,mode=Mode.MENU,menu=Menu("upgradeTargets",2,Mode.EXPLORE),
            inventory=mutableListOf(gear,Gear(2,Kind.SCROLL,"강화서"),Gear(3,Kind.SCROLL,"강화서")),
            equipment=mutableMapOf(Slot.LEFT to 1L,Slot.RIGHT to 1L))
        assertTrue(Forge.useScroll(s,2,1))
        assertEquals(gear,s.upgradeResult!!.before)
        assertEquals(s.inventory.first { it.id==1L },s.upgradeResult!!.after)
        assertEquals(60.0,s.upgradeResult!!.after.flat.attack,.0)
        assertEquals(-5.0,s.upgradeResult!!.after.flat.defense,.0)
        assertEquals("upgradeResult",s.menu!!.type)
        val e=GameEngine(SaveCodec.decode(SaveCodec.encode(s)))
        assertEquals(s.upgradeResult,e.state.upgradeResult)
        val noRepeat=SaveCodec.encode(e.state)
        e.choose(Choice("stale","upgradeTarget",1));assertEquals(noRepeat,SaveCodec.encode(e.state))
        e.navigateBack();assertNull(e.state.upgradeResult);assertEquals("inventory",e.state.menu!!.type)
        assertEquals(Mode.EXPLORE,e.state.menu!!.returnMode)
        assertEquals(1,e.state.inventory.count { it.kind==Kind.SCROLL })
    }

    @Test fun oldItemNamesConvertOnceWithoutChangingStatsAndHandLabelsMatchEquipRules() {
        val gear=Gear(1,Kind.CLAW,"클로 +2",2,flat=Stats.zero().copy(attack=12.0,agility=3.0))
        val source=State(bootstrapped=true,inventory=mutableListOf(gear),equipment=mutableMapOf(Slot.RIGHT to 1L))
        val next=SaveCodec.decode(SaveCodec.encode(source))
        assertEquals(gear.copy(name="클로 (+2)"),next.inventory.single())
        assertEquals(Equipment.stats(source),Equipment.stats(next))
        assertEquals(next,SaveCodec.decode(SaveCodec.encode(next)))
        for(kind in listOf(Kind.SWORD,Kind.SPEAR,Kind.HAMMER,Kind.CLAW,Kind.WHIP)) {
            val g=ItemFactory.create(State(),kind)
            assertTrue(g.name.endsWith("(+${g.tier})"))
            assertTrue(g.summary().contains(if(g.twoHanded)"양손용" else "한손용"))
        }
        val mixed=source.copy(inventory=mutableListOf(gear,gear.copy(id=2,name="클로 (+2)")))
        assertEquals(1,Forge.pairs(mixed).size)
    }
}
