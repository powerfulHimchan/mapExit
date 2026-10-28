package com.powerfulhimchan.mapexit.core

import org.junit.Assert.*
import org.junit.Test

class GameRulesTest {
    private fun state() = State(bootstrapped = true)

    private fun gear(s: State, k: Kind): Gear =
        ItemFactory.create(s, k, 1).also { s.inventory.add(it) }

    private fun tick(t: TrapState, ms: Long) {
        var left = ms
        while (left > 0) {
            val d = minOf(left, 50L)
            Traps.tick(t, d)
            left -= d
        }
    }

    private fun choose(e: GameEngine, action: String, arg: Long? = null) {
        val i =
            e.choices().indexOfFirst { it.action == action && (arg == null || it.argument == arg) }
        assertTrue("Missing action $action", i >= 0)
        e.choose(e.choices()[i])
    }

    @Test
    fun generatedMapsAreConnectedAndHaveAllEvents() {
        for (seed in 1L..50L) {
            val s = state().copy(rng = seed)
            val m = MapGenerator.generate(s, seed.toInt())
            val dist = MapGenerator.distances(m, 1, 1)
            m.cells.forEachIndexed { i, c ->
                if (!c.wall) assertTrue("Unreachable seed $seed cell $i", dist[i] >= 0)
            }
            assertEquals(1, m.cells.count { it.event == Event.EXIT })
            assertTrue(m.here().event == Event.NONE)
            for (event in
                listOf(
                    Event.MONSTER,
                    Event.CHEST,
                    Event.HEAL,
                    Event.MERCHANT,
                    Event.NPC,
                    Event.TRAP,
                )) assertTrue(m.cells.any { it.event == event })
        }
    }

    @Test
    fun sameSeedReplaysSameMapAndRng() {
        val a = state().copy(rng = 21)
        val b = state().copy(rng = 21)
        assertEquals(MapGenerator.generate(a, 10), MapGenerator.generate(b, 10))
        assertEquals(a.rng, b.rng)
    }

    @Test
    fun revisitsProduceNewMaps() {
        val s = state()
        val a = MapGenerator.generate(s, 1)
        val b = MapGenerator.generate(s, 1)
        assertNotEquals(a, b)
    }

    @Test
    fun twoHandedIsOneItemAndReplacingItClearsBothHands() {
        val s = state()
        val sword = gear(s, Kind.SWORD)
        val shield = gear(s, Kind.SHIELD)
        val spear = gear(s, Kind.SPEAR)
        Equipment.equip(s, sword.id, Slot.LEFT)
        Equipment.equip(s, shield.id, Slot.RIGHT)
        Equipment.equip(s, spear.id, Slot.LEFT)
        assertEquals(spear.id, s.equipment[Slot.LEFT])
        assertEquals(spear.id, s.equipment[Slot.RIGHT])
        assertEquals(1, Equipment.equipped(s).size)
        assertEquals(s.base.attack + spear.flat.attack, Equipment.stats(s).attack, .0001)
        Equipment.equip(s, sword.id, Slot.RIGHT)
        assertNull(s.equipment[Slot.LEFT])
        assertEquals(3, s.inventory.size)
    }

    @Test
    fun clawsAreSeparateAndShieldWorksInEitherHand() {
        for (slot in listOf(Slot.LEFT, Slot.RIGHT)) {
            val s = state()
            val a = gear(s, Kind.CLAW)
            val b = gear(s, Kind.CLAW)
            val shield = gear(s, Kind.SHIELD)
            val other = if (slot == Slot.LEFT) Slot.RIGHT else Slot.LEFT
            Equipment.equip(s, a.id, slot)
            Equipment.equip(s, b.id, other)
            assertEquals(2, Equipment.equipped(s).size)
            Equipment.equip(s, shield.id, other)
            assertEquals(a.id, s.equipment[slot])
            assertEquals(shield.id, s.equipment[other])
        }
    }

    @Test
    fun oneRingCannotBeDuplicatedAndOrderDoesNotMatter() {
        val s = state()
        val a = gear(s, Kind.RING)
        val b = gear(s, Kind.RING)
        Equipment.equip(s, a.id, Slot.RING1)
        Equipment.equip(s, a.id, Slot.RING2)
        assertEquals(1, s.equipment.size)
        Equipment.equip(s, b.id, Slot.RING1)
        val first = Equipment.stats(s)
        s.equipment.clear()
        Equipment.equip(s, a.id, Slot.RING1)
        Equipment.equip(s, b.id, Slot.RING2)
        assertEquals(first, Equipment.stats(s))
    }

