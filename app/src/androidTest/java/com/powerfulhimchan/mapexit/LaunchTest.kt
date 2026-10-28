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
    @Test
    fun launchesAndTransitionsThroughTwoButtonInput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.game.active = false
                it.game.engine = GameEngine(State())
            }
            instrumentation.waitForIdleSync()
            fun find(node: AccessibilityNodeInfo?, label: String): Rect? {
                if (node == null) return null
                if (node.contentDescription?.toString() == label) {
                    return Rect().also { node.getBoundsInScreen(it) }
                }
                for (i in 0 until node.childCount) find(node.getChild(i), label)?.let { return it }
                return null
            }
            var readyButton: Rect? = null
            val buttonDeadline = SystemClock.uptimeMillis() + 10_000
            while (readyButton == null && SystemClock.uptimeMillis() < buttonDeadline) {
                instrumentation.waitForIdleSync()
                readyButton = find(instrumentation.uiAutomation.rootInActiveWindow, "B 실행")
                if (readyButton == null) SystemClock.sleep(50)
            }
            val button = requireNotNull(readyButton) { "첫 화면에 B 버튼이 표시되지 않았습니다" }
            val now = SystemClock.uptimeMillis()
            MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, button.exactCenterX(), button.exactCenterY(), 0).also {
                instrumentation.sendPointerSync(it)
                it.recycle()
            }
            MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, button.exactCenterX(), button.exactCenterY(), 0).also {
                instrumentation.sendPointerSync(it)
                it.recycle()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(Mode.EXPLORE, it.game.engine.state.mode)
                assertEquals(1, it.game.engine.state.floor)
                it.game.pause()
                val restored = SaveCodec.decode(File(it.filesDir, "mapexit-save-v1.json").readText())
                assertEquals(1, restored.floor)
                assertFalse(restored.debug)
            }
            fun capture(name: String) {
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                assertTrue(bitmap.width > 0 && bitmap.height > 0)
                bitmap.recycle()
                val descriptor = instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/mapexit-$name")
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
            capture("explore.png")
            scenario.onActivity {
                it.game.engine.startBattle(false)
                it.game.down(0)
                it.game.up(0)
            }
            instrumentation.waitForIdleSync()
            capture("battle.png")
            scenario.onActivity {
                val s = it.game.engine.state
                s.enemy = null
                s.trap = Traps.create(s, 1)
                Traps.tick(s.trap!!, 1200)
                s.mode = Mode.TRAP
                it.game.active = true
                it.game.tick(1)
                it.game.active = false
            }
            instrumentation.waitForIdleSync()
            capture("trap.png")
        }
    }
}
