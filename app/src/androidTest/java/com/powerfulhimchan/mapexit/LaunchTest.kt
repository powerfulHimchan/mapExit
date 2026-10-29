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
        if (label in listOf("A 일반 공격", "B 강공격", "A 방어", "B 회피")) SystemClock.sleep(800)
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250)
        // Accessibility may be ready before the cold-launch window has been replaced by a frame.
        var blank = true
        for (attempt in 0..19) {
            val frame = instrumentation.uiAutomation.takeScreenshot()!!
            blank =
                listOf(1 to 3, 2 to 2, 3 to 3).all { (x, y) ->
                    val pixel = frame.getPixel(frame.width * x / 4, frame.height / y)
                    android.graphics.Color.red(pixel) >= 245 &&
                        android.graphics.Color.green(pixel) >= 245 &&
                        android.graphics.Color.blue(pixel) >= 245
                }
            frame.recycle()
            if (!blank) break
            SystemClock.sleep(100)
        }
        assertFalse(
            "Capture must contain the rendered game rather than the launch window: $name",
            blank,
        )
        val descriptor =
            instrumentation.uiAutomation.executeShellCommand(
                "screencap -p /sdcard/Download/mapexit-$name.png"
            )
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun assertVictoryClearsDamage(scenario: ActivityScenario<MainActivity>) {
        var popupLabel = ""
        scenario.onActivity { popupLabel = "적 피해 ${it.game.engine.state.lastImpact!!.label()}" }
        bounds("B로 결과 확인 후 계속")
        assertNull(
            "Finished combat must not retain the final damage popup",
            find(instrumentation.uiAutomation.rootInActiveWindow, popupLabel),
        )
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
            tap("장비", allowScroll = true)
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
                assertEquals(Mode.MENU, it.game.engine.state.mode)
                assertEquals("equipment", it.game.engine.state.menu?.type)
            }
            capture("equipment-stays")
            var helmetId = 0L
            scenario.onActivity {
                val e = it.game.engine
                val helmet = ItemFactory.create(e.state, Kind.HEAD)
                helmetId = helmet.id
                e.state.inventory.add(helmet)
                it.game.refresh()
            }
            tap("머리: 비어 있음", allowScroll = true)
            var helmetChoice = ""
            scenario.onActivity {
                helmetChoice =
                    it.game.engine
                        .choices()
                        .first { c -> c.action == "item" && c.argument == helmetId }
                        .label
            }
            tap(helmetChoice, allowScroll = true)
            tap("머리에 장착", allowScroll = true)
            bounds("장비 장착")
            scenario.onActivity {
                assertEquals(helmetId, it.game.engine.state.equipment[Slot.HEAD])
                assertEquals(swordId, it.game.engine.state.equipment[Slot.LEFT])
                assertEquals("equipment", it.game.engine.state.menu?.type)
            }
            capture("equipment-continuous")
            tap("돌아가기")
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
            tap("회복약 사용")
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
            assertVictoryClearsDamage(scenario)
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
                assertVictoryClearsDamage(scenario)
                capture("compact-victory")
                tap("B 결과 확인 · 계속")
                scenario.onActivity {
                    val s = it.game.engine.state
                    s.trap = Traps.create(s, 4)
                    s.mode = Mode.TRAP
                    it.game.refresh()
                }
                bounds("봉을 피해라")
                capture("compact-cue")
                scenario.onActivity {
                    it.game.engine.die()
                    it.game.refresh()
                }
                bounds("사망했습니다")
                val continueButton = bounds("준비 화면으로")
                val resultScreenshot = instrumentation.uiAutomation.takeScreenshot()!!
                assertTrue(continueButton.bottom <= resultScreenshot.height)
                resultScreenshot.recycle()
                capture("compact-death")
                tap("준비 화면으로")
                tap("장비", allowScroll = true)
                bounds("머리: 비어 있음", allowScroll = true)
                capture("compact-equipment")
                bounds("신발: 비어 있음", allowScroll = true)
                scenario.onActivity {
                    val e = it.game.engine
                    e.navigateBack()
                    e.state.points = 1
                    e.choose(e.choices().first { c -> c.action == "growth" })
                    it.game.refresh()
                }
                bounds("남은 성장 포인트 1P · 카드당 1P")
                capture("compact-growth")
                scenario.onActivity {
                    val e = it.game.engine
                    e.navigateBack()
                    e.state.gold = 1000
                    e.choose(e.choices().first { c -> c.action == "shop" })
                    it.game.refresh()
                }
                bounds("아이템 판매")
                capture("compact-shop")
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
    fun preparationMenusHideRepeatedLogAndGrowthUsesFourColumns() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val menus =
                listOf(
                    "equipment" to "장비 장착",
                    "inventory" to "가방",
                    "shop" to "상점",
                    "growth" to "능력치 성장",
                    "bank" to "골드 보관소",
                    "help" to "탐험 기록",
                )
            for ((action, title) in menus) {
                var labels = emptyList<String>()
                scenario.onActivity {
                    val e = GameEngine(State(points = 3, gold = 500))
                    e.state.message = "반복 행동 배너 검사"
                    e.choose(e.choices().first { c -> c.action == action })
                    labels =
                        e.choices().filter { c -> c.action == "stat" }.take(4).map { c -> c.label }
                    it.game.active = false
                    it.game.engine = e
                    it.game.refresh()
                }
                bounds(title)
                assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "반복 행동 배너 검사"))
                if (action == "growth") {
                    val cards = labels.map { bounds(it) }
                    assertEquals(4, cards.size)
                    assertTrue(cards.all { it.top == cards.first().top })
                    assertTrue(cards.zipWithNext().all { (a, b) -> a.right <= b.left })
                }
                capture("menu-$action")
            }
            scenario.onActivity {
                val e = GameEngine()
                e.enterFloor(1)
                it.game.engine = e
                it.game.refresh()
            }
            val scene = bounds("메인 장면")
            val caption = bounds("현재 상황")
            val map = bounds("주변 미니맵")
            assertTrue(caption.top >= scene.bottom && caption.bottom <= map.top)
            capture("scene-caption")
        }
    }

    @Test
    fun liveCombatSynchronizesCriticalFeedbackAndPreventsOverlappingTurns() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var popup = ""
            scenario.onActivity {
                val e = GameEngine(State(base = Stats(crit = 1.0)))
                e.enterFloor(1)
                e.startBattle(false)
                e.state.enemy = e.state.enemy!!.copy(hp = 5000.0, maxHp = 5000.0)
                e.state.rng = 0L
                it.game.engine = e
                it.game.active = false
                it.game.refresh()
                it.game.choose(e.choices().first { c -> c.action == "attack" })
                assertTrue(it.game.battleBusy())
                val serial = e.state.impactSequence
                it.game.choose(e.choices().first { c -> c.action == "guard" })
                assertEquals(serial, e.state.impactSequence)
                assertEquals(Mode.COMBAT_DEFEND, e.state.mode)
                assertTrue(e.state.lastImpact!!.critical)
                popup = "적 피해 ${e.state.lastImpact!!.label()}"
            }
            bounds(popup)
            capture("critical-enemy")
            SystemClock.sleep(800)
            scenario.onActivity {
                val s = it.game.engine.state
                s.rng = (0L..1000L).first { seed -> s.copy(rng = seed).random() < .1 }
            }
            tap("A 방어")
            scenario.onActivity {
                val hit = it.game.engine.state.lastImpact!!
                assertTrue(hit.critical)
                assertEquals(ImpactTarget.PLAYER, hit.target)
                popup = "플레이어 피해 ${hit.label()}"
            }
            val damage = bounds(popup)
            val image = instrumentation.uiAutomation.takeScreenshot()!!
            var redPixels = 0
            for (y in damage.top until damage.bottom) for (x in damage.left until damage.right) {
                val c = image.getPixel(x, y)
                if (
                    android.graphics.Color.red(c) > 180 &&
                        android.graphics.Color.red(c) > android.graphics.Color.green(c) * 1.3 &&
                        android.graphics.Color.red(c) > android.graphics.Color.blue(c) * 1.3
                )
                    redPixels++
            }
            image.recycle()
            assertTrue("Player damage must be visibly red", redPixels > 10)
            capture("critical-player")
            scenario.onActivity {
                val e = it.game.engine
                e.state.mode = Mode.COMBAT_DEFEND
                e.state.hp = 1.0
                it.game.choose(e.choices().first { c -> c.action == "guard" })
                assertEquals(Mode.RUN_RESULT, e.state.mode)
                assertTrue(it.game.battleBusy())
                assertEquals(
                    Mode.COMBAT_DEFEND,
                    it.game.presentationState(SystemClock.uptimeMillis()).mode,
                )
            }
            bounds("사망했습니다")
            scenario.onActivity { assertFalse(it.game.battleBusy()) }
            capture("animated-death")
        }
    }

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    fun renderEveryWeaponAndMonsterAtWindupStrikeAndRecovery() {
        val bitmap = androidx.compose.ui.graphics.ImageBitmap(480, 640)
        val scope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
        fun render(s: State, visual: BattleVisual, elapsed: Long): android.graphics.Bitmap {
            scope.draw(
                androidx.compose.ui.unit.Density(1f),
                androidx.compose.ui.unit.LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(bitmap),
                androidx.compose.ui.geometry.Size(480f, 640f),
            ) {
                drawScene(s, 1000L + elapsed, battle = visual)
            }
            return bitmap.asAndroidBitmap()
        }
        val phases = listOf(120L, 320L, 660L)
        for (kind in listOf(Kind.SWORD, Kind.SPEAR, Kind.HAMMER, Kind.CLAW, Kind.WHIP)) {
            val e = GameEngine()
            e.enterFloor(1)
            e.state.equipment.clear()
            val gear = ItemFactory.create(e.state, kind)
            e.state.inventory.add(gear)
            Equipment.equip(e.state, gear.id, Slot.RIGHT)
            e.startBattle(false)
            val before = e.state.battleSnapshot()
            val saved = SaveCodec.encode(e.state)
            val visual =
                BattleVisual(
                    before,
                    if (kind == Kind.HAMMER) "strong" else "attack",
                    1000,
                    BattleImpact(
                        1,
                        ImpactTarget.ENEMY,
                        100,
                        ImpactOutcome.DAMAGE,
                        critical = kind == Kind.SWORD,
                    ),
                )
            var previous: IntArray? = null
            phases.forEachIndexed { index, elapsed ->
                val frame = render(e.state, visual, elapsed)
                val pixels = IntArray(480 * 640)
                frame.getPixels(pixels, 0, 480, 0, 0, 480, 640)
                previous?.let { assertFalse(it.contentEquals(pixels)) }
                previous = pixels
                exportBitmap("motion-${kind.name.lowercase()}-$index", frame)
            }
            assertEquals(saved, SaveCodec.encode(e.state))
        }
        for ((name, id) in
            listOf("동굴 슬라임" to "slime", "심연 박쥐" to "bat", "녹슨 기사" to "knight", "수문장" to "boss")) {
            val e = GameEngine()
            e.enterFloor(1)
            e.startBattle(id == "boss")
            e.state.enemy = e.state.enemy!!.copy(name = name)
            val visual =
                BattleVisual(
                    e.state.battleSnapshot(),
                    "guard",
                    1000,
                    BattleImpact(1, ImpactTarget.PLAYER, 30, ImpactOutcome.GUARD),
                )
            phases.forEachIndexed { index, elapsed ->
                exportBitmap("motion-$id-$index", render(e.state, visual, elapsed))
            }
        }
    }

    @Test
    fun iconGrowthAndTradingCardsRefreshImmediately() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var hpChoice = ""
            var attackChoice = ""
            scenario.onActivity {
                val e = GameEngine(State(gold = 10000, points = 1))
                e.choose(e.choices().first { c -> c.action == "growth" })
                hpChoice =
                    e.choices()
                        .first { c -> c.action == "stat" && c.argument == Stat.HP.ordinal.toLong() }
                        .label
                attackChoice =
                    e.choices()
                        .first { c ->
                            c.action == "stat" && c.argument == Stat.ATTACK.ordinal.toLong()
                        }
                        .label
                it.game.engine = e
                it.game.active = false
                it.game.refresh()
            }
            val hp = bounds(hpChoice)
            val attack = bounds(attackChoice)
            assertEquals(hp.top, attack.top)
            assertTrue(hp.centerX() < attack.centerX())
            bounds("최대 체력 성장 후 385")
            capture("growth-grid")
            tap(hpChoice)
            bounds("남은 성장 포인트 0P · 카드당 1P")
            var noPoints = ""
            scenario.onActivity {
                assertEquals(385.0, it.game.engine.stats().hp, .001)
                noPoints = it.game.engine.choices().first { c -> c.action == "stat" }.label
            }
            tap(noPoints)
            scenario.onActivity { assertEquals(0, it.game.engine.state.points) }
            capture("growth-empty")
            var swordLabel = ""
            var shieldLabel = ""
            var swordId = 0L
            scenario.onActivity {
                val e = it.game.engine
                e.navigateBack()
                val sword = ItemFactory.create(e.state, Kind.SWORD)
                val shield = ItemFactory.create(e.state, Kind.SHIELD)
                val potion = ItemFactory.create(e.state, Kind.POTION)
                swordId = sword.id
                e.state.shop = mutableListOf(sword, shield, potion)
                e.choose(e.choices().first { c -> c.action == "shop" })
                swordLabel =
                    e.choices().first { c -> c.action == "buy" && c.argument == sword.id }.label
                shieldLabel =
                    e.choices().first { c -> c.action == "buy" && c.argument == shield.id }.label
                it.game.refresh()
            }
            val sword = bounds(swordLabel)
            val shield = bounds(shieldLabel)
            assertEquals(sword.top, shield.top)
            assertTrue(sword.centerX() < shield.centerX())
            capture("shop-grid")
            tap(swordLabel)
            scenario.onActivity {
                assertFalse(it.game.engine.state.shop.any { gear -> gear.id == swordId })
                assertTrue(it.game.engine.state.inventory.any { gear -> gear.id == swordId })
            }
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, swordLabel))
            capture("shop-purchased")
            var potionLabel = ""
            scenario.onActivity {
                val e = it.game.engine
                val potion = e.state.shop.first { gear -> gear.kind == Kind.POTION }
                potionLabel =
                    e.choices().first { c -> c.action == "buy" && c.argument == potion.id }.label
            }
            tap(potionLabel, allowScroll = true)
            var quantities = listOf<String>()
            scenario.onActivity {
                quantities =
                    it.game.engine
                        .choices()
                        .filter { c -> c.action == "buyQuantity" }
                        .map { c -> c.label }
            }
            val one = bounds(quantities[0])
            val three = bounds(quantities[1])
            val five = bounds(quantities[2])
            assertEquals(one.top, three.top)
            assertEquals(one.top, five.top)
            capture("quantity-grid")
            tap(quantities[1])
            scenario.onActivity {
                assertEquals(
                    5,
                    it.game.engine.state.inventory.count { gear -> gear.kind == Kind.POTION },
                )
            }
            scenario.onActivity { it.game.back() }
            tap("아이템 판매")
            capture("sell-grid")
        }
    }

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    fun continuousDungeonProjectionProducesDistinctOpaqueFrames() {
        val e = GameEngine()
        val map = Dungeon(9, 11, MutableList(99) { Cell() }, x = 4, y = 8, direction = 0)
        for (y in 1..9) map.cell(4, y).wall = false
        for (x in 2..4) map.cell(x, 3).wall = false
        e.state.dungeon = map
        e.state.floor = 1
        e.state.mode = Mode.EXPLORE
        val camera = DungeonCamera()
        camera.observe(e.state, 0)
        map.y--
        camera.observe(e.state, 0)
        val saved = SaveCodec.encode(e.state)
        val renderer = DungeonRaster(480, 640)
        var previous: IntArray? = null
        for ((index, time) in listOf(0L, 150L, 300L, 450L, 600L).withIndex()) {
            val bitmap = renderer.render(map, camera.sample(time)).asAndroidBitmap()
            val pixels = IntArray(480 * 640)
            bitmap.getPixels(pixels, 0, 480, 0, 0, 480, 640)
            assertTrue(pixels.all { (it ushr 24) == 255 })
            assertTrue(pixels.toSet().size > 40)
            previous?.let { assertFalse(it.contentEquals(pixels)) }
            previous = pixels
            exportBitmap("walk-$index", bitmap)
        }
        assertEquals(saved, SaveCodec.encode(e.state))
        for ((index, direction) in listOf(0, 1, 2, 3).withIndex()) {
            val pose =
                DungeonViewPose(4.5f, 3.5f, (-Math.PI / 2 + direction * Math.PI / 2).toFloat())
            exportBitmap("turn-$index", renderer.render(map, pose).asAndroidBitmap())
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.game.active = false
                it.game.engine = e
                it.game.refresh()
            }
            bounds("메인 장면")
            capture("smooth-corridor")
        }
    }

    @Test
    fun shopComparisonRecordsAndNavigationUseActualEquipment() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var oldName = ""
            scenario.onActivity {
                val e = GameEngine(State(gold = 10000))
                val old = ItemFactory.create(e.state, Kind.HEAD)
                oldName = old.name
                e.state.inventory.add(old)
                Equipment.equip(e.state, old.id, Slot.HEAD)
                e.state.shop = mutableListOf(ItemFactory.create(e.state, Kind.HEAD))
                e.choose(Choice("상점", "shop"))
                it.game.active = false
                it.game.engine = e
                it.game.refresh()
            }
            bounds("현재: $oldName")
            capture("shop-comparison")
            scenario.onActivity {
                val e = it.game.engine
                e.navigateBack()
                e.state.log.add("표시하면 안 되는 행동 로그")
                e.choose(Choice("기록", "help"))
                it.game.refresh()
            }
            bounds("누적 탐험")
            bounds("진행 중인 의뢰")
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "표시하면 안 되는 행동 로그"))
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "가방 / 장비"))
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "장착 현황 / 상세 능력치"))
            capture("records-clean")
            tap("돌아가기")
            bounds("던전 진입")
            capture("entry-redesign")
            tap("던전 진입")
            scenario.onActivity {
                val e = it.game.engine
                e.startBattle(false)
                e.state.mode = Mode.COMBAT_DEFEND
                it.game.refresh()
            }
            bounds("A 방어")
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "A  ·  바로 실행"))
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "B  ·  바로 실행"))
            capture("defense-controls")
        }
    }

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    fun fourCombatActionsRenderDifferentFramesWithoutChangingGame() {
        val bitmap = androidx.compose.ui.graphics.ImageBitmap(480, 640)
        val scope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
        val e = GameEngine()
        e.enterFloor(1)
        e.startBattle(false)
        val saved = SaveCodec.encode(e.state)
        val contactFrames = mutableListOf<IntArray>()
        for (action in listOf("attack", "strong", "guard", "dodge")) {
            val visual =
                BattleVisual(
                    e.state.battleSnapshot(),
                    action,
                    1000,
                    BattleImpact(
                        1,
                        if (action in listOf("attack", "strong")) ImpactTarget.ENEMY
                        else ImpactTarget.PLAYER,
                        10,
                        if (action == "dodge") ImpactOutcome.DODGE else ImpactOutcome.DAMAGE,
                    ),
                )
            for ((index, elapsed) in listOf(180L, 320L, 650L).withIndex()) {
                scope.draw(
                    androidx.compose.ui.unit.Density(1f),
                    androidx.compose.ui.unit.LayoutDirection.Ltr,
                    androidx.compose.ui.graphics.Canvas(bitmap),
                    androidx.compose.ui.geometry.Size(480f, 640f),
                ) {
                    drawScene(e.state, 1000 + elapsed, battle = visual)
                }
                val frame = bitmap.asAndroidBitmap()
                if (index == 1) {
                    val pixels = IntArray(480 * 640)
                    frame.getPixels(pixels, 0, 480, 0, 0, 480, 640)
                    assertTrue(contactFrames.none { it.contentEquals(pixels) })
                    contactFrames.add(pixels)
                }
                exportBitmap("action-$action-$index", frame)
            }
        }
        assertEquals(saved, SaveCodec.encode(e.state))
    }

    @Test
    fun dungeonHeaderMenusAndTurnStripUseSeparateAreas() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                val e = GameEngine(State(points = 3))
                e.enterFloor(1)
                it.game.active = false
                it.game.engine = e
                it.game.refresh()
            }
            val scene = bounds("메인 장면")
            assertTrue(bounds("장비").bottom <= scene.top)
            assertTrue(bounds("능력치").bottom <= scene.top)
            capture("dungeon-toolbar")
            tap("장비")
            bounds("장비 장착")
            capture("dungeon-equipment")
            tap("돌아가기")
            tap("능력치")
            bounds("능력치 성장")
            capture("dungeon-stats")
            tap("돌아가기")
            scenario.onActivity {
                it.game.engine.startBattle(false)
                it.game.engine.state.enemy!!.hp = 5000.0
                it.game.refresh()
            }
            var turn = bounds("전투 차례")
            var caption = bounds("현재 상황")
            var action = bounds("A 일반 공격")
            assertTrue(turn.top > caption.bottom && turn.bottom <= action.top)
            bounds("공격 차례")
            capture("turn-attack")
            tap("A 일반 공격")
            turn = bounds("전투 차례")
            caption = bounds("현재 상황")
            action = bounds("A 방어")
            assertTrue(turn.top > caption.bottom && turn.bottom <= action.top)
            bounds("방어 차례")
            capture("turn-defense")
        }
    }

    @Test
    fun levelUpBannerAppearsAfterFinalStrikeAndCanBeDismissed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                val e = GameEngine(State(experience = 79))
                e.enterFloor(1)
                e.startBattle(false)
                e.state.enemy!!.hp = 1.0
                e.state.rng = 0L
                it.game.active = false
                it.game.engine = e
                it.game.refresh()
                it.game.choose(Choice("공격", "attack"))
                assertTrue(it.game.battleBusy())
                assertEquals(LevelUpNotice(1, 2, 3), e.state.levelUpNotice)
            }
            val banner = bounds("레벨 업 LV.1에서 LV.2")
            assertTrue(banner.bottom <= bounds("메인 장면").top)
            bounds("성장 포인트 +3P · 보유 3P")
            capture("level-up")
            tap("레벨업 알림 닫기")
            scenario.onActivity { assertNull(it.game.engine.state.levelUpNotice) }
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "레벨 업 LV.1에서 LV.2"))
            capture("level-up-dismissed")
        }
    }

    @Test
    fun deadEndMessageDisappearsAfterReturningToCorridor() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                val d = Dungeon(9, 9, MutableList(81) { Cell() }, x = 4, y = 3, direction = 0)
                for (y in 2..6) d.cell(4, y).wall = false
                val e =
                    GameEngine(
                        State(bootstrapped = true, dungeon = d, floor = 1, mode = Mode.EXPLORE)
                    )
                e.choose(Choice("앞으로", "move", 0))
                it.game.active = false
                it.game.engine = e
                it.game.refresh()
            }
            bounds("막다른 길입니다")
            capture("dead-end")
            tap("A ↓ 뒤로 이동")
            bounds("통로를 탐험하고 있습니다")
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "막다른 길입니다"))
            capture("dead-end-cleared")
        }
    }

    @Test
    fun unaffordableShopItemsStayTouchableAndOnlyWarnAfterTap() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var buy = ""
            var itemId = 0L
            scenario.onActivity {
                val e = GameEngine(State(gold = 0))
                val item = ItemFactory.create(e.state, Kind.HEAD)
                itemId = item.id
                e.state.shop = mutableListOf(item)
                e.choose(Choice("상점", "shop"))
                buy = e.choices().first { it.action == "buy" }.label
                it.game.active = false
                it.game.engine = e
                it.game.refresh()
            }
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "골드부족"))
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow, "골드 부족"))
            capture("shop-red-name")
            tap(buy)
            bounds("골드부족")
            scenario.onActivity {
                assertEquals(0, it.game.engine.state.gold)
                assertTrue(it.game.engine.state.shop.any { gear -> gear.id == itemId })
                assertFalse(it.game.engine.state.inventory.any { gear -> gear.id == itemId })
            }
            capture("shop-gold-toast")
        }
    }

    @Test
    fun topMenusExposeInventoryRecordsDirectionResetPotionAndFlee() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                val e = GameEngine()
                e.enterFloor(1)
                it.game.active = false
                it.game.engine = e
                it.game.refresh()
            }
            val scene = bounds("메인 장면")
            for (label in listOf("가방", "기록", "방향 처음")) assertTrue(bounds(label).bottom <= scene.top)
            capture("top-explore-menus")
            tap("가방")
            bounds("가방")
            tap("돌아가기")
            tap("기록")
            bounds("탐험 기록")
            tap("돌아가기")
            scenario.onActivity {
                val e = it.game.engine
                e.startBattle(false)
                e.state.enemy!!.hp = 5000.0
                e.state.hp = 100.0
                it.game.refresh()
            }
            assertTrue(bounds("회복약 사용").bottom <= bounds("메인 장면").top)
            assertTrue(bounds("도주 시도").bottom <= bounds("메인 장면").top)
            capture("top-combat-menus")
            tap("회복약 사용")
            scenario.onActivity {
                assertEquals(
                    1,
                    it.game.engine.state.inventory.count { gear -> gear.kind == Kind.POTION },
                )
                assertEquals(Mode.COMBAT_DEFEND, it.game.engine.state.mode)
                assertTrue(it.game.engine.state.hp > 100.0)
            }
        }
    }

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    fun allPhysicalObstaclesRenderDistinctPhaseFramesWithoutMutatingGame() {
        val image = androidx.compose.ui.graphics.ImageBitmap(480, 640)
        val scope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
        val e = GameEngine()
        e.enterFloor(1)
        e.state.mode = Mode.TRAP
        val kinds = mutableListOf<IntArray>()
        for (type in 0..4) {
            e.state.trap = Traps.create(e.state, type)
            var previous: IntArray? = null
            for ((index, time) in listOf(100L, 700L, 1400L).withIndex()) {
                val t = e.state.trap!!
                t.phase = if (type == 3 && index == 0) TrapPhase.REVEAL else TrapPhase.ACTIVE
                t.elapsedMs = time
                if (type == 4) t.holdStarted = 0
                val saved = SaveCodec.encode(e.state)
                scope.draw(
                    androidx.compose.ui.unit.Density(1f),
                    androidx.compose.ui.unit.LayoutDirection.Ltr,
                    androidx.compose.ui.graphics.Canvas(image),
                    androidx.compose.ui.geometry.Size(480f, 640f),
                ) {
                    drawScene(e.state, time)
                }
                val bitmap = image.asAndroidBitmap()
                val pixels = IntArray(480 * 640)
                bitmap.getPixels(pixels, 0, 480, 0, 0, 480, 640)
                previous?.let { assertFalse(it.contentEquals(pixels)) }
                previous = pixels
                if (index == 1) {
                    assertTrue(kinds.none { it.contentEquals(pixels) })
                    kinds.add(pixels)
                }
                assertEquals(saved, SaveCodec.encode(e.state))
                exportBitmap("obstacle-$type-$index", bitmap)
            }
        }
    }

    @Test
    fun longPressNeverOpensAnAlternateMenuAndGoldNoticeIsCentered() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                val e = GameEngine()
                e.enterFloor(1)
                e.startBattle(false)
                e.state.enemy!!.hp = 5000.0
                it.game.active = false
                it.game.engine = e
                it.game.refresh()
            }
            tap("A 일반 공격", held = true)
            scenario.onActivity {
                assertEquals(
                    2,
                    it.game.engine.state.inventory.count { gear -> gear.kind == Kind.POTION },
                )
                assertEquals(Mode.COMBAT_DEFEND, it.game.engine.state.mode)
            }
            capture("tap-only-combat")
            var label = ""
            scenario.onActivity {
                val e = GameEngine(State(gold = 0))
                e.choose(Choice("상점", "shop"))
                label = e.choices().first { c -> c.action == "buy" }.label
                it.game.engine = e
                it.game.refresh()
            }
            tap(label)
            val message = bounds("골드부족")
            val image = instrumentation.uiAutomation.takeScreenshot()!!
            assertTrue(kotlin.math.abs(message.centerY() - image.height / 2) < image.height * .12)
            image.recycle()
            capture("gold-center")
        }
    }

    @Test
    fun chestLootAppearsInSceneAndFullGuardShowsZero() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                val e = GameEngine()
                e.enterFloor(1)
                e.state.dungeon!!.here().event = Event.CHEST
                e.state.rng = 1L
                e.choose(Choice("상자", "encounter", Event.CHEST.ordinal.toLong()))
                e.choose(Choice("열기", "chest"))
                it.game.active = false
                it.game.engine = e
                it.game.refresh()
            }
            val loot = bounds("상자 획득")
            val scene = bounds("메인 장면")
            assertTrue(loot.top > scene.top && loot.bottom < scene.bottom)
            capture("chest-loot")
            tap("획득 확인")
            scenario.onActivity {
                assertNull(it.game.engine.state.chestLoot)
                val e = GameEngine(State(base = Stats(defense = 400.0, luck = 200.0)))
                e.enterFloor(1)
                e.startBattle(false)
                e.state.mode = Mode.COMBAT_DEFEND
                e.state.rng = 0L
                it.game.engine = e
                it.game.refresh()
            }
            tap("A 방어")
            bounds("플레이어 피해 완전 방어 · 0 피해")
            scenario.onActivity {
                assertEquals(ImpactOutcome.BLOCK, it.game.engine.state.lastImpact!!.outcome)
            }
            capture("full-guard-zero")
        }
    }

    @Test
    fun publicTrainingMenuCanChooseAllFourActivitiesAndExitSafely() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.game.active = false
                it.game.engine = GameEngine(State(gold = 57))
                it.game.refresh()
            }
            tap("테스트 메뉴", allowScroll = true)
            for (label in listOf("장애물 피하기", "전투", "상인", "미션 NPC 대화")) bounds(label)
            capture("training-menu")
            tap("전투")
            bounds("A 일반 공격")
            capture("training-battle")
            tap("테스트 목록")
            tap("상인")
            bounds("A 거래하기")
            capture("training-merchant")
            tap("테스트 목록")
            tap("미션 NPC 대화")
            capture("training-npc")
            scenario.onActivity { assertNotNull(it.game.engine.state.dungeon!!.here().questOffer) }
            tap("테스트 목록")
            tap("장애물 피하기")
            tap("천장 나무봉")
            bounds("봉을 피해라")
            capture("training-trap")
            tap("테스트 종료")
            bounds("출정 준비")
            scenario.onActivity {
                assertEquals(57, it.game.engine.state.gold)
                assertFalse(it.game.engine.state.training)
            }
        }
    }

    @Test
    fun bladeAndBeamUseFixedActionsAndShowTheActualAvoidancePose() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun show(type: Int, time: Long) {
                scenario.onActivity {
                    val e = GameEngine()
                    e.enterFloor(1)
                    e.state.mode = Mode.TRAP
                    e.state.trap =
                        Traps.create(e.state, type)
                            .copy(
                                phase = TrapPhase.ACTIVE,
                                elapsedMs = time,
                                pattern = listOf(if (type == 1) 1 else 0),
                            )
                    it.game.active = false
                    it.game.engine = e
                    it.game.refresh()
                }
                bounds("A 점프")
                bounds("B 몸 숙이기")
            }
            show(1, 100)
            capture("blades-raised")
            scenario.onActivity {
                repeat(4) { _ -> it.game.engine.tick(225) }
                assertTrue(it.game.engine.state.trap!!.bladesRetracted())
                it.game.refresh()
            }
            capture("blades-retracted")
            tap("A 점프")
            scenario.onActivity {
                assertTrue(it.game.engine.state.trap!!.success)
                assertEquals(360.0, it.game.engine.state.hp, .001)
                repeat(2) { _ -> it.game.engine.tick(225) }
                it.game.refresh()
            }
            bounds("회피 성공")
            capture("blades-jump")
            scenario.onActivity {
                repeat(2) { _ -> it.game.engine.tick(225) }
                it.game.engine.tick(1)
                assertEquals(Mode.EXPLORE, it.game.engine.state.mode)
            }
            show(4, 0)
            scenario.onActivity {
                val before = it.game.engine.state.trap!!.beamDescent()
                it.game.engine.tick(250)
                assertTrue(it.game.engine.state.trap!!.beamDescent() > before)
                it.game.engine.state.trap!!.elapsedMs = 1400
                it.game.refresh()
            }
            capture("beam-descending")
            tap("B 몸 숙이기")
            scenario.onActivity {
                assertTrue(it.game.engine.state.trap!!.success)
                assertEquals(1, it.game.engine.state.trap!!.action)
                assertEquals(360.0, it.game.engine.state.hp, .001)
                repeat(2) { _ -> it.game.engine.tick(250) }
                it.game.engine.tick(150)
                it.game.refresh()
            }
            bounds("회피 성공")
            capture("beam-crouch")
        }
    }

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    fun physicalAvoidancePosesFitTheCompactSceneWithoutChangingState() {
        val image = androidx.compose.ui.graphics.ImageBitmap(480, 400)
        val scope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
        for (type in listOf(1, 4)) {
            val e = GameEngine()
            e.enterFloor(1)
            e.state.mode = Mode.TRAP
            val t = Traps.create(e.state, type).copy(phase = TrapPhase.ACTIVE, elapsedMs = 1000)
            e.state.trap = t
            e.press(if (type == 1) 0 else 1)
            t.elapsedMs = if (type == 1) 450 else 650
            val saved = SaveCodec.encode(e.state)
            scope.draw(
                androidx.compose.ui.unit.Density(1f),
                androidx.compose.ui.unit.LayoutDirection.Ltr,
                androidx.compose.ui.graphics.Canvas(image),
                androidx.compose.ui.geometry.Size(480f, 400f),
            ) {
                drawScene(e.state, 0L)
            }
            assertEquals(saved, SaveCodec.encode(e.state))
            exportBitmap(
                if (type == 1) "blades-compact" else "beam-compact",
                image.asAndroidBitmap(),
            )
        }
    }

    private fun exportBitmap(name: String, bitmap: android.graphics.Bitmap) {
        val resolver = instrumentation.targetContext.contentResolver
        val values =
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "mapexit-$name.png")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(
                    android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                )
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri =
            requireNotNull(
                resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            )
        requireNotNull(resolver.openOutputStream(uri)).use {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        resolver.update(
            uri,
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            },
            null,
            null,
        )
    }

    @Test
    fun expeditionResultsRetreatConfirmationAndEquipmentLayout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.game.active = false
                val e = GameEngine(State(gold = 120, bank = 500))
                e.enterFloor(7)
                e.state.runKills = 9
                e.state.runChests = 2
                e.state.runElapsedMs = 65000
                it.game.engine = e
                it.game.refresh()
            }
            val retreat = bounds("지상으로")
            val scene = bounds("메인 장면")
            assertTrue(retreat.bottom <= scene.top)
            assertTrue(retreat.centerX() > scene.centerX())
            tap("지상으로")
            bounds("탐험을 포기하고 지상으로 갈까요?")
            capture("retreat-confirm")
            tap("계속 탐험")
            scenario.onActivity {
                assertEquals(Mode.EXPLORE, it.game.engine.state.mode)
                assertEquals(120, it.game.engine.state.gold)
                assertTrue(it.game.engine.state.inventory.isNotEmpty())
            }
            tap("지상으로")
            tap("포기하고 귀환")
            bounds("탐험을 포기했습니다")
            bounds("처치한 적 9명")
            bounds("귀환한 위치 지하 7층")
            capture("retreat-result")
            scenario.onActivity {
                assertEquals(Mode.RUN_RESULT, it.game.engine.state.mode)
                assertEquals(0, it.game.engine.state.gold)
                assertTrue(it.game.engine.state.equipment.isEmpty())
                it.game.pause()
                val cold = GameModel(it.application)
                assertEquals(Mode.RUN_RESULT, cold.engine.state.mode)
                assertEquals(9, cold.engine.state.runResult!!.kills)
            }
            tap("준비 화면으로")
            bounds("출정 준비")
            tap("장비", allowScroll = true)
            bounds("장착 현황 · 0/9 부위")
            capture("equipment-empty")
            tap("머리: 비어 있음", allowScroll = true)
            scenario.onActivity { assertEquals("inventory", it.game.engine.state.menu?.type) }
            scenario.onActivity {
                val e = GameEngine()
                val spear = ItemFactory.create(e.state, Kind.SPEAR)
                e.state.inventory.add(spear)
                Equipment.equip(e.state, spear.id, Slot.RIGHT)
                e.choose(e.choices().first { c -> c.action == "equipment" })
                it.game.engine = e
                it.game.refresh()
            }
            bounds("장비 장착")
            capture("equipment-body-layout")
            var left = ""
            var right = ""
            scenario.onActivity {
                val e = it.game.engine
                left =
                    e.choices()
                        .first { c ->
                            c.action == "slotItem" && c.argument == Slot.LEFT.ordinal.toLong()
                        }
                        .label
                right =
                    e.choices()
                        .first { c ->
                            c.action == "slotItem" && c.argument == Slot.RIGHT.ordinal.toLong()
                        }
                        .label
            }
            val l = bounds(left, allowScroll = true)
            val r = bounds(right)
            assertTrue(l.centerX() < r.centerX())
            assertEquals(l.top, r.top)
            scenario.onActivity {
                val e = GameEngine(State(gold = 60))
                e.enterFloor(4)
                e.state.runKills = 3
                e.startBattle(false)
                e.state.mode = Mode.COMBAT_DEFEND
                e.state.hp = 1.0
                e.defend(false)
                it.game.engine = e
                it.game.refresh()
            }
            bounds("사망했습니다")
            bounds("사망한 위치 지하 4층")
            bounds("처치한 적 3명")
            capture("death-result")
            tap("준비 화면으로")
            bounds("출정 준비")
            scenario.onActivity { assertEquals(Mode.SURFACE, it.game.engine.state.mode) }
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
                    for (b in pattern) tap(if (b == 0) "A 왼쪽 동작" else "B 오른쪽 동작")
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
    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
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
        val resolver = instrumentation.targetContext.contentResolver
        val values =
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "mapexit-fine-atlas.png")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(
                    android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                )
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri =
            requireNotNull(
                resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            )
        requireNotNull(resolver.openOutputStream(uri)).use {
            assertTrue(atlas.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        resolver.update(
            uri,
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            },
            null,
            null,
        )
        val png = requireNotNull(resolver.openInputStream(uri)).use { it.readBytes() }
        assertTrue("The complete sprite atlas must be exported as a readable PNG", png.size > 8)
        assertArrayEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), png.copyOfRange(0, 8))
        atlas.recycle()
    }
}