    @Test
    fun maxHpEquipmentDoesNotHealOnReequip() {
        val s = state()
        val ring = Gear(1, Kind.RING, "체력 반지", percent = Stats.zero().copy(hp = .5))
        s.inventory.add(ring)
        s.hp = 50.0
        repeat(4) {
            Equipment.equip(s, 1, Slot.RING1)
            Equipment.unequip(s, 1)
        }
        assertEquals(50.0, s.hp, .001)
    }

    @Test
    fun necklacesNeverChangeSevenStats() {
        val s = state()
        val base = Equipment.stats(s)
        for (c in Charm.entries.filter { it != Charm.NONE }) {
            val n = Gear(c.ordinal.toLong(), Kind.NECKLACE, "목걸이", charm = c)
            s.inventory.add(n)
            Equipment.equip(s, n.id, Slot.NECK)
            assertEquals(base, Equipment.stats(s))
            assertEquals(c, Equipment.charm(s))
        }
    }

    @Test
    fun merchantDiscountCannotCreateBuySellProfit() {
        val s = state()
        val n = Gear(99, Kind.NECKLACE, "상인의 목걸이", charm = Charm.MERCHANT)
        s.inventory.add(n)
        Equipment.equip(s, 99, Slot.NECK)
        for (price in 1..500) {
            val item = Gear(price.toLong(), Kind.SWORD, "검", price = price)
            assertTrue(Equipment.sellPrice(item) <= Equipment.buyPrice(s, item))
        }
    }

    @Test
    fun weaponModifierDirectionsHold() {
        val s = state()
        for (k in listOf(Kind.SWORD, Kind.SPEAR, Kind.HAMMER, Kind.CLAW, Kind.WHIP)) {
            val g = gear(s, k)
            assertTrue(g.flat.attack > 0)
            when (k) {
                Kind.SWORD -> assertTrue(g.flat.critDamage > 0)
                Kind.SPEAR -> {
                    assertTrue(g.flat.defense < 0)
                    assertTrue(g.flat.crit < 0)
                }
                Kind.HAMMER -> {
                    assertTrue(g.flat.agility < 0)
                    assertTrue(g.flat.defense < 0)
                    assertTrue(g.flat.crit < 0)
                }
                Kind.CLAW -> assertTrue(g.flat.agility > 0)
                Kind.WHIP -> {
                    assertTrue(g.flat.agility > 0)
                    assertTrue(g.flat.critDamage > 0)
                    assertTrue(g.flat.crit < 0)
                }
                else -> Unit
            }
        }
    }

    @Test
    fun deathRetainsGrowthBankDevicesButLosesEveryItem() {
        val s =
            state().copy(base = Stats(attack = 210.0), level = 7, points = 2, gold = 90, bank = 700)
        val old = gear(s, Kind.SWORD)
        old.inherited = true
        s.devices.add(10)
        s.quest = Quest("의뢰", "kill", 3)
        val e = GameEngine(s)
        e.die()
        assertEquals(210.0, s.base.attack, .001)
        assertEquals(7, s.level)
        assertEquals(700, s.bank)
        assertTrue(10 in s.devices)
        assertEquals(0, s.gold)
        assertTrue(s.inventory.isEmpty())
        assertTrue(s.equipment.isEmpty())
        assertNull(s.quest)
        assertEquals(Mode.SURFACE, s.mode)
    }

    @Test
    fun newCycleKeepsOldInheritanceAndAlwaysAddsOne() {
        val s = state().copy(level = 8, gold = 500, bank = 1000, points = 8)
        val e = GameEngine(s)
        val old = gear(s, Kind.RING)
        old.inherited = true
        val fresh = gear(s, Kind.SPEAR)
        gear(s, Kind.POTION)
        s.devices.add(10)
        s.base = s.base.copy(attack = 300.0)
        e.beginInheritance()
        assertTrue(e.choices().none { it.argument == old.id })
        e.newCycle(fresh.id)
        assertEquals(setOf(old.id, fresh.id), s.inventory.map { it.id }.toSet())
        assertTrue(s.inventory.all { it.inherited })
        assertTrue(s.equipment.isEmpty())
        assertEquals(Stats(), s.base)
        assertEquals(0, s.gold)
        assertEquals(0, s.bank)
        assertTrue(s.devices.isEmpty())
        assertEquals(1, s.level)
        assertEquals(0, s.points)
        assertEquals(2, s.cycle)
        val next = gear(s, Kind.CLAW)
        e.beginInheritance()
        e.newCycle(next.id)
        assertEquals(3, s.inventory.size)
        assertEquals(3, s.cycle)
    }

