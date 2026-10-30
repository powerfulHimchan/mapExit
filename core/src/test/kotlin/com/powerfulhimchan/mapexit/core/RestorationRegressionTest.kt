package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RestorationRegressionTest {
    private fun choose(e:GameEngine,action:String,arg:Long?=null)=e.choose(e.choices().first { it.action==action && (arg==null || it.argument==arg) })
    private fun win(e:GameEngine) { e.state.enemy!!.hp=1.0;e.state.rng=0;e.attack(false);assertEquals(Mode.VICTORY,e.state.mode) }
    @Test fun themesChangeOnlyAtTenFloorBoundaries() {
        assertEquals(5,(1..50).map { DungeonTheme.forFloor(it) }.distinct().size)
        for(f in 1..50)assertEquals(DungeonTheme.entries[(f-1)/10],DungeonTheme.forFloor(f))
    }
    @Test fun everyEliteIsImmediatelyBeforeTheExitAndCannotReplaceAChest() {
        for(seed in 1L..15L)for(floor in 5..50 step 5) {
            val map=MapGenerator.generate(State(rng=seed),floor)
            val elite=map.cells.indexOfFirst { it.event==Event.ELITE }
            val exit=map.cells.indexOfFirst { it.event==Event.EXIT }
            assertEquals(1,abs(elite%map.width-exit%map.width)+abs(elite/map.width-exit/map.width))
            val dist=MapGenerator.distances(map,1,1)
            assertEquals(dist[exit]-1,dist[elite]);assertEquals(2,map.cells.count { it.event==Event.CHEST })
        }
    }
    @Test fun eliteVictoryGivesOneGearAndExtraRewardsWithoutClearingTheBoss() {
        val e=GameEngine();e.enterFloor(10)
        val m=e.state.dungeon!!;val i=m.cells.indexOfFirst { it.event==Event.ELITE };m.x=i%m.width;m.y=i/m.width
        e.startBattle(false,elite=true);val s=e.state
        val normalHp=300+10*26.0;assertEquals(normalHp*1.4,s.enemy!!.maxHp,.0001)
        win(e)
        assertTrue(m.eliteCleared);assertTrue(m.here().resolved);assertFalse(m.bossCleared)
        val result=s.battleResult!!
        assertEquals(112,result.experience);assertEquals(90,result.gold)
        assertEquals(1,result.rewards.size);assertNotEquals(Kind.POTION,result.rewards.single().kind)
        assertEquals(Mode.EXPLORE,result.returnMode)
        val before=s.inventory.size;choose(e,"battleContinue");assertEquals(before,s.inventory.size)
        val exit=m.cells.indexOfFirst { it.event==Event.EXIT };m.x=exit%m.width;m.y=exit/m.width
        choose(e,"exit");assertTrue(s.enemy!!.boss);assertFalse(s.enemy!!.elite)
    }
    @Test fun legacyFifthFloorSaveStillRequiresItsGuardBeforeClearing() {
        for(floor in listOf(5,10)) {
            val e=GameEngine();e.enterFloor(floor);val m=e.state.dungeon!!
            m.cells.filter { it.event==Event.ELITE }.forEach { it.event=Event.NONE }
            val i=m.cells.indexOfFirst { it.event==Event.EXIT };m.x=i%m.width;m.y=i/m.width
            choose(e,"exit");assertTrue(e.state.enemy!!.elite);win(e);choose(e,"battleContinue")
            assertFalse(m.here().resolved)
            if(floor==5)assertEquals(Mode.FLOOR_CLEAR,e.state.mode)
            else { choose(e,"exit");assertTrue(e.state.enemy!!.boss) }
        }
    }
    @Test fun emptySlotOnlyOffersCompatibleGearAndEquipsOnSelection() {
        for(slot in Slot.entries) {
            val e=GameEngine();e.state.equipment.clear()
            Kind.entries.forEach { e.state.inventory.add(ItemFactory.create(e.state,it)) }
            choose(e,"equipment");choose(e,"slotItem",slot.ordinal.toLong())
            assertEquals("slotInventory",e.state.menu!!.type)
            val offered=e.choices().filter { it.action=="item" }
            assertTrue(offered.isNotEmpty())
            for(c in offered)assertTrue(slot in Equipment.allowedSlots(e.state.inventory.first { it.id==c.argument }))
            choose(e,"item",offered.first().argument)
            assertEquals(offered.first().argument,e.state.equipment[slot]);assertEquals("equipment",e.state.menu!!.type)
        }
    }
    @Test fun bagAndOccupiedSlotKeepTheirDetailsAndUseTheNewWornLabel() {
        val e=GameEngine();choose(e,"inventory")
        val worn=e.state.equipment[Slot.RIGHT]!!
        assertTrue(e.choices().first { it.action=="item" && it.argument==worn }.label.contains("[착용중]"))
        choose(e,"item",worn);assertEquals("item",e.state.menu!!.type)
        assertTrue(e.choices().any { it.action=="unequip" })
    }
    @Test fun quickEquipCannotEquipUnownedOrUnrewardedItems() {
        val e=GameEngine();e.enterFloor(1)
        val id=e.state.inventory.first().id
        val before=SaveCodec.encode(e.state)
        e.choose(Choice("장착","equipLoot",id));e.choose(Choice("장착","equipLoot",999))
        assertEquals(before,SaveCodec.encode(e.state))
    }
    @Test fun quickEquipUsesEmptyHandsAndAppliesTwoHandedReplacementRules() {
        val e=GameEngine();e.enterFloor(1);val s=e.state
        val g=ItemFactory.create(s,Kind.SPEAR);s.inventory.add(g);s.quickEquipIds=listOf(g.id)
        e.choose(Choice("장착","equipLoot",g.id))
        assertEquals(g.id,s.equipment[Slot.LEFT]);assertEquals(g.id,s.equipment[Slot.RIGHT])
        val saved=SaveCodec.encode(s);e.choose(Choice("장착","equipLoot",g.id));assertEquals(saved,SaveCodec.encode(s))
    }
    @Test fun recentDropsRemainAvailableThroughSaveAndDisappearAfterMovement() {
        val e=GameEngine();e.enterFloor(5);e.startBattle(false,elite=true);win(e)
        val restored=GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertEquals(1,restored.lootChoices().size)
        restored.choose(restored.lootChoices().single());assertTrue(restored.state.quickEquipIds.single() in restored.state.equipment.values)
        choose(restored,"battleContinue");assertTrue(restored.state.quickEquipIds.isEmpty())
    }
    @Test fun comparisonIncludesPenaltiesDisplacedGearAndUnclampedStats() {
        val old=Gear(1,Kind.SWORD,"검",flat=Stats.zero().copy(attack=10.0,agility=4.0,crit=.9))
        val better=old.copy(id=2,flat=old.flat.copy(attack=11.0,crit=1.0))
        val s=State(bootstrapped=true,inventory=mutableListOf(old,better),equipment=mutableMapOf(Slot.RIGHT to 1L))
        assertTrue(Equipment.isUpgrade(s,better))
        assertFalse(Equipment.isUpgrade(s,better.copy(id=3,flat=better.flat.copy(agility=3.0))))
        assertFalse(Equipment.isUpgrade(s,better.copy(id=4,kind=Kind.SPEAR)))
        assertFalse(Equipment.isUpgrade(s,better,Slot.LEFT))
        assertFalse(Equipment.isUpgrade(s,old))
        assertFalse(Equipment.isUpgrade(s,old.copy(id=3)))
    }
    @Test fun ringComparisonUsesOnlyTheSelectedSlotWhenFiltering() {
        val strong=Gear(1,Kind.RING,"상급",flat=Stats.zero().copy(luck=10.0))
        val weak=strong.copy(id=2,flat=Stats.zero().copy(luck=1.0))
        val candidate=strong.copy(id=3,flat=Stats.zero().copy(luck=5.0))
        val s=State(bootstrapped=true,inventory=mutableListOf(strong,weak,candidate),equipment=mutableMapOf(Slot.RING1 to 1L,Slot.RING2 to 2L))
        assertTrue(Equipment.isUpgrade(s,candidate));assertFalse(Equipment.isUpgrade(s,candidate,Slot.RING1));assertTrue(Equipment.isUpgrade(s,candidate,Slot.RING2))
    }
    @Test fun shopSelectionAndCancelNeverPurchaseAndConfirmationBuysOnlyOnce() {
        val e=GameEngine();val s=e.state;s.gold=10000
        choose(e,"shop");val g=s.shop.first { it.kind!=Kind.POTION };val wallet=s.gold;val inventory=s.inventory.toList()
        assertEquals(g.name,e.choices().first { it.action=="buy" && it.argument==g.id }.label)
        choose(e,"buy",g.id);assertEquals("shopItem",s.menu!!.type);assertEquals(wallet,s.gold);assertEquals(inventory,s.inventory)
        choose(e,"back");assertEquals("shop",s.menu!!.type);assertEquals(wallet,s.gold)
        choose(e,"buy",g.id);assertFalse(e.purchase(g.id,3));assertTrue(e.purchase(g.id,1));assertFalse(e.purchase(g.id,1))
        assertEquals(wallet-Equipment.buyPrice(s,g),s.gold);assertEquals(1,s.inventory.count { it.id==g.id });assertFalse(s.shop.any { it.id==g.id })
        assertEquals(listOf(g.id),s.quickEquipIds)
    }
    @Test fun potionQuantitiesAreUniqueAndInsufficientFundsKeepTheDetailOpen() {
        val e=GameEngine();val s=e.state;s.gold=0;choose(e,"shop");val p=s.shop.first { it.kind==Kind.POTION }
        choose(e,"buy",p.id);val count=s.inventory.size
        assertFalse(e.purchase(p.id,5));assertEquals(count,s.inventory.size);assertEquals("shopItem",s.menu!!.type)
        s.gold=1000;assertTrue(e.purchase(p.id,5));assertEquals(count+5,s.inventory.size)
        assertEquals(s.inventory.size,s.inventory.map { it.id }.distinct().size);assertTrue(s.quickEquipIds.isEmpty())
    }
    @Test fun completedActionsClearOnSuccessfulMovementButNotWhileFacingAWall() {
        for(note in listOf("함정 회피 성공!","장비를 변경했습니다","회복 장소: HP 45% 회복")) {
            val m=Dungeon(9,9,MutableList(81){Cell()},4,5,0);for(y in 2..6)m.cell(4,y).wall=false
            val e=GameEngine(State(bootstrapped=true,floor=1,dungeon=m,mode=Mode.EXPLORE,message=note))
            choose(e,"move",0);assertEquals("통로를 탐험하고 있습니다",e.state.message)
        }
    }
    @Test fun turningAtADeadEndDoesNotStepOrLoseTheIgnoredFacility() {
        val m=Dungeon(9,9,MutableList(81){Cell()},4,4,0);m.here().wall=false;m.cell(4,5).wall=false;m.here().event=Event.HEAL
        val e=GameEngine(State(bootstrapped=true,floor=1,dungeon=m,mode=Mode.EXPLORE))
        e.press(1);val turn=e.controls.buttons().indexOfFirst { it.choice?.action=="_turnBack" };assertTrue(turn>=0)
        e.press(turn);assertEquals(4,m.x);assertEquals(4,m.y);assertEquals(2,m.direction);assertFalse(m.here().resolved);assertEquals(0L,e.state.movementMs)
    }
    @Test fun attackPosesStayContinuousAndReactionNeverPrecedesContact() {
        for(kind in Kind.entries.filter { Gear(1,it,"").weapon })for(strong in listOf(false,true)) {
            var previous=BattleAnimation.pose(kind,0,strong)
            for(ms in 1L..760L) {
                val p=BattleAnimation.pose(kind,ms,strong)
                assertTrue(abs(p.angle-previous.angle)<8)
                assertTrue(abs(p.advance-previous.advance)<.06)
                assertTrue(p.impact in 0f..1f)
                if(ms<320) { assertEquals(0f,p.impact,0f);assertEquals(0f,BattleAnimation.reaction(ms),0f) }
                previous=p
            }
            assertEquals(0f,previous.advance,0f)
        }
        for(i in -100..1100)assertTrue(BattleAnimation.smooth(i/1000f) in 0f..1f)
        assertEquals(320L,BattleAnimation.motionTime(351,true));assertEquals(760L,BattleAnimation.motionTime(760,true))
    }
}
