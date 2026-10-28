package com.powerfulhimchan.mapexit

import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.powerfulhimchan.mapexit.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun find(node: AccessibilityNodeInfo?, label: String): Rect? {
        if (node == null) return null
        if (node.contentDescription?.toString() == label || node.text?.toString() == label)
            return Rect().also { node.getBoundsInScreen(it) }
        for (i in 0 until node.childCount) find(node.getChild(i), label)?.let {
            return it
        }
        return null
    }

    private fun scroll(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
            return true
        for (i in 0 until node.childCount) if (scroll(node.getChild(i))) return true
        return false
    }

    private fun bounds(label: String, allowScroll: Boolean = false): Rect {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            find(root, label)?.let { if (it.width() > 0 && it.height() > 0) return it }
            if (allowScroll) scroll(root)
            SystemClock.sleep(100)
        }
        error("화면에서 찾을 수 없음: $label")
    }

    private fun tap(label: String, held: Boolean = false, allowScroll: Boolean = false) {
        val button = bounds(label, allowScroll)
        SystemClock.sleep(
            200
        ) // Deliberate second gestures; rapid queued release is guarded by the app.
        val now = SystemClock.uptimeMillis()
        fun event(action: Int, time: Long) {
            MotionEvent.obtain(now, time, action, button.exactCenterX(), button.exactCenterY(), 0)
                .also {
                    instrumentation.sendPointerSync(it)
                    it.recycle()
                }
        }
        event(MotionEvent.ACTION_DOWN, now)
        SystemClock.sleep(if (held) 700 else 60)
        event(MotionEvent.ACTION_UP, SystemClock.uptimeMillis())
        instrumentation.waitForIdleSync()
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250)
        val descriptor =
            instrumentation.uiAutomation.executeShellCommand(
                "screencap -p /sdcard/Download/mapexit-$name.png"
            )
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun assertVictoryFeedbackLayout(scenario: ActivityScenario<MainActivity>) {
        var popupLabel = ""
        scenario.onActivity { popupLabel = "적 피해 ${it.game.engine.state.lastImpact!!.label()}" }
        val footer = bounds("B로 결과 확인 후 계속")
        val popup = bounds(popupLabel)
        assertTrue("Final damage must sit below the victory card", popup.top >= footer.bottom)
        assertNull(
            "Victory replaces the combat header",
            find(instrumentation.uiAutomation.rootInActiveWindow, "3인칭 전투"),
        )
    }

    @Test
    fun preparationDirectActionsSaveAndSceneLayout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.game.active = false
                it.game.engine = GameEngine(State(gold = 300))
                it.game.refresh()
            }
            bounds("던전 진입")
            capture("preparation")
            tap("상점")
            var potionLabel = ""
            scenario.onActivity { activity ->
                assertEquals("shop", activity.game.engine.state.menu?.type)
                potionLabel =
                    activity.game.engine
                        .choices()
                        .first { c ->
                            activity.game.engine.state.shop.any {
                                it.id == c.argument && it.kind == Kind.POTION
                            }
                        }
                        .label
            }
            tap(potionLabel, allowScroll = true)
            var buyLabel = ""
            scenario.onActivity { activity ->
                buyLabel =
                    activity.game.engine
                        .choices()
                        .first { it.action == "buyQuantity" && it.argument == 3L }
                        .label
            }
            tap(buyLabel)
            capture("shop")
            scenario.onActivity {
                assertEquals(
                    5,
                    it.game.engine.state.inventory.count { gear -> gear.kind == Kind.POTION },
                )
                it.game.back()
                it.game.back()
            }
            tap("장비")
            bounds("장비 장착")
            var slotLabel = ""
            var swordId = 0L
            scenario.onActivity { activity ->
                swordId = activity.game.engine.state.equipment[Slot.RIGHT]!!
                slotLabel =
                    activity.game.engine
                        .choices()
                        .first {
                            it.action == "slotItem" && it.argument == Slot.RIGHT.ordinal.toLong()
                        }
                        .label
            }
            tap(slotLabel, allowScroll = true)
            capture("equipment")
            tap("왼손에 장착", allowScroll = true)
            scenario.onActivity {
                assertEquals(swordId, it.game.engine.state.equipment[Slot.LEFT])
                assertNull(it.game.engine.state.equipment[Slot.RIGHT])
                assertEquals(Mode.SURFACE, it.game.engine.state.mode)
            }
            tap("던전 진입")
            scenario.onActivity {
                assertEquals(Mode.EXPLORE, it.game.engine.state.mode)
                assertEquals(1, it.game.engine.state.floor)
                it.game.pause()
                val saved = SaveCodec.decode(File(it.filesDir, "mapexit-save-v1.json").readText())
                assertEquals(1, saved.floor)
                assertEquals(5, saved.inventory.count { gear -> gear.kind == Kind.POTION })
                val coldModel = GameModel(it.application)
                assertEquals(1, coldModel.engine.state.floor)
                assertEquals(saved.equipment, coldModel.engine.state.equipment)
                assertEquals(
                    5,
                    coldModel.engine.state.inventory.count { gear -> gear.kind == Kind.POTION },
                )
            }
            val main = bounds("메인 장면")
            val map = bounds("주변 미니맵")
            assertTrue("Main scene must dominate the screen", main.height() > map.height() * 3)
            assertTrue(
                "Map must use less than half the scene width",
                map.width() < main.width() / 2,
            )
            capture("explore")
            scenario.recreate()
            scenario.onActivity {
                assertEquals(1, it.game.engine.state.floor)
                it.game.active = false
            }
            var movementLabel = ""
            var before = 0 to 0
            scenario.onActivity {
                val d = it.game.engine.state.dungeon!!
                before = d.x to d.y
                val index =
                    it.game.engine.controls.buttons().indexOfFirst { b ->
                        b.choice?.action == "move"
                    }
                assertTrue(index >= 0)
                val b = it.game.engine.controls.buttons()[index]
                movementLabel = "${if(index==0) "A" else "B"} ${b.label}"
            }
            tap(movementLabel)
            scenario.onActivity {
                val d = it.game.engine.state.dungeon!!
                assertNotEquals(before, d.x to d.y)
            }
            scenario.onActivity {
                it.game.engine.startBattle(false)
                it.game.engine.state.enemy =
                    it.game.engine.state.enemy!!.copy(maxHp = 2000.0, hp = 2000.0)
                it.game.active = false
                it.game.refresh()
            }
            tap("A 일반 공격")
            scenario.onActivity { assertEquals(Mode.COMBAT_DEFEND, it.game.engine.state.mode) }
            tap("B 회피")
            scenario.onActivity { assertEquals(Mode.COMBAT_ATTACK, it.game.engine.state.mode) }
            tap("A 일반 공격", held = true)
            scenario.onActivity {
                assertEquals(Mode.COMBAT_DEFEND, it.game.engine.state.mode)
                assertEquals(
                    4,
                    it.game.engine.state.inventory.count { gear -> gear.kind == Kind.POTION },
                )
            }
            capture("battle")
            scenario.onActivity {
                val s = it.game.engine.state
                s.enemy = null
                s.trap = Traps.create(s, 1)
                Traps.tick(s.trap!!, 1200)
                s.mode = Mode.TRAP
                it.game.refresh()
            }
            bounds("메인 장면")
            capture("trap")
        }
    }

    @Test
    fun combatFeedbackVictoryAndAllAutomaticTrapCues() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.game.active = false
                val e = GameEngine()
                e.enterFloor(1)
                it.game.engine = e
                it.game.refresh()
            }
            bounds("메인 장면")
            capture("wall-depth")
            scenario.onActivity {
                it.game.engine.startBattle(false)
                it.game.engine.state.enemy =
                    it.game.engine.state.enemy!!.copy(maxHp = 2000.0, hp = 2000.0)
                it.game.engine.state.rng = 0L
                it.game.refresh()
            }
            bounds("3인칭 전투")
            bounds("적 HP 2000 / 2000")
            SystemClock.sleep(500)
            capture("third-person")
            tap("A 일반 공격")
            var popup = ""
            scenario.onActivity { popup = "적 피해 ${it.game.engine.state.lastImpact!!.label()}" }
            bounds(popup)
            capture("enemy-damage")
            tap("A 방어")
            scenario.onActivity {
                assertEquals(ImpactTarget.PLAYER, it.game.engine.state.lastImpact!!.target)
                popup = "플레이어 피해 ${it.game.engine.state.lastImpact!!.label()}"
            }
            bounds(popup)
            capture("player-damage")
            scenario.onActivity {
                it.game.engine.state.enemy!!.hp = 1.0
                it.game.engine.state.rng = 0L
                it.game.refresh()
            }
            tap("B 강공격")
            bounds("전투 승리")
            assertVictoryFeedbackLayout(scenario)
            capture("victory")
            var gold = 0
            scenario.onActivity {
                val e = it.game.engine
                assertEquals(Mode.VICTORY, e.state.mode)
                gold = e.state.gold
                it.game.pause()
                val cold = GameModel(it.application)
                assertEquals(Mode.VICTORY, cold.engine.state.mode)
                assertEquals(gold, cold.engine.state.gold)
                assertNotNull(cold.engine.state.battleResult)
                assertNull(cold.currentImpact)
            }
            tap("B 결과 확인 · 계속")
            scenario.onActivity {
                assertEquals(Mode.EXPLORE, it.game.engine.state.mode)
                assertEquals(gold, it.game.engine.state.gold)
            }
            for (type in 0..4) {
                var cue = ""
                scenario.onActivity {
                    val e = GameEngine(debugTools = true)
                    e.choose(e.choices().first { c -> c.action == "debug" })
                    e.choose(
                        e.choices().first { c ->
                            c.action == "testTrap" && c.argument == type.toLong()
                        }
                    )
                    assertEquals(Mode.TRAP, e.state.mode)
                    assertEquals(TrapPhase.PREPARE, e.state.trap!!.phase)
                    cue = e.state.trap!!.cue()
                    it.game.engine = e
                    it.game.active = false
                    it.game.refresh()
                }
                bounds(cue)
                capture("trap-cue-$type")
                scenario.onActivity {
                    repeat(4) { _ -> it.game.engine.tick(250) }
                    assertEquals(
                        if (type == 3) TrapPhase.REVEAL else TrapPhase.ACTIVE,
                        it.game.engine.state.trap!!.phase,
                    )
                    assertEquals(0, it.game.engine.state.trap!!.progress)
                    it.game.refresh()
                }
            }
        }
    }

    @Test
    fun compactScreenKeepsBothActionsAndMainSceneVisible() {
        val descriptor = instrumentation.uiAutomation.executeShellCommand("wm size 720x1280")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.game.active = false
                    it.game.engine = GameEngine()
                    it.game.refresh()
                }
                tap("던전 진입")
                scenario.onActivity {
                    it.game.engine.startBattle(false)
                    it.game.refresh()
                }
                val a = bounds("A 일반 공격")
                val b = bounds("B 강공격")
                val main = bounds("메인 장면")
                val screenshot = instrumentation.uiAutomation.takeScreenshot()!!
                assertTrue(a.bottom <= screenshot.height && b.bottom <= screenshot.height)
                assertTrue(a.height() >= 72 && b.height() >= 72)
                assertTrue(
                    "Main scene must keep a majority of compact play space",
                    main.height() > (a.bottom - main.top) / 2,
                )
                screenshot.recycle()
                tap("B 강공격")
                scenario.onActivity { assertEquals(Mode.COMBAT_DEFEND, it.game.engine.state.mode) }
                tap("A 방어")
                scenario.onActivity { assertEquals(Mode.COMBAT_ATTACK, it.game.engine.state.mode) }
                capture("compact")
                scenario.onActivity {
                    it.game.engine.state.enemy!!.hp = 1.0
                    it.game.engine.state.rng = 0L
                    it.game.refresh()
                }
                tap("A 일반 공격")
                bounds("전투 승리")
                assertVictoryFeedbackLayout(scenario)
                capture("compact-victory")
                tap("B 결과 확인 · 계속")
                scenario.onActivity {
                    val s = it.game.engine.state
                    s.trap = Traps.create(s, 4)
                    s.mode = Mode.TRAP
                    it.game.refresh()
                }
                bounds("신호에 놓아라")
                capture("compact-cue")
            }
        } finally {
            val reset = instrumentation.uiAutomation.executeShellCommand("wm size reset")
            ParcelFileDescriptor.AutoCloseInputStream(reset).use { it.readBytes() }
        }
    }

    private fun encounterEngine(event: Event): GameEngine {
        val e = GameEngine()
        val d = Dungeon(7, 7, MutableList(49) { Cell() }, x = 3, y = 3, direction = 0)
        d.here().wall = false
        d.cell(3, 2).wall = false
        d.cell(3, 2).event = event
        e.state.dungeon = d
        e.state.floor = 1
        e.state.mode = Mode.EXPLORE
        return e
    }

    private fun awaitGame(scenario: ActivityScenario<MainActivity>, condition: (State) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            var matched = false
            scenario.onActivity { matched = condition(it.game.engine.state) }
            if (matched) return
            SystemClock.sleep(40)
        }
        fail("Timed out waiting for the automatic game transition")
    }

    @Test
    fun encounterChoicesAndMerchantSaleRefreshOnScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.game.active = false
                it.game.engine = encounterEngine(Event.NPC)
                it.game.refresh()
            }
            tap("A ↑ 앞으로 이동")
            bounds("여행자의 의뢰")
            bounds("A 수락")
            bounds("B 거절")
            capture("npc-encounter")
            tap("B 거절")
            scenario.onActivity { assertNull(it.game.engine.state.quest) }
            // Reset only transient controls; the persisted NPC offer remains the same.
            scenario.onActivity {
                it.game.engine.controls.reset()
                it.game.refresh()
            }
            tap("A NPC 만나기")
            tap("A 수락")
            scenario.onActivity {
                assertNotNull(it.game.engine.state.quest)
                assertEquals(Mode.EXPLORE, it.game.engine.state.mode)
                it.game.engine = encounterEngine(Event.CHEST)
                it.game.refresh()
            }
            tap("A ↑ 앞으로 이동")
            bounds("상자 발견")
            bounds("A 열기")
            bounds("B 지나가기")
            capture("chest-encounter")
            scenario.onActivity { it.game.engine.state.rng = 0L }
            tap("A 열기")
            bounds("3인칭 전투")
            scenario.onActivity { assertTrue(it.game.engine.state.enemy!!.name.startsWith("상자 속")) }
            capture("chest-ambush")
            scenario.onActivity {
                val e = encounterEngine(Event.MERCHANT)
                val sword = e.state.inventory.first { gear -> gear.weapon }
                e.state.inventory.add(sword.copy(id = e.state.nextId++))
                it.game.engine = e
                it.game.refresh()
            }
            tap("A ↑ 앞으로 이동")
            bounds("떠돌이 상인")
            capture("merchant-encounter")
            tap("A 거래하기")
            tap("아이템 판매", allowScroll = true)
            var wornLabel = ""
            var duplicateLabel = ""
            var wornId = 0L
            var gold = 0
            var price = 0
            scenario.onActivity {
                val e = it.game.engine
                wornId = e.state.equipment[Slot.RIGHT]!!
                wornLabel =
                    e.choices().first { c -> c.action == "sell" && c.argument == wornId }.label
                val twin = e.state.inventory.last { gear -> gear.weapon }
                duplicateLabel =
                    e.choices().first { c -> c.action == "sell" && c.argument == twin.id }.label
                gold = e.state.gold
                price = Equipment.sellPrice(e.state.inventory.first { gear -> gear.id == wornId })
                assertTrue(wornLabel.contains("장착 중"))
                assertFalse(duplicateLabel.contains("장착 중"))
            }
            bounds(wornLabel, allowScroll = true)
            capture("sell-equipped")
            tap(wornLabel, allowScroll = true)
            bounds(duplicateLabel, allowScroll = true)
            scenario.onActivity {
                val e = it.game.engine
                assertEquals(gold + price, e.state.gold)
                assertFalse(e.state.inventory.any { gear -> gear.id == wornId })
                assertFalse(wornId in e.state.equipment.values)
                assertFalse(e.choices().any { c -> c.action == "sell" && c.argument == wornId })
                it.game.pause()
                val cold = GameModel(it.application)
                assertFalse(cold.engine.state.inventory.any { gear -> gear.id == wornId })
            }
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, wornLabel))
            capture("sell-removed")
            scenario.onActivity {
                it.game.back()
                it.game.back()
            }
            bounds("떠돌이 상인")
            tap("B 지나가기")
            scenario.onActivity { assertEquals(Mode.EXPLORE, it.game.engine.state.mode) }
        }
    }

    @Test
    fun trapsUseLiveTimersAndReturnWithoutConfirmation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (type in 0..1) {
                var pattern = emptyList<Int>()
                scenario.onActivity {
                    val e = encounterEngine(Event.TRAP)
                    e.press(0)
                    e.state.trap = Traps.create(e.state, type)
                    pattern = e.state.trap!!.pattern
                    it.game.engine = e
                    it.game.active = true
                    it.game.refresh()
                }
                awaitGame(scenario) { it.trap?.phase == TrapPhase.ACTIVE }
                if (type == 0) {
                    for (b in pattern) tap(if (b == 0) "A A 입력" else "B B 입력")
                }
                awaitGame(scenario) { it.trap?.phase == TrapPhase.RESULT }
                scenario.onActivity { assertEquals(type == 0, it.game.engine.state.trap!!.success) }
                capture(if (type == 0) "trap-auto-success" else "trap-auto-failure")
                awaitGame(scenario) { it.mode == Mode.EXPLORE }
                scenario.onActivity {
                    assertNull(it.game.engine.state.trap)
                    assertTrue(it.game.engine.state.dungeon!!.here().resolved)
                    it.game.active = false
                }
            }
        }
    }

    @Test
    fun fineArtCoversAllWeaponsEnemiesAndFacilities() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (kind in listOf(Kind.SWORD, Kind.SPEAR, Kind.HAMMER, Kind.CLAW, Kind.WHIP)) {
                scenario.onActivity {
                    val e = GameEngine()
                    e.enterFloor(1)
                    e.state.equipment.clear()
                    val weapon = ItemFactory.create(e.state, kind)
                    e.state.inventory.add(weapon)
                    Equipment.equip(e.state, weapon.id, Slot.RIGHT)
                    if (!weapon.twoHanded) {
                        val shield = e.state.inventory.first { gear -> gear.kind == Kind.SHIELD }
                        Equipment.equip(e.state, shield.id, Slot.LEFT)
                    }
                    e.startBattle(false)
                    e.state.enemy =
                        e.state.enemy!!.copy(name = "동굴 슬라임", hp = 2000.0, maxHp = 2000.0)
                    it.game.engine = e
                    it.game.active = false
                    it.game.refresh()
                }
                bounds("3인칭 전투")
                SystemClock.sleep(500)
                capture("weapon-${kind.name.lowercase()}")
            }
            for ((name, id) in
                listOf("녹슨 기사" to "knight", "심연 박쥐" to "bat", "10층의 수문장" to "boss")) {
                scenario.onActivity {
                    val e = GameEngine()
                    e.enterFloor(if (id == "boss") 10 else 1)
                    e.startBattle(id == "boss")
                    e.state.enemy = e.state.enemy!!.copy(name = name)
                    it.game.engine = e
                    it.game.refresh()
                }
                bounds("3인칭 전투")
                capture("enemy-$id")
            }
            for ((event, id) in listOf(Event.HEAL to "heal", Event.EXIT to "door")) {
                scenario.onActivity {
                    val e = GameEngine()
                    e.enterFloor(1)
                    e.state.dungeon!!.here().event = event
                    it.game.engine = e
                    it.game.refresh()
                }
                bounds("메인 장면")
                capture("fine-$id")
            }
            scenario.onActivity {
                val e = GameEngine()
                e.enterFloor(50)
                e.beginInheritance()
                it.game.engine = e
                it.game.refresh()
            }
            bounds("다음 여정에 남길 장비")
            capture("fine-inheritance")
            scenario.onActivity {
                val e = GameEngine()
                Kind.entries.forEach { kind ->
                    e.state.inventory.add(ItemFactory.create(e.state, kind))
                }
                e.choose(e.choices().first { c -> c.action == "inventory" })
                it.game.engine = e
                it.game.refresh()
            }
            bounds("가방")
            capture("fine-inventory-top")
            repeat(3) {
                scroll(instrumentation.uiAutomation.rootInActiveWindow)
                instrumentation.waitForIdleSync()
                SystemClock.sleep(150)
            }
            capture("fine-inventory-bottom")
        }
        // Render every authored asset with Android's actual bitmap implementation.
        val atlas =
            android.graphics.Bitmap.createBitmap(672, 464, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(atlas)
        canvas.drawColor(0xff101a24.toInt())
        val paint =
            android.graphics.Paint().apply {
                isAntiAlias = false
                isFilterBitmap = false
                color = 0xffe2e9e1.toInt()
                textSize = 11f
            }
        FineSprites.all.entries.forEachIndexed { index, entry ->
            val x = (index % 7) * 96
            val y = (index / 7) * 116
            val art = entry.value
            canvas.drawText(entry.key, x + 3f, y + 12f, paint)
            canvas.drawBitmap(
                art.bitmap.asAndroidBitmap(),
                null,
                android.graphics.Rect(
                    x + 16,
                    y + 17,
                    x + 16 + art.width * 2,
                    y + 17 + art.height * 2,
                ),
                paint,
            )
        }
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "fine-atlas.png")
        file.outputStream().use {
            atlas.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        atlas.recycle()
    }
}