    @Test
    fun inheritanceCannotBeProcessedTwiceWithSameId() {
        val s = state()
        val e = GameEngine(s)
        val g = gear(s, Kind.SWORD)
        e.newCycle(g.id)
        e.newCycle(g.id)
        assertEquals(2, s.cycle)
        assertEquals(1, s.inventory.size)
    }

    @Test
    fun inheritanceProvidesCandidateIfAllExistingGearIsInherited() {
        val s = state()
        gear(s, Kind.RING).inherited = true
        val e = GameEngine(s)
        e.beginInheritance()
        assertEquals(1, e.choices().size)
        assertFalse(s.inventory.first { it.id == e.choices()[0].argument }.inherited)
    }

    @Test
    fun lethalAttackNeverTriggersRetaliation() {
        val s = state()
        val e = GameEngine(s)
        e.enterFloor(1)
        e.startBattle(false)
        s.enemy!!.hp = .01
        s.base = s.base.copy(crit = 0.0)
        val hp = s.hp
        repeat(20) { if (s.enemy != null) e.attack(false) }
        assertNull(s.enemy)
        assertEquals(hp, s.hp, .001)
    }

    @Test
    fun attackAndDefenseAlternate() {
        val s = state()
        val e = GameEngine(s)
        e.startBattle(false)
        s.enemy!!.hp = 999999.0
        e.attack(false)
        assertEquals(Mode.COMBAT_DEFEND, s.mode)
        val hp = s.enemy!!.hp
        e.attack(false)
        assertEquals(hp, s.enemy!!.hp, .001)
        e.defend(false)
        assertEquals(Mode.COMBAT_ATTACK, s.mode)
    }

    @Test
    fun failedFleeGoesToDefenseInsteadOfDirectDamage() {
        val s = state()
        val e = GameEngine(s)
        e.startBattle(false)
        s.base = s.base.copy(agility = 0.0)
        var failed = false
        repeat(30) {
            if (!failed) {
                e.startBattle(false)
                val hp = s.hp
                choose(e, "flee")
                if (s.mode == Mode.COMBAT_DEFEND) {
                    assertEquals(hp, s.hp, .001)
                    failed = true
                }
            }
        }
        assertTrue(failed)
    }

    @Test
    fun bossDeviceUnlocksOnlyAfterVictory() {
        val s = state()
        val e = GameEngine(s)
        e.enterFloor(10)
        e.startBattle(true)
        assertTrue(s.devices.isEmpty())
        s.enemy!!.hp = .01
        repeat(20) {
            if (s.enemy != null) {
                e.attack(false)
                if (s.mode == Mode.COMBAT_DEFEND) e.defend(false)
            }
        }
        assertTrue(10 in s.devices)
        assertEquals(Mode.VICTORY, s.mode)
        choose(e, "battleContinue")
        assertEquals(Mode.FLOOR_CLEAR, s.mode)
    }

    @Test
    fun fiftyEndsWithInheritanceAndNeverFloor51() {
        val s = state()
        val e = GameEngine(s)
        e.enterFloor(50)
        s.mode = Mode.FLOOR_CLEAR
        assertTrue(e.choices().none { it.action == "enter" })
        choose(e, "ending")
        assertEquals(Mode.INHERIT, s.mode)
        assertTrue(e.choices().isNotEmpty())
    }

    @Test
    fun saveRoundTripIncludesRngTrapAndInventory() {
        val s = state()
        val e = GameEngine(s)
        e.enterFloor(30)
        gear(s, Kind.CLAW).inherited = true
        s.trap = Traps.create(s, 3)
        s.mode = Mode.TRAP
        s.quest = Quest("의뢰", "kill", 5, 2)
        val restored = SaveCodec.decode(SaveCodec.encode(s))
        assertEquals(s, restored)
        assertEquals(s.random(), restored.random(), 0.0)
    }

