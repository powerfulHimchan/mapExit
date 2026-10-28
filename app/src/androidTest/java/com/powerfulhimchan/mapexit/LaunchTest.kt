package com.powerfulhimchan.mapexit

import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
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
    fun combatFeedbackVictoryAndAllTrapBriefings() {
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
                var title = ""
                scenario.onActivity {
                    val e = GameEngine(debugTools = true)
                    e.choose(e.choices().first { c -> c.action == "debug" })
                    e.choose(
                        e.choices().first { c ->
                            c.action == "testTrap" && c.argument == type.toLong()
                        }
                    )
                    repeat(24) { e.tick(250) }
                    assertEquals(Mode.TRAP_GUIDE, e.state.mode)
                    assertEquals(0L, e.state.trap!!.elapsedMs)
                    title = "함정 발견 · ${e.state.trap!!.title}"
                    it.game.engine = e
                    it.game.active = false
                    it.game.refresh()
                }
                bounds(title)
                capture("trap-guide-$type")
                tap("B 함정 시작")
                scenario.onActivity {
                    assertEquals(Mode.TRAP, it.game.engine.state.mode)
                    assertEquals(0L, it.game.engine.state.trap!!.elapsedMs)
                    assertEquals(0, it.game.engine.state.trap!!.progress)
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
                capture("compact-victory")
                tap("B 결과 확인 · 계속")
                scenario.onActivity {
                    val s = it.game.engine.state
                    s.trap = Traps.create(s, 4)
                    s.mode = Mode.TRAP_GUIDE
                    it.game.refresh()
                }
                bounds("함정 발견 · 누르고 떼기")
                bounds("B 함정 시작")
                capture("compact-guide")
            }
        } finally {
            val reset = instrumentation.uiAutomation.executeShellCommand("wm size reset")
            ParcelFileDescriptor.AutoCloseInputStream(reset).use { it.readBytes() }
        }
    }
}
