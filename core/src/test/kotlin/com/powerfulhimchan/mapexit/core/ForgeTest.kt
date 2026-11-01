package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class ForgeTest {
    @Test fun identicalFinalStatsStillStackWhileDifferentNecklacePowersRemainSeparate() {
        val upgraded=Forge.upgraded(Gear(1,Kind.BODY,"갑옷 +2",2,Stats.zero().copy(defense=8.0,hp=30.0),price=70))
        val natural=upgraded.copy(id=2,refinements=0)
        assertEquals(1,InventoryStacks.groups(listOf(upgraded,natural)).size)
        val charm=Gear(3,Kind.NECKLACE,"탐색의 목걸이 +3",3,charm=Charm.MAP)
        assertEquals(2,InventoryStacks.groups(listOf(charm,charm.copy(id=4,refinements=1))).size)
    }
    private fun fixture(): GameEngine {
        val m=Dungeon(9,9,MutableList(81){Cell()},4,4,0)
        m.here().wall=false; m.here().event=Event.FORGE; m.cell(4,3).wall=false
        val gear=Gear(1,Kind.BODY,"시험 갑옷 +2",2,Stats.zero().copy(defense=8.0,hp=30.0))
        return GameEngine(State(bootstrapped=true,floor=20,dungeon=m,mode=Mode.EXPLORE,
            inventory= mutableListOf(gear,gear.copy(id=2)),equipment= mutableMapOf(Slot.BODY to 1L),nextId=10))
    }
    private fun confirm(e:GameEngine) {
        e.choose(e.choices().first { it.action=="forge" })
        e.choose(e.choices().first { it.action=="forgePair" })
    }
    @Test fun onlyTwentyAndFortyContainAReachableForgeAndOldMapsAreUpgradedOnce() {
        for(floor in listOf(1,19,20,21,39,40,41,50)) {
            val e=GameEngine(); e.enterFloor(floor); val m=e.state.dungeon!!
            val sites=m.cells.withIndex().filter { it.value.event==Event.FORGE }
            assertEquals(if(floor%20==0)1 else 0,sites.size)
            for(site in sites) assertTrue(MapGenerator.distances(m,m.x,m.y)[site.index]>=0)
            if(floor==20) {
                sites.single().value.event=Event.NONE
                val cold=GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
                assertEquals(1,cold.state.dungeon!!.cells.count { it.event==Event.FORGE })
                Forge.ensure(cold.state); assertEquals(1,cold.state.dungeon!!.cells.count { it.event==Event.FORGE })
            }
        }
    }
    @Test fun twoPlusTwoProducePlusThreeThenTwoPlusThreeProducePlusFour() {
        val e=fixture();confirm(e);e.state.rng=1
        assertTrue(Forge.combine(e.state,1));val three=e.state.inventory.single()
        assertEquals(3,three.tier);assertEquals(1L,e.state.equipment[Slot.BODY]);assertTrue(three.flat.defense>8)
        assertFalse(Forge.combine(e.state,1))
        e.state.inventory.add(three.copy(id=3))
        e.choose(e.choices().first { it.action=="forgePair" });e.state.rng=1
        assertTrue(Forge.combine(e.state,1));assertEquals(4,e.state.inventory.single().tier)
        val cold=GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertEquals(4,cold.state.inventory.single().tier);assertEquals(1L,cold.state.equipment[Slot.BODY])
    }
    @Test fun failureOnlyConsumesMaterialAndNeverDowngradesOrConsumesInheritance() {
        val e=fixture();e.state.inventory[0].inherited=true;confirm(e);e.state.rng=0
        val before=e.state.inventory[0]
        assertFalse(Forge.combine(e.state,1));assertEquals(listOf(before),e.state.inventory)
        assertTrue(e.state.inventory.single().inherited);assertEquals(1L,e.state.equipment[Slot.BODY])
        assertTrue(e.state.message.contains("합성 실패"));assertEquals("forge",e.state.menu!!.type)
    }
    @Test fun mismatchedGradesVariantsAndBossOptionsCannotBeMaterials() {
        val e=fixture();val item=e.state.inventory[1]
        for(other in listOf(item.copy(tier=3,name="시험 갑옷 +3"),item.copy(variantId="other"),item.copy(affixes=listOf(WeaponAffix(Stat.HP,20.0))))) {
            e.state.inventory[1]=other;assertTrue(Forge.pairs(e.state).isEmpty())
        }
        e.state.inventory[1]=item.copy(inherited=true);e.state.inventory[0].inherited=true
        assertTrue(Forge.pairs(e.state).isEmpty())
    }
    @Test fun staleAndRemoteForgeCallsDoNotConsumeItemsOrRollRng() {
        val e=fixture();val before=SaveCodec.encode(e.state)
        assertFalse(Forge.combine(e.state,1));assertEquals(before,SaveCodec.encode(e.state))
        confirm(e);e.state.floor=19;val invalid=SaveCodec.encode(e.state)
        assertFalse(Forge.combine(e.state,1));assertEquals(invalid,SaveCodec.encode(e.state))
    }
    @Test fun scrollUpgradesOneRealEquippedItemAndPreservesPenaltiesAndBothHandSlots() {
        val g=Gear(1,Kind.SPEAR,"장창 +2",2,Stats.zero().copy(attack=40.0,defense=-5.0),inherited=true)
        val s=State(bootstrapped=true,inventory=mutableListOf(g,Gear(2,Kind.SCROLL,"강화서"),Gear(3,Kind.SCROLL,"강화서")),
            equipment=mutableMapOf(Slot.LEFT to 1L,Slot.RIGHT to 1L),mode=Mode.MENU,menu=Menu("upgradeTargets",2))
        assertTrue(Forge.useScroll(s,2,1));assertEquals(2,s.inventory.size)
        val upgraded=s.inventory.first { it.id==1L };assertEquals(3,upgraded.tier);assertTrue(upgraded.inherited)
        assertEquals(-5.0,upgraded.flat.defense,.0);assertTrue(upgraded.flat.attack>g.flat.attack)
        assertEquals(setOf(1L),s.equipment.values.toSet());assertFalse(Forge.useScroll(s,2,1))
        val cold=SaveCodec.decode(SaveCodec.encode(s));assertEquals(upgraded,cold.inventory.first { it.id==1L })
    }
    @Test fun invalidScrollTargetDoesNotSpendTheBookAndNecklacesActuallyGainEffect() {
        val s=State(bootstrapped=true,inventory=mutableListOf(Gear(1,Kind.SCROLL,"강화서")),mode=Mode.MENU,menu=Menu("upgradeTargets",1))
        assertFalse(Forge.useScroll(s,1,1));assertEquals(1,s.inventory.size)
        val necklace=Gear(2,Kind.NECKLACE,"상인의 목걸이 +2",2,charm=Charm.MERCHANT)
        s.inventory.add(necklace);s.equipment[Slot.NECK]=2
        val price=Equipment.buyPrice(s,Gear(3,Kind.BODY,"시험",price=100))
        assertTrue(Forge.useScroll(s,1,2));assertTrue(Equipment.buyPrice(s,Gear(3,Kind.BODY,"시험",price=100))<price)
    }
    @Test fun questAchievementAndRareMerchantProvideBooksAndBooksCannotBeEquipped() {
        val e=fixture();e.state.dungeon!!.here().event=Event.NPC;e.state.dungeon!!.here().npcRole=NpcRole.QUEST
        e.state.quest=Quest("시험","kill",1,1);e.state.mode=Mode.ENCOUNTER;e.state.encounter=Event.NPC
        e.choose(e.choices().first { it.action=="claim" });assertEquals(1,e.state.inventory.count { it.kind==Kind.SCROLL })
        e.choose(Choice("stale","claim"));assertEquals(1,e.state.inventory.count { it.kind==Kind.SCROLL })
        val s=State(bootstrapped=true,mode=Mode.MENU,menu=Menu("achievements"))
        s.achievements.unlocked.add("quests-5")
        assertTrue(Achievements.claim(s,Achievements.all.indexOfFirst { it.id=="quests-5" }))
        assertEquals(1,s.inventory.count { it.kind==Kind.SCROLL })
        assertTrue(Equipment.allowedSlots(s.inventory.single()).isEmpty())
        val offers=(1L..500L).count { seed -> val shop=State(bootstrapped=true,rng=seed);ItemFactory.rareScroll(shop);shop.shop.any { it.kind==Kind.SCROLL } }
        assertTrue(offers in 1..40)
    }
}
