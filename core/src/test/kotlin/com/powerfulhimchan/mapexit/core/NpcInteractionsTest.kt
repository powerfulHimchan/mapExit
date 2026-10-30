package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class NpcInteractionsTest {
    private fun fixture(role: NpcRole, gold: Int = 30, hp: Double = 100.0, gift: Boolean? = null): GameEngine {
        val e = GameEngine(State(gold = gold, hp = hp))
        val map = Dungeon(7,7,MutableList(49) { Cell() },3,3,0)
        map.here().wall=false;map.here().event=Event.NPC;map.here().npcRole=role;map.here().npcGift=gift
        e.state.dungeon=map;e.state.floor=4;e.state.mode=Mode.EXPLORE
        e.choose(Choice("NPC", "encounter", Event.NPC.ordinal.toLong()))
        return e
    }
    private fun choose(e: GameEngine, action: String) = e.choose(e.choices().first { it.action==action })
    @Test fun everyRoleHasItsOwnActionAndSupportersDoNotOfferOrClaimQuests() {
        for (role in NpcRole.entries.filter { it!=NpcRole.QUEST }) {
            val e=fixture(role)
            assertEquals(listOf(role.action,"encounterLeave"),e.choices().map { it.action })
            assertNull(e.state.dungeon!!.here().questOffer)
            val quest=Quest("이전 의뢰","kill",1,progress=1)
            e.state.quest=quest
            assertFalse(e.choices().any { it.action in listOf("questAccept","claim") })
            choose(e,role.action)
            assertEquals(quest,e.state.quest)
        }
    }
    @Test fun donationCostsExactlyTenAndBothGiftOutcomesAreSingleUse() {
        for (gift in listOf(false,true)) {
            val e=fixture(NpcRole.BEGGAR,gift=gift)
            val count=e.state.inventory.count { it.kind==Kind.POTION }
            val action=e.choices().first { it.action=="npcDonate" }
            e.choose(action)
            assertEquals(20,e.state.gold)
            assertEquals(count+if(gift)1 else 0,e.state.inventory.count { it.kind==Kind.POTION })
            assertTrue(e.state.dungeon!!.here().resolved)
            assertEquals(setOf(4),e.state.npcServicesUsed)
            assertEquals(Mode.EXPLORE,e.state.mode)
            e.choose(action)
            assertEquals(20,e.state.gold)
            assertEquals(count+if(gift)1 else 0,e.state.inventory.count { it.kind==Kind.POTION })
        }
    }
    @Test fun insufficientGoldDisablesDonationWithoutConsumingNpcOrItems() {
        for (gold in listOf(0,9)) {
            val e=fixture(NpcRole.BEGGAR,gold=gold,gift=true)
            val items=e.state.inventory.toList();val rng=e.state.rng
            assertFalse(e.controls.buttons().first().enabled)
            assertTrue(NpcInteractions.description(e.state).startsWith("골드가 부족"))
            e.press(0);choose(e,"npcDonate")
            assertEquals(gold,e.state.gold);assertEquals(items,e.state.inventory)
            assertEquals(rng,e.state.rng);assertFalse(e.state.dungeon!!.here().resolved)
            assertTrue(e.state.npcServicesUsed.isEmpty())
            e.state.gold=10
            assertTrue(e.controls.buttons().first().enabled)
            choose(e,"npcDonate");assertEquals(0,e.state.gold)
        }
    }
    @Test fun healerUsesEquippedMaximumAndClampsAtFullHealthWithoutSpendingGoldOrPoints() {
        val e=fixture(NpcRole.HEALER,hp=30.0)
        val ring=Gear(e.state.nextId++,Kind.RING,"수호의 반지",percent=Stats.zero().copy(hp=.25))
        e.state.inventory.add(ring);Equipment.equip(e.state,ring.id,Slot.RING1)
        val maximum=e.stats().hp;val gold=e.state.gold;val points=e.state.points;val base=e.state.base
        choose(e,"npcHeal")
        assertEquals(30+maximum*.20,e.state.hp,.001)
        assertEquals(gold,e.state.gold);assertEquals(points,e.state.points);assertEquals(base,e.state.base)
        val nearly=fixture(NpcRole.HEALER);nearly.state.hp=nearly.stats().hp-1
        choose(nearly,"npcHeal");assertEquals(nearly.stats().hp,nearly.state.hp,.001)
        assertTrue(NpcInteractions.HEAL_RATIO<.45)
    }
    @Test fun healerCannotBeWastedAtFullHpAndRemainsAvailableAfterDeclining() {
        val e=fixture(NpcRole.HEALER,hp=360.0)
        assertFalse(e.controls.buttons().first().enabled)
        assertTrue(NpcInteractions.description(e.state).startsWith("체력이 가득"))
        e.press(0);choose(e,"npcHeal")
        assertFalse(e.state.dungeon!!.here().resolved)
        assertTrue(e.state.npcServicesUsed.isEmpty())
        choose(e,"encounterLeave");e.state.hp=1.0;choose(e,"encounter")
        assertTrue(e.controls.buttons().first().enabled)
        choose(e,"npcHeal");assertEquals(73.0,e.state.hp,.001)
    }
    @Test fun potionIsOneInventoryItemNotAutomaticHealingAndHasAUniqueId() {
        val e=fixture(NpcRole.HERBALIST);val hp=e.state.hp;val items=e.state.inventory.size
        val choice=e.choices().first { it.action=="npcPotion" }
        e.choose(choice)
        assertEquals(hp,e.state.hp,.0);assertEquals(items+1,e.state.inventory.size)
        assertEquals(Kind.POTION,e.state.inventory.last().kind)
        assertEquals(e.state.inventory.size,e.state.inventory.map { it.id }.distinct().size)
        val saved=SaveCodec.encode(e.state);e.choose(choice);assertEquals(saved,SaveCodec.encode(e.state))
    }
    @Test fun declineAndReloadPreserveIdentityAndGiftRegardlessOfTheCombatRandomStream() {
        val e=fixture(NpcRole.BEGGAR)
        val gift=e.state.dungeon!!.here().npcGift
        assertNotNull(gift)
        choose(e,"encounterLeave");assertFalse(e.state.dungeon!!.here().resolved)
        val restored=GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        restored.state.rng=9999999
        choose(restored,"encounter")
        assertEquals(gift,restored.state.dungeon!!.here().npcGift)
        val count=restored.state.inventory.size
        choose(restored,"npcDonate")
        assertEquals(count+if(gift==true)1 else 0,restored.state.inventory.size)
    }
    @Test fun wrongRoleStaleOrOutOfContextActionsCannotMutateResources() {
        val e=fixture(NpcRole.HERBALIST);val saved=SaveCodec.encode(e.state)
        assertFalse(NpcInteractions.use(e.state,"npcDonate").success)
        assertEquals(saved,SaveCodec.encode(e.state))
        choose(e,"encounterLeave");val exploring=SaveCodec.encode(e.state)
        assertFalse(NpcInteractions.use(e.state,"npcPotion").success)
        assertEquals(exploring,SaveCodec.encode(e.state))
    }
    @Test fun generatedRolesKeepTheOriginalThreeOrFourFloorScheduleAndDoNotReroll() {
        val seen=mutableSetOf<NpcRole>()
        for(seed in 1L..12L) {
            val s=State(bootstrapped=true,rng=seed,visitorSeed=seed)
            val scheduled=VisitorSchedule.floors(seed,Event.NPC)
            for(floor in 1..50) {
                val first=MapGenerator.generate(s,floor)
                val npc=first.cells.singleOrNull { it.event==Event.NPC }
                assertEquals(floor in scheduled,npc!=null)
                if(npc!=null) {
                    seen.add(npc.npcRole)
                    s.rng+=719
                    val again=MapGenerator.generate(SaveCodec.decode(SaveCodec.encode(s)),floor).cells.single { it.event==Event.NPC }
                    assertEquals(npc.npcRole,again.npcRole);assertEquals(npc.npcGift,again.npcGift)
                }
            }
        }
        assertEquals(NpcRole.entries.toSet(),seen)
    }
    @Test fun usedServicesSurviveSaveDeathAndRegeneratedMapsButResetOnInheritance() {
        val e=fixture(NpcRole.HERBALIST);choose(e,"npcPotion")
        val restored=GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        restored.die();assertEquals(setOf(4),restored.state.npcServicesUsed)
        val seed=(1L..1000L).first { NpcInteractions.roleFor(it,4)==NpcRole.HERBALIST }
        restored.state.visitorSeed=seed
        // Use a scheduled floor that is guaranteed to contain a support NPC.
        val floor=VisitorSchedule.floors(seed,Event.NPC).first { NpcInteractions.roleFor(seed,it)!=NpcRole.QUEST }
        restored.state.npcServicesUsed.add(floor)
        val repeatMap=MapGenerator.generate(restored.state,floor)
        assertTrue(repeatMap.cells.single { it.event==Event.NPC }.resolved)
        val gear=ItemFactory.create(restored.state,Kind.SWORD);restored.state.inventory.add(gear)
        restored.newCycle(gear.id)
        assertTrue(restored.state.npcServicesUsed.isEmpty())
        assertNull(restored.state.visitorSeed)
    }
    @Test fun giftRateAndRoleDistributionRemainNearTheApprovedRates() {
        val gifts=(1L..10000L).count { NpcInteractions.giftFor(it,17) }
        assertTrue("gifts=$gifts",gifts in 2700..3300)
        for(role in NpcRole.entries) {
            val count=(1L..10000L).count { NpcInteractions.roleFor(it,17)==role }
            assertTrue("$role=$count",count in 2200..2800)
        }
    }
    @Test fun legacyCellsDefaultToQuestAndRetainTheirOffer() {
        val e=fixture(NpcRole.QUEST)
        val offer=e.state.dungeon!!.here().questOffer
        assertNotNull(offer)
        val old=SaveCodec.encode(e.state).replace(",\"npcRole\":\"QUEST\"","").replace(",\"npcGift\":null","").replace(",\"npcServicesUsed\":[]","")
        val restored=GameEngine(SaveCodec.decode(old))
        assertEquals(NpcRole.QUEST,restored.state.dungeon!!.here().npcRole)
        assertEquals(offer,restored.state.dungeon!!.here().questOffer)
        assertTrue(restored.state.npcServicesUsed.isEmpty())
        choose(restored,"questAccept");assertEquals(offer,restored.state.quest)
    }
    @Test fun trainingSupportActionsRestoreAllNormalResourcesAndServiceHistory() {
        for((action,role) in listOf("trainBeggar" to NpcRole.BEGGAR,"trainHealer" to NpcRole.HEALER,"trainHerbalist" to NpcRole.HERBALIST)) {
            val e=GameEngine(State(gold=57,npcServicesUsed=mutableSetOf(3,7)))
            val normal=SaveCodec.encode(e.state)
            choose(e,"training");choose(e,action)
            assertEquals(role,NpcInteractions.role(e.state))
            choose(e,role.action)
            assertTrue(e.state.training)
            e.exitTraining();assertEquals(normal,SaveCodec.encode(e.state))
        }
    }
}
