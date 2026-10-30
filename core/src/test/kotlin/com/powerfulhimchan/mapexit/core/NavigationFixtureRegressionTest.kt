package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class NavigationFixtureRegressionTest {
    private fun junction(facing: Int, mask: Int): GameEngine {
        val map=Dungeon(9,9,MutableList(81) { Cell() },4,4,facing)
        map.here().wall=false
        for(relative in 0..3) if(mask and (1 shl relative)!=0) {
            val d=(facing+relative)%4
            map.cell(4+MapGenerator.dx[d],4+MapGenerator.dy[d]).wall=false
        }
        return GameEngine(State(bootstrapped=true,dungeon=map,floor=1,mode=Mode.EXPLORE))
    }
    private fun assertSides(e: GameEngine) {
        val facing=e.state.dungeon!!.direction
        val buttons=e.controls.buttons()
        assertEquals(2,buttons.size)
        buttons.forEachIndexed { index,b ->
            b.choice?.takeIf { it.action=="move" }?.let {
                when((it.argument.toInt()-facing+4)%4) {
                    3 -> assertEquals("Left must be on the left",0,index)
                    1 -> assertEquals("Right must be on the right",1,index)
                }
            }
        }
    }
    @Test fun leftAndRightKeepTheirPhysicalButtonsAtEveryJunctionAndOrientation() {
        for(facing in 0..3) for(mask in 1..15) {
            val e=junction(facing,mask);assertSides(e)
            for(group in e.controls.buttons().mapNotNull { it.choice }.filter { it.action in listOf("_pair","_routes") }) {
                val fresh=junction(facing,mask)
                val index=fresh.controls.buttons().indexOfFirst { it.choice?.action==group.action && it.choice.argument==group.argument }
                fresh.press(index);assertSides(fresh)
            }
        }
    }
    @Test fun theRightButtonActuallyEntersTheRightPathEvenWhenOnlyThatPathExists() {
        for(facing in 0..3) {
            val e=junction(facing,2)
            val right=(facing+1)%4
            assertEquals("move",e.controls.buttons()[1].choice!!.action)
            e.press(1)
            assertEquals(4+MapGenerator.dx[right],e.state.dungeon!!.x)
            assertEquals(4+MapGenerator.dy[right],e.state.dungeon!!.y)
        }
    }
    @Test fun bladesStartBeyondTheWholeBodyAndFinishBehindTheCenteredLanding() {
        for(height in listOf(200f,480f,640f,960f)) {
            val t=Traps.create(State(),1)
            for(phase in listOf(TrapPhase.PREPARE,TrapPhase.ACTIVE)) {
                t.phase=phase
                val stage=ObstacleLayout.blade(480f,height,t)
                assertTrue(stage.heroX+stage.heroWidth<stage.bladeLeft)
                assertEquals(0f,stage.jump,0f)
            }
            t.phase=TrapPhase.RESULT;t.action=0
            var previous=-1f
            for(ms in 0L..899L step 10) {
                t.elapsedMs=ms
                val stage=ObstacleLayout.blade(480f,height,t)
                assertTrue(stage.heroX>=previous)
                previous=stage.heroX
                assertTrue(stage.jump>=0)
            }
            t.elapsedMs=899
            val end=ObstacleLayout.blade(480f,height,t)
            assertEquals(240f,end.heroX+end.heroWidth/2,.001f)
            assertTrue(end.heroX>end.bladeRight)
            assertEquals(0f,end.jump,.001f)
        }
    }
    private fun corridor(): Dungeon {
        val map=Dungeon(9,9,MutableList(81) { Cell() },4,7,0)
        for(y in 2..7) map.cell(4,y).wall=false
        map.cell(4,2).event=Event.EXIT
        map.cell(4,5).event=Event.HEAL
        return map
    }
    @Test fun fixturesTwoCellsAheadAreVisibleAndWallsAndLookingAwayHideThem() {
        val map=corridor()
        val view=DungeonViewPose(4.5f,7.5f,(-PI/2).toFloat())
        assertEquals(setOf(Event.HEAL,Event.EXIT),DungeonFixtures.visible(map,view).map { it.event }.toSet())
        assertEquals(2f,DungeonFixtures.visible(map,view).first { it.event==Event.HEAL }.depth,.001f)
        assertTrue(DungeonFixtures.visible(map,view.copy(angle=(PI/2).toFloat())).isEmpty())
        map.cell(4,6).wall=true
        assertTrue(DungeonFixtures.visible(map,view).isEmpty())
    }
    @Test fun closeFacilityCameraStopsSmoothlyWithoutZoomingSmallerAtArrival() {
        val map=corridor();map.y=5;map.arrivalDirection=0
        val focal=DungeonFixtures.focal(map,480,200)
        var previous=0f
        for(distance in listOf(2.5f,2f,1.5f,1f,.8f,.7f,.5f,.2f,0f)) {
            val view=DungeonFixtures.camera(map,DungeonViewPose(4.5f,5.5f+distance,(-PI/2).toFloat()))
            val item=DungeonFixtures.visible(map,view).first { it.event==Event.HEAL }
            val span=.72f*focal/item.depth
            assertTrue(span>=previous-.001f)
            assertTrue(span<=.72f*focal/.7f+.001f)
            previous=span
        }
        assertEquals(4 to 1,DungeonFixtures.doorWall(map,4,2))
        map.direction=2
        val view=DungeonViewPose(4.5f,5.5f,(PI/2).toFloat())
        assertEquals(view,DungeonFixtures.camera(map,view))
    }
    @Test fun resolvedFixturesDisappearAndOldSavesKeepNewDefaults() {
        val map=corridor();val view=DungeonViewPose(4.5f,7.5f,(-PI/2).toFloat())
        map.cell(4,5).resolved=true
        assertEquals(listOf(Event.EXIT),DungeonFixtures.visible(map,view).map { it.event })
        val text=SaveCodec.encode(State()).replace("\"quickEquipIds\":[],","")
        assertTrue(SaveCodec.decode(text).quickEquipIds.isEmpty())
        assertFalse(SaveCodec.decode(text).dungeon?.eliteCleared ?: false)
    }
    @Test fun eachFifthFloorKeepsItsGuardAndTwoChests() {
        for(floor in 1..50) {
            val map=MapGenerator.generate(State(rng=floor.toLong()),floor)
            assertEquals(if(floor%5==0)1 else 0,map.cells.count { it.event==Event.ELITE })
            assertEquals(2,map.cells.count { it.event==Event.CHEST })
        }
    }
    @Test fun levelUpGrowthReturnsToThePendingRewardAndKeepsExperience() {
        val e=GameEngine(State(experience=79));e.enterFloor(1);e.startBattle(false)
        e.state.enemy!!.hp=1.0;e.state.rng=0;e.attack(false)
        assertEquals(Mode.VICTORY,e.state.mode)
        assertEquals(2,e.state.level);assertEquals(29,e.state.experience)
        assertEquals(160,e.state.experienceRequired);assertEquals(131,e.state.experienceRemaining)
        val reward=e.state.battleResult
        assertTrue(e.openLevelUpGrowth());e.spendPoint(Stat.ATTACK)
        val restored=GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        restored.navigateBack()
        assertEquals(Mode.VICTORY,restored.state.mode);assertEquals(reward,restored.state.battleResult)
        assertEquals(2,restored.state.points)
    }
    @Test fun quickEquipAndSameKindUpgradeChecksRetainThePreviousFlow() {
        val old=Gear(1,Kind.SWORD,"검",flat=Stats.zero().copy(attack=10.0))
        val next=old.copy(id=2,flat=old.flat.copy(attack=11.0))
        val s=State(bootstrapped=true,mode=Mode.EXPLORE,inventory=mutableListOf(old,next),
            equipment=mutableMapOf(Slot.RIGHT to 1L),quickEquipIds=listOf(2))
        assertTrue(Equipment.isUpgrade(s,next))
        val e=GameEngine(s);e.choose(e.choices().first { it.action=="equipLoot" })
        assertTrue(2L in s.equipment.values)
        assertFalse(Equipment.isUpgrade(s,next))
        assertEquals(2,s.inventory.size)
    }
}