    @Test
    fun quickPatternCorrectWrongAndDeadline() {
        val s = state()
        val t = Traps.create(s, 0)
        tick(t, Traps.PREPARE_MS)
        for (b in t.pattern) {
            Traps.down(t, b)
            Traps.up(t, b)
        }
        assertTrue(t.success)
        val wrong = Traps.create(s, 0)
        tick(wrong, Traps.PREPARE_MS)
        Traps.down(wrong, 1 - wrong.pattern[0])
        assertEquals(TrapPhase.RESULT, wrong.phase)
        assertFalse(wrong.success)
        val late = Traps.create(s, 0)
        tick(late, Traps.PREPARE_MS + late.limitMs + 1)
        assertEquals(TrapPhase.RESULT, late.phase)
        assertFalse(late.success)
    }

    @Test
    fun timingBarUsesPositionNotFrameRate() {
        val s = state()
        val t = Traps.create(s, 1)
        tick(t, Traps.PREPARE_MS)
        tick(t, (t.target * 1000).toLong())
        Traps.down(t, t.pattern[0])
        assertTrue(t.success)
        val wrong = Traps.create(s, 1)
        tick(wrong, Traps.PREPARE_MS)
        Traps.down(wrong, wrong.pattern[0])
        assertFalse(wrong.success)
    }

    @Test
    fun mashRequiresReleaseAndIgnoresAutorepeat() {
        val t = Traps.create(state(), 2)
        tick(t, Traps.PREPARE_MS)
        repeat(50) { Traps.down(t, t.pattern[0]) }
        assertEquals(0, t.progress)
        Traps.up(t, t.pattern[0])
        assertEquals(1, t.progress)
        repeat(t.required - 1) {
            Traps.down(t, t.pattern[0])
            Traps.up(t, t.pattern[0])
        }
        assertTrue(t.success)
    }

    @Test
    fun memoryDoesNotAcceptInputWhileRevealed() {
        val t = Traps.create(state(), 3)
        tick(t, Traps.PREPARE_MS)
        assertEquals(TrapPhase.REVEAL, t.phase)
        Traps.down(t, t.pattern[0])
        assertEquals(0, t.progress)
        tick(t, t.revealMs)
        for (b in t.pattern) {
            Traps.down(t, b)
            Traps.up(t, b)
        }
        assertTrue(t.success)
    }

    @Test
    fun holdReleaseSupportsEarlyCorrectLateAndPause() {
        fun start(): TrapState =
            Traps.create(state(), 4).also {
                tick(it, Traps.PREPARE_MS)
                Traps.down(it, it.pattern[0])
            }
        val early = start()
        tick(early, 100)
        Traps.up(early, early.pattern[0])
        assertFalse(early.success)
        val ok = start()
        tick(ok, ok.signalMs)
        Traps.up(ok, ok.pattern[0])
        assertTrue(ok.success)
        val late = start()
        tick(late, late.signalMs + late.releaseWindowMs + 1)
        assertFalse(late.success)
        assertEquals(TrapPhase.RESULT, late.phase)
        val paused = start()
        tick(paused, 300)
        Traps.pause(paused)
        val time = paused.elapsedMs
        tick(paused, 5000)
        assertEquals(time, paused.elapsedMs)
        Traps.down(paused, paused.pattern[0])
        tick(paused, paused.signalMs - 300)
        Traps.up(paused, paused.pattern[0])
        assertTrue(paused.success)
    }

    @Test
    fun trapDamageAppliesOnlyOnceAndDeathsUseSharedRule() {
        val s = state()
        val e = GameEngine(s)
        s.trap = Traps.create(s, 0)
        s.mode = Mode.TRAP
        repeat(200) { e.tick(50) }
        val hp = s.hp
        repeat(50) { e.tick(50) }
        assertEquals(hp, s.hp, .001)
        s.hp = 1.0
        s.trap = Traps.create(s, 0)
        s.mode = Mode.TRAP
        repeat(200) { e.tick(50) }
        assertEquals(Mode.SURFACE, s.mode)
        assertNull(s.trap)
    }

