package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class RequestedImprovementsTest {
    private fun battle(floor: Int = 1): GameEngine = GameEngine(State()).also { it.enterFloor(floor); it.startBattle(false) }
    @Test fun everyTenFloorsHasSixDifferentSpeciesAndMapsPersistTheirAssignment() {
        val groups = (1..50 step 10).map { MonsterCatalog.forFloor(it) }
        assertTrue(groups.all { it.size == 6 })
        assertEquals(30, groups.flatten().map { it.name }.distinct().size)
        assertEquals(30, groups.flatten().map { it.sprite }.distinct().size)
        for (floor in 1..50) {
            assertEquals(groups[(floor-1)/10], MonsterCatalog.forFloor(floor))
            val e = GameEngine(State(rng=floor.toLong()));e.enterFloor(floor)
            val map = e.state.dungeon!!
            val allowed = MonsterCatalog.forFloor(floor).map { it.id }.toSet()
            assertTrue(map.cells.filter { it.event==Event.MONSTER }.all { it.monsterId in allowed })
            val index=map.cells.indexOfFirst { it.event==Event.MONSTER }
            map.x=index%map.width;map.y=index/map.width
            val expected=map.here().monsterId
            val restored=GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
            restored.startBattle(false)
            assertEquals(expected, restored.state.enemy!!.speciesId)
        }
    }
    @Test fun encounterKillDeathAndRetreatHaveIndependentPersistentCounters() {
        val e = battle()
        val id=e.state.enemy!!.speciesId!!
        assertEquals(1,e.state.bestiary.getValue(id).encounters)
        e.state.enemy!!.hp=1.0;e.state.rng=0;e.attack(false)
        assertEquals(Mode.VICTORY,e.state.mode)
        assertEquals(1,e.state.bestiary.getValue(id).defeats)
        e.navigateBack() // no effect outside a menu
        e.choose(e.choices().first { it.action=="battleContinue" })
        e.startBattle(false);val deadId=e.state.enemy!!.speciesId!!
        e.state.hp=0.0;e.die();e.die()
        assertEquals(1,e.state.bestiary.getValue(deadId).deaths)
        val saved=GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertEquals(e.state.bestiary,saved.state.bestiary)
        saved.choose(saved.choices().first { it.action=="runContinue" })
        saved.enterFloor(1);saved.startBattle(false)
        val deaths=saved.state.bestiary.values.sumOf { it.deaths }
        saved.state.enemy=null;saved.state.mode=Mode.EXPLORE
        saved.choose(saved.choices().first { it.action=="retreat" })
        assertEquals(deaths,saved.state.bestiary.values.sumOf { it.deaths })
    }
    @Test fun trainingDoesNotPolluteTheBookAndOldSavesStartWithNoInventedHistory() {
        val e = GameEngine(State(training=true,debug=true));e.enterFloor(1);e.startBattle(false);e.die()
        assertTrue(e.state.bestiary.isEmpty())
        val old=SaveCodec.encode(State()).replace(",\"bestiary\":{}", "")
        assertTrue(SaveCodec.decode(old).bestiary.isEmpty())
        val normal=battle();val id=normal.state.enemy!!.speciesId!!
        val restored=GameEngine(SaveCodec.decode(SaveCodec.encode(normal.state)))
        assertEquals(1,restored.state.bestiary.getValue(id).encounters)
    }
    @Test fun bookSurvivesInheritanceAndUsesOneSpeciesForChestAmbushes() {
        val e=battle();val monster=e.state.enemy!!
        MonsterCatalog.record(e.state,monster.copy(name="상자 속 ${monster.name}"),encounter=1)
        assertEquals(1,e.state.bestiary.size)
        assertEquals(2,e.state.bestiary.values.single().encounters)
        val book=e.state.bestiary.toMap()
        val gear=e.state.inventory.first { it.kind!=Kind.POTION }
        e.newCycle(gear.id)
        assertEquals(book,e.state.bestiary)
    }
    @Test fun fullHealCostsExactlyOnePointUsesEquippedMaxAndCannotWastePoints() {
        val e=GameEngine(State(points=3));e.enterFloor(1);e.state.hp=1.0
        assertFalse(e.restoreWithGrowthPoint())
        e.choose(e.choices().first { it.action=="growth" })
        val inventory=e.state.inventory.toList();val base=e.state.base;val level=e.state.level
        assertTrue(e.restoreWithGrowthPoint());assertEquals(2,e.state.points)
        assertEquals(e.stats().hp,e.state.hp,0.0)
        assertFalse(e.restoreWithGrowthPoint());assertEquals(2,e.state.points)
        assertEquals(inventory,e.state.inventory);assertEquals(base,e.state.base);assertEquals(level,e.state.level)
        e.state.points=0;e.state.hp=1.0
        assertFalse(e.restoreWithGrowthPoint());assertEquals(1.0,e.state.hp,0.0)
        e.state.points=1;e.state.hp=0.0
        assertFalse(e.restoreWithGrowthPoint())
    }
    @Test fun fullHealPreservesPendingVictoryAndGrowthReturnMode() {
        val e=battle();e.state.enemy!!.hp=1.0;e.state.rng=0;e.state.hp=10.0;e.attack(false)
        e.state.points=1;val result=e.state.battleResult
        assertTrue(e.openDungeonPanel("growth"))
        assertTrue(e.restoreWithGrowthPoint());e.navigateBack()
        assertEquals(Mode.VICTORY,e.state.mode);assertEquals(result,e.state.battleResult)
    }
    @Test fun everyDoorHasOnlyItsOneAccessibleFaceInEveryDirection() {
        for (d in 0..3) {
            val m=Dungeon(9,9,MutableList(81) { Cell() },4,4,d)
            m.here().wall=false;m.here().event=Event.EXIT
            m.cell(4-MapGenerator.dx[d],4-MapGenerator.dy[d]).wall=false
            val wx=4+MapGenerator.dx[d];val wy=4+MapGenerator.dy[d]
            assertTrue(DungeonFixtures.doorOnFace(m,wx,wy,4,4))
            assertFalse(DungeonFixtures.doorOnFace(m,wx,wy,4+2*MapGenerator.dx[d],4+2*MapGenerator.dy[d]))
            for (side in 0..3) if(side!=(d+2)%4)
                assertFalse(DungeonFixtures.doorOnFace(m,wx,wy,wx+MapGenerator.dx[side],wy+MapGenerator.dy[side]))
            m.here().resolved=true;assertFalse(DungeonFixtures.doorOnFace(m,wx,wy,4,4))
        }
    }
    @Test fun nearFixturesStayInsideBothScreenCapsAndStillGrowAsTheyApproach() {
        for (h in listOf(180,640,960)) for(event in listOf(Event.CHEST,Event.MONSTER,Event.NPC,Event.HEAL,Event.ELITE)) {
            var previous=0f
            for(depth in listOf(8f,4f,2f,1f,.5f,.13f)) {
                val span=DungeonFixtures.spriteSpan(event,200f,depth,480,h,1.33f)
                assertTrue(span>=previous);assertTrue(span<=h*.56f+.001f);assertTrue(span*1.33f<=480*.56f+.001f)
                previous=span
            }
        }
    }
    @Test fun weaponsJewelryAndTiersHaveDifferentStableVisualKeysIncludingOldItems() {
        val s=State();val keys=WeaponCatalog.all.map { v -> GearAppearance.key(ItemFactory.create(s,v.kind,1,v.id).copy(tier=1)) }
        assertEquals(20,keys.distinct().size)
        val rings=listOf("전사의","바람의","광기의","사냥꾼의","행운의","수호의").mapIndexed { i,name -> GearAppearance.key(Gear(i.toLong(),Kind.RING,"$name 반지 +1")) }
        assertEquals(6,rings.distinct().size)
        val old=Gear(1,Kind.SWORD,"레이피어 +2",tier=2)
        assertTrue(GearAppearance.key(old).contains("rapier"))
        assertNotEquals(GearAppearance.key(old),GearAppearance.key(old.copy(tier=3)))
        assertTrue(GearAppearance.heldScale(Kind.CLAW)<.6f)
        Stat.entries.forEach { assertTrue(Growth.description(it).isNotBlank()) }
    }
}
