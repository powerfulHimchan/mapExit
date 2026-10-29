package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class ContextControlsTest {
    private fun corridor(directions: List<Int> = listOf(0, 2)): GameEngine {
        val d = Dungeon(9, 9, MutableList(81) { Cell() }, x = 4, y = 4, direction = 0)
        d.here().wall = false
        directions.forEach { direction ->
            d.cell(4 + MapGenerator.dx[direction], 4 + MapGenerator.dy[direction]).wall = false
        }
        return GameEngine(State(bootstrapped = true, mode = Mode.EXPLORE, floor = 1, dungeon = d))
    }

    private fun choose(e: GameEngine, action: String, arg: Long? = null) {
        e.choose(e.choices().first { it.action == action && (arg == null || it.argument == arg) })
    }

    @Test
    fun straightHallHasImmediateForwardAndBackward() {
        val e = corridor()
        assertEquals(listOf("↑ 앞으로 이동", "↓ 뒤로 이동"), e.controls.buttons().map { it.label })
        e.press(0)
        assertEquals(3, e.state.dungeon!!.y)
        assertEquals(0, e.state.selection)
    }

    @Test
    fun walkingBackwardPreservesFacingAndStopsAfterOneCell() {
        val e = corridor()
        val d = e.state.dungeon!!
        d.cell(4, 6).wall = false
        e.press(1)
        assertEquals(5, d.y)
        assertEquals(0, d.direction)
        assertEquals(Mode.EXPLORE, e.state.mode)
        e.tick(250)
        e.tick(250)
        e.tick(250)
        assertEquals(5, d.y)
    }

    @Test
    fun allFourJunctionExitsCanBeReachedUsingOnlyTwoButtons() {
        for (target in 0..3) {
            val e = corridor(listOf(0, 1, 2, 3))
            var presses = 0
            while (presses++ < 4) {
                val b = e.controls.buttons()
                assertEquals(2, b.size)
                val index =
                    b.indexOfFirst {
                        it.choice?.action == "move" && it.choice.argument == target.toLong()
                    }
                if (index >= 0) {
                    e.press(index)
                    break
                }
                if (b.first().choice?.action == "_pair")
                    e.press(if (target in listOf(0, 2)) 0 else 1)
                else e.press(1)
            }
            assertTrue(presses <= 2)
            assertEquals(4 + MapGenerator.dx[target], e.state.dungeon!!.x)
            assertEquals(4 + MapGenerator.dy[target], e.state.dungeon!!.y)
        }
    }

    @Test
    fun branchNavigationCanReturnWithoutMovingOrSpendingATurn() {
        val e = corridor(listOf(0, 1, 2, 3))
        val rng = e.state.rng
        e.press(1)
        e.hold(1)
        assertEquals("앞 / 뒤 방향", e.controls.buttons()[0].label)
        assertEquals(4, e.state.dungeon!!.y)
        assertEquals(rng, e.state.rng)
    }

    @Test
    fun ignoredChestCanBeOpenedWhenRevisitedAndDoesNotLoseLoot() {
        val e = corridor()
        val d = e.state.dungeon!!
        d.here().event = Event.CHEST
        assertEquals("상자 살펴보기", e.controls.buttons()[0].label)
        e.press(1)
        assertFalse(d.here().resolved)
        assertTrue(e.controls.buttons()[0].label.contains("앞으로"))
        e.press(0)
        e.press(0) // at the dead end, the sole legal direction is backward
        assertEquals(4, d.y)
        assertEquals(Mode.ENCOUNTER, e.state.mode)
        assertEquals("열기", e.controls.buttons()[0].label)
        e.state.rng = 1L
        e.press(0)
        assertTrue(d.here().resolved)
        assertTrue(e.state.inventory.isNotEmpty())
    }

    @Test
    fun attackAndDefenceButtonsImmediatelyChangeTheCombatTurn() {
        for (button in 0..1) {
            val e = corridor()
            e.startBattle(false)
            e.state.enemy!!.hp = 100000.0
            assertEquals(listOf("일반 공격", "강공격"), e.controls.buttons().map { it.label })
            e.press(button)
            assertEquals(Mode.COMBAT_DEFEND, e.state.mode)
            assertEquals(listOf("방어", "회피"), e.controls.buttons().map { it.label })
            e.press(button)
            assertEquals(Mode.COMBAT_ATTACK, e.state.mode)
        }
    }

    @Test
    fun displayedHoldPotionConsumesOneItemAndTheAttackTurn() {
        val e = GameEngine()
        e.enterFloor(1)
        e.startBattle(false)
        e.state.hp = 40.0
        val count = e.state.inventory.count { it.kind == Kind.POTION }
        e.hold(0)
        assertEquals(count - 1, e.state.inventory.count { it.kind == Kind.POTION })
        assertTrue(e.state.hp > 40)
        assertEquals(Mode.COMBAT_DEFEND, e.state.mode)
    }

    @Test
    fun holdingPotionWithoutStockDoesNotConsumeATurn() {
        val e = corridor()
        e.startBattle(false)
        e.hold(0)
        assertEquals(Mode.COMBAT_ATTACK, e.state.mode)
    }

    @Test
    fun shopQuantityPurchaseUsesGoldAndUniqueIdsAndSurvivesSave() {
        val e = GameEngine()
        e.state.gold = 300
        choose(e, "shop")
        val potion = e.state.shop.first { it.kind == Kind.POTION }
        choose(e, "buy", potion.id)
        assertEquals("quantity", e.state.menu!!.type)
        val count = e.state.inventory.size
        val price = Equipment.buyPrice(e.state, potion)
        choose(e, "buyQuantity", 3)
        assertEquals(count + 3, e.state.inventory.size)
        assertEquals(300 - price * 3, e.state.gold)
        val restored = SaveCodec.decode(SaveCodec.encode(e.state))
        assertEquals(restored.inventory.size, restored.inventory.map { it.id }.distinct().size)
        val gold = e.state.gold
        e.state.gold = 0
        choose(e, "buyQuantity", 5)
        assertEquals(count + 3, e.state.inventory.size)
        assertEquals(0, e.state.gold)
        e.state.gold = gold
    }

    @Test
    fun inventoryAndCleanRecordsAreReachableFromDungeon() {
        val e = GameEngine()
        e.enterFloor(1)
        e.state.dungeon!!.here().resolved = true
        e.hold(0)
        assertEquals("inventory", e.state.menu!!.type)
        e.navigateBack()
        assertEquals(Mode.EXPLORE, e.state.mode)
        e.hold(1)
        assertEquals("help", e.state.menu!!.type)
        assertEquals(listOf("back"), e.choices().map { it.action })
        e.navigateBack()
        assertEquals(Mode.EXPLORE, e.state.mode)
    }

    @Test
    fun staleTouchChoiceCannotBuyOrEquipAnItemNoLongerInTheMenu() {
        val e = GameEngine()
        choose(e, "inventory")
        val old = e.choices().first { it.action == "item" }
        e.navigateBack()
        e.choose(old)
        assertEquals(Mode.SURFACE, e.state.mode)
    }

    @Test
    fun oldSaveWithSelectionStillRestoresToDirectButtons() {
        val e = corridor()
        e.state.selection = 9
        val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        restored.press(0)
        assertEquals(3, restored.state.dungeon!!.y)
    }

    @Test
    fun touchMenuActionsDoNotChangeTheMainStateDuringEquipmentPreview() {
        val e = GameEngine()
        val s = e.state
        val before = SaveCodec.encode(s)
        val item = s.inventory.first { it.weapon }
        val preview = s.copy(equipment = s.equipment.toMutableMap())
        Equipment.equip(preview, item.id, Slot.LEFT)
        assertEquals(before, SaveCodec.encode(s))
    }

    @Test
    fun stopGestureRemainsValidWhileAutomaticMovementAdvances() {
        val e = corridor()
        val d = e.state.dungeon!!
        d.cell(4, 2).wall = false
        d.cell(4, 1).wall = false
        e.press(0)
        assertEquals(Mode.MOVING, e.state.mode)
        val token = e.controls.inputToken()
        repeat(3) { e.tick(250) }
        assertEquals(Mode.MOVING, e.state.mode)
        assertEquals(token, e.controls.inputToken())
        e.press(1)
        assertEquals(Mode.EXPLORE, e.state.mode)
    }

    @Test
    fun retreatAfterBackwardEncounterUsesThePreviousCellEvenAfterReload() {
        val e = corridor()
        val d = e.state.dungeon!!
        d.cell(4, 5).event = Event.MONSTER
        e.press(1)
        assertEquals(Mode.COMBAT_ATTACK, e.state.mode)
        assertEquals(0, d.direction)
        val restored = GameEngine(SaveCodec.decode(SaveCodec.encode(e.state)))
        restored.state.rng = 1L
        restored.state.base = restored.state.base.copy(agility = 200.0)
        restored.hold(1)
        assertEquals(Mode.EXPLORE, restored.state.mode)
        assertEquals(4, restored.state.dungeon!!.x)
        assertEquals(4, restored.state.dungeon!!.y)
        assertEquals(0, restored.state.dungeon!!.direction)
    }
}
