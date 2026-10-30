package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class EncounterFlowTest {
    private fun fixture(event: Event, floor: Int = 1): GameEngine {
        val e = GameEngine()
        val d = Dungeon(7, 7, MutableList(49) { Cell() }, x = 3, y = 3, direction = 0)
        d.here().wall = false
        d.cell(3, 2).wall = false
        d.cell(3, 2).event = event
        e.state.dungeon = d
        e.state.floor = floor
        e.state.mode = Mode.EXPLORE
        e.press(0)
        return e
    }

    private fun choose(e: GameEngine, action: String) =
        e.choose(e.choices().first { it.action == action })

    private fun advance(e: GameEngine, ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(50L, left)
            e.tick(step)
            left -= step
        }
    }

    @Test
    fun allThreeEventsStopMovementAndRestoreTheirOwnScene() {
        for (event in listOf(Event.CHEST, Event.NPC, Event.MERCHANT)) {
            val e = fixture(event)
            assertEquals(Mode.ENCOUNTER, e.state.mode)
            assertEquals(event, e.state.encounter)
            assertEquals(2, e.controls.buttons().size)
            assertFalse(e.choices().any { it.action == "move" })
            val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
            assertEquals(event, restored.state.encounter)
            assertEquals(e.choices(), restored.choices())
            choose(restored, "encounterLeave")
            assertEquals(Mode.EXPLORE, restored.state.mode)
            assertTrue(restored.controls.buttons().any { it.choice?.action == "move" })
            assertFalse(restored.state.dungeon!!.here().resolved)
        }
    }

    @Test
    fun oneNpcOfferIsStableAcrossDeclineRevisitAndReload() {
        val e = fixture(Event.NPC)
        val offer = e.state.dungeon!!.here().questOffer!!
        assertEquals(listOf("questAccept", "encounterLeave"), e.choices().map { it.action })
        choose(e, "encounterLeave")
        assertNull(e.state.quest)
        val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        choose(restored, "encounter")
        assertEquals(offer, restored.state.dungeon!!.here().questOffer)
        choose(restored, "questAccept")
        assertEquals(offer, restored.state.quest)
        assertEquals(Mode.EXPLORE, restored.state.mode)
        assertFalse(offer === restored.state.quest)
    }

    @Test
    fun activeMissionCannotBeReplacedAndRewardCanOnlyBeClaimedOnce() {
        val e = fixture(Event.NPC)
        choose(e, "questAccept")
        val quest = e.state.quest!!
        choose(e, "encounter")
        assertEquals(listOf("encounterLeave"), e.choices().map { it.action })
        assertSame(quest, e.state.quest)
        quest.progress = quest.target
        val gold = e.state.gold
        val claim = e.choices().first { it.action == "claim" }
        e.choose(claim)
        assertEquals(gold + quest.reward, e.state.gold)
        assertNull(e.state.quest)
        assertTrue(e.state.dungeon!!.here().resolved)
        e.choose(claim)
        assertEquals(gold + quest.reward, e.state.gold)
    }

    @Test
    fun finalFloorHasNoImpossibleMission() {
        val e = fixture(Event.NPC, 50)
        assertNull(e.state.dungeon!!.here().questOffer)
        assertFalse(e.choices().any { it.action == "questAccept" })
    }

    @Test
    fun chestCanContainLootOrAnAmbushAndCannotBeOpenedTwice() {
        for (seed in listOf(0L, 1L)) {
            val e = fixture(Event.CHEST)
            e.state.rng = seed
            e.state.quest = Quest("상자", "chest", 3)
            val count = e.state.inventory.size
            val action = e.choices().first { it.action == "chest" }
            e.choose(action)
            assertTrue(e.state.dungeon!!.here().resolved)
            assertEquals(1, e.state.quest!!.progress)
            if (seed == 0L) {
                assertEquals(Mode.COMBAT_ATTACK, e.state.mode)
                assertTrue(e.state.enemy!!.name.startsWith("상자 속"))
                assertEquals(count, e.state.inventory.size)
                e.state.enemy!!.hp = 1.0
                e.state.rng = 0L
                e.attack(false)
                choose(e, "battleContinue")
            } else assertEquals(count + 1, e.state.inventory.size)
            val saved = SaveCodec.encode(e.state)
            e.choose(action)
            assertEquals(saved, SaveCodec.encode(e.state))
            assertEquals(Mode.EXPLORE, e.state.mode)
        }
    }

    @Test
    fun merchantReturnsToEncounterAndSellingRemovesOnlyTheSelectedId() {
        val e = fixture(Event.MERCHANT)
        val sword = e.state.inventory.first { it.weapon }
        val duplicate = sword.copy(id = e.state.nextId++)
        e.state.inventory.add(duplicate)
        choose(e, "shop")
        assertEquals(Mode.ENCOUNTER, e.state.menu!!.returnMode)
        choose(e, "sellMenu")
        val row = e.choices().first { it.action == "sell" && it.argument == sword.id }
        assertTrue(row.label.contains("[착용중] · 오른손"))
        assertFalse(
            e.choices()
                .first { it.action == "sell" && it.argument == duplicate.id }
                .label
                .contains("[착용중]")
        )
        val gold = e.state.gold
        e.choose(row)
        assertEquals(gold + Equipment.sellPrice(sword), e.state.gold)
        assertFalse(sword.id in e.state.equipment.values)
        assertTrue(e.state.inventory.any { it.id == duplicate.id })
        assertFalse(e.choices().any { it.action == "sell" && it.argument == sword.id })
        e.choose(row)
        assertEquals(gold + Equipment.sellPrice(sword), e.state.gold)
        val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        assertFalse(restored.choices().any { it.action == "sell" && it.argument == sword.id })
        restored.navigateBack()
        restored.navigateBack()
        assertEquals(Mode.ENCOUNTER, restored.state.mode)
        assertEquals(Event.MERCHANT, restored.state.encounter)
    }

    @Test
    fun sellingTwoHandedGearClearsBothSlotsAndOnlyPaysOnce() {
        val e = fixture(Event.MERCHANT)
        val spear = ItemFactory.create(e.state, Kind.SPEAR)
        e.state.inventory.add(spear)
        Equipment.equip(e.state, spear.id, Slot.LEFT)
        choose(e, "shop")
        choose(e, "sellMenu")
        val row = e.choices().first { it.argument == spear.id && it.action == "sell" }
        assertTrue(row.label.contains("왼손·오른손"))
        val gold = e.state.gold
        e.choose(row)
        e.choose(row)
        assertEquals(gold + Equipment.sellPrice(spear), e.state.gold)
        assertNull(e.state.equipment[Slot.LEFT])
        assertNull(e.state.equipment[Slot.RIGHT])
    }

    @Test
    fun everyTrapTimesOutAfterTwoSecondsAndReturnsAutomatically() {
        for (type in 0..4) {
            val e = fixture(Event.TRAP)
            e.state.trap = Traps.create(e.state, type)
            val t = e.state.trap!!
            val hp = e.state.hp
            assertEquals(2000L, t.limitMs)
            advance(e, 999)
            assertEquals(TrapPhase.PREPARE, t.phase)
            advance(e, 1)
            if (type == 3) advance(e, t.revealMs)
            assertEquals(TrapPhase.ACTIVE, t.phase)
            advance(e, 1999)
            assertEquals(TrapPhase.ACTIVE, t.phase)
            advance(e, 1)
            assertEquals(TrapPhase.RESULT, t.phase)
            val damageHp = e.state.hp
            assertTrue(damageHp < hp)
            e.press(0)
            e.press(1)
            assertEquals(Mode.TRAP, e.state.mode)
            advance(e, Traps.RESULT_MS - 1)
            assertEquals(Mode.TRAP, e.state.mode)
            val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
            restored.tick(1)
            assertEquals(Mode.EXPLORE, restored.state.mode)
            assertEquals(damageHp, restored.state.hp, .001)
            assertNull(restored.state.trap)
            assertTrue(restored.state.dungeon!!.here().resolved)
        }
    }

    @Test
    fun allFiveTrapsCanSucceedWithinTheShortInputWindow() {
        for (type in 0..4) {
            val e = fixture(Event.TRAP)
            val t = Traps.create(e.state, type)
            e.state.trap = t
            advance(e, Traps.PREPARE_MS)
            if (type == 3) advance(e, t.revealMs)
            when (type) {
                0,
                3 -> t.pattern.forEach { e.press(it) }
                1 -> {
                    advance(e, t.limitMs / 2)
                    e.press(0)
                }
                2 -> repeat(t.required) { e.press(t.pattern[0]) }
                4 -> {
                    advance(e, t.limitMs / 2)
                    e.press(1)
                }
            }
            assertTrue("trap $type", t.success)
            advance(e, Traps.RESULT_MS)
            assertEquals(Mode.EXPLORE, e.state.mode)
        }
    }

    @Test
    fun legacyManualGuideAndNpcMenuMigrateWithoutLosingProgress() {
        val e = fixture(Event.NPC)
        val s =
            e.state.copy(
                mode = Mode.MENU,
                menu = Menu("npc", returnMode = Mode.EXPLORE),
                encounter = null,
            )
        val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(s)))
        assertEquals(Mode.ENCOUNTER, restored.state.mode)
        assertEquals(Event.NPC, restored.state.encounter)
        val oldTrap =
            State(bootstrapped = true, mode = Mode.TRAP_GUIDE, trap = Traps.create(State(), 0))
        val migrated = GameEngine(SaveCodec.decode(SaveCodec.encode(oldTrap)))
        advance(migrated, Traps.PREPARE_MS)
        assertEquals(TrapPhase.ACTIVE, migrated.state.trap!!.phase)
    }
}