    @Test
    fun debugModeDoesNotReplaceNormalProgress() {
        val e = GameEngine(state().copy(gold = 55), true)
        choose(e, "debug")
        assertTrue(e.state.debug)
        choose(e, "testGear")
        assertTrue(e.state.gold > 55)
        choose(e, "debugExit")
        assertEquals(55, e.state.gold)
        assertFalse(e.state.debug)
    }

    @Test
    fun automaticMovementStopsAtJunction() {
        val s = state()
        val e = GameEngine(s)
        e.enterFloor(1)
        val m = s.dungeon!!
        choose(e, "move", MapGenerator.directions(m).first().toLong())
        repeat(100) { e.tick(200) }
        assertNotEquals(Mode.MOVING, s.mode)
    }

    @Test
    fun lastPatternButtonDoesNotDismissTrapResult() {
        val s = state()
        val e = GameEngine(s)
        s.trap = Traps.create(s, 0).copy(pattern = listOf(0, 1))
        s.mode = Mode.TRAP
        repeat(24) { e.tick(50) }
        e.rawDown(0)
        e.rawUp(0)
        e.rawDown(1)
        e.rawUp(1)
        assertEquals(Mode.TRAP, s.mode)
        assertEquals(TrapPhase.RESULT, s.trap!!.phase)
        e.rawDown(1)
        e.rawUp(1)
        assertEquals(Mode.TRAP, s.mode)
        repeat(4) { e.tick(250) }
        assertEquals(Mode.SURFACE, s.mode)
    }

    @Test
    fun debugBattleWithoutMapReturnsToUsableSurface() {
        val s = state()
        val e = GameEngine(s)
        e.startBattle(false)
        s.enemy!!.hp = .001
        repeat(30) {
            if (s.enemy != null) {
                s.mode = Mode.COMBAT_ATTACK
                e.attack(false)
            }
        }
        assertEquals(Mode.VICTORY, s.mode)
        choose(e, "battleContinue")
        assertEquals(Mode.SURFACE, s.mode)
        assertTrue(e.choices().isNotEmpty())
    }

    @Test
    fun lastFloorCannotGenerateImmediatelyRepeatableNextFloorQuest() {
        val e = GameEngine(state())
        e.enterFloor(50)
        e.state.mode = Mode.MENU
        e.state.menu = Menu("npc", returnMode = Mode.EXPLORE)
        assertTrue(e.choices().none { it.action == "questFloor" })
    }

    @Test
    fun fiftyFloorStateFlowWithInvulnerableFixture() {
        val s = state().copy(base = Stats(hp = 1000000.0, attack = 1000000.0))
        val e = GameEngine(s)
        e.enterFloor(1)
        val reached = mutableSetOf<Int>()
        var steps = 0
        while (s.clears == 0 && steps++ < 100000) {
            reached.add(s.floor)
            s.hp = e.stats().hp
            when (s.mode) {
                Mode.EXPLORE -> {
                    val m = s.dungeon!!
                    val exit = m.cells.indexOfFirst { it.event == Event.EXIT }
                    val dist = MapGenerator.distances(m, exit % m.width, exit / m.width)
                    val d =
                        MapGenerator.directions(m).minBy { dir ->
                            dist[
                                (m.y + MapGenerator.dy[dir]) * m.width + m.x + MapGenerator.dx[dir]]
                        }
                    choose(e, "move", d.toLong())
                }
                Mode.MOVING -> e.tick(250)
                Mode.COMBAT_ATTACK -> e.attack(false)
                Mode.COMBAT_DEFEND -> e.defend(false)
                Mode.VICTORY -> choose(e, "battleContinue")
                Mode.ENCOUNTER -> choose(e, "encounterLeave")
                Mode.TRAP_GUIDE,
                Mode.TRAP -> e.tick(250)
                Mode.FLOOR_CLEAR ->
                    if (s.floor == 50) choose(e, "ending")
                    else choose(e, "enter", (s.floor + 1).toLong())
                Mode.INHERIT -> e.choose(e.choices().first())
                else -> fail("Unexpected mode ${s.mode}")
            }
        }
        assertTrue("Flow stalled", steps < 100000)
        assertEquals((1..50).toSet(), reached)
        assertEquals(2, s.cycle)
        assertEquals(Mode.SURFACE, s.mode)
        assertEquals(1, s.inventory.size)
        assertTrue(s.inventory.single().inherited)
        assertEquals(Stats(), s.base)
        assertTrue(s.devices.isEmpty())
    }
}
