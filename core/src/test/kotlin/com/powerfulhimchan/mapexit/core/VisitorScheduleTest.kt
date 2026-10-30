package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class VisitorScheduleTest {
    @Test
    fun lateNpcNeverRequestsMoreChestsThanRemainInTheCurrentAndLastFloor() {
        var offeredChestQuest = false
        for (seed in 1L..100L) {
            val d = Dungeon(9, 9, MutableList(81) { Cell() })
            d.here().wall = false
            d.here().event = Event.NPC
            val e =
                GameEngine(
                    State(
                        bootstrapped = true,
                        rng = seed,
                        floor = 49,
                        dungeon = d,
                        mode = Mode.EXPLORE,
                    )
                )
            e.choose(Choice("NPC", "encounter", Event.NPC.ordinal.toLong()))
            val offer = e.state.dungeon!!.here().questOffer
            if (offer?.kind == "chest") {
                offeredChestQuest = true
                assertEquals(2, offer.target)
            }
        }
        assertTrue(offeredChestQuest)
    }

    @Test
    fun visitorsStartOnThirdOrFourthFloorAndEveryFollowingGapIsThreeOrFour() {
        val gaps = mutableSetOf<Int>()
        for (seed in 1L..100L) for (event in listOf(Event.NPC, Event.MERCHANT)) {
            val floors = VisitorSchedule.floors(seed, event)
            assertTrue(floors.first() in 3..4)
            assertTrue(50 - floors.last() in 0..3)
            assertTrue(floors.size in 12..16)
            floors
                .zipWithNext { a, b -> b - a }
                .forEach {
                    assertTrue(it in 3..4)
                    gaps.add(it)
                }
        }
        assertEquals(setOf(3, 4), gaps)
        assertTrue(
            (1L..20L).any {
                VisitorSchedule.floors(it, Event.NPC) != VisitorSchedule.floors(it, Event.MERCHANT)
            }
        )
    }

    @Test
    fun allFiftyMapsHaveTwoChestsAndExactlyOneVisitorOnlyOnItsScheduledFloors() {
        val s = State(bootstrapped = true, rng = 777L)
        for (floor in 1..50) {
            val d = MapGenerator.generate(s, floor)
            assertEquals(2, d.cells.count { it.event == Event.CHEST })
            for (event in listOf(Event.NPC, Event.MERCHANT)) assertEquals(
                if (floor in VisitorSchedule.floors(s.visitorSeed!!, event)) 1 else 0,
                d.cells.count { it.event == event },
            )
            assertEquals(Event.NONE, d.here().event)
            assertEquals(1, d.cells.count { it.event == Event.EXIT })
        }
    }

    @Test
    fun revisitingWithADifferentMapSeedAndReloadingCannotRerollTheVisitorSchedule() {
        val s = State(bootstrapped = true, rng = 77L)
        MapGenerator.generate(s, 1)
        val seed = s.visitorSeed!!
        for (floor in 1..50) {
            val first = MapGenerator.generate(s, floor)
            s.rng = floor.toLong() * 9911
            val restored = SaveCodec.decode(SaveCodec.encode(s))
            val again = MapGenerator.generate(restored, floor)
            assertEquals(seed, restored.visitorSeed)
            for (event in listOf(Event.NPC, Event.MERCHANT)) assertEquals(
                first.cells.count { it.event == event },
                again.cells.count { it.event == event },
            )
        }
    }

    @Test
    fun aNewCycleClearsThePreviousVisitorSchedule() {
        val e = GameEngine()
        e.enterFloor(1)
        assertNotNull(e.state.visitorSeed)
        val gear = e.state.inventory.first { it.weapon }
        e.newCycle(gear.id)
        assertNull(e.state.visitorSeed)
        e.enterFloor(1)
        assertNotNull(e.state.visitorSeed)
    }

    @Test
    fun legacySavedCurrentMapIsPreservedAndGetsAScheduleOnTheNextGeneratedFloor() {
        val e = GameEngine()
        e.enterFloor(1)
        e.state.dungeon!!
            .cells
            .first { !it.wall && it.event == Event.NONE && it !== e.state.dungeon!!.here() }
            .event = Event.NPC
        val root = Json.parseToJsonElement(SaveCodec.encode(e.state)).jsonObject
        val old = JsonObject(root.filterKeys { it != "visitorSeed" }).toString()
        val restored = GameEngine(SaveCodec.decode(old))
        assertEquals(e.state.dungeon, restored.state.dungeon)
        assertNull(restored.state.visitorSeed)
        restored.enterFloor(2)
        assertNotNull(restored.state.visitorSeed)
        assertEquals(2, restored.state.dungeon!!.cells.count { it.event == Event.CHEST })
    }
}
