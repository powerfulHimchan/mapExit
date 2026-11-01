package com.powerfulhimchan.mapexit

import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.powerfulhimchan.mapexit.core.GameEngine
import com.powerfulhimchan.mapexit.core.SaveCodec
import com.powerfulhimchan.mapexit.core.State
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Existing gameplay tests enter through the same action as the title screen. */
internal fun launchGame(): ActivityScenario<MainActivity> =
    ActivityScenario.launch(MainActivity::class.java).also { scenario ->
        scenario.onActivity { it.startGame() }
    }

class IntroScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun findStart(node: AccessibilityNodeInfo?): Rect? {
        if (node == null) return null
        if (node.contentDescription?.toString() == "화면을 터치하여 시작")
            return Rect().also { node.getBoundsInScreen(it) }
        for (i in 0 until node.childCount) findStart(node.getChild(i))?.let { return it }
        return null
    }

    private fun startBounds(): Rect {
        val deadline = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < deadline) {
            findStart(instrumentation.uiAutomation.rootInActiveWindow)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Intro start action not visible")
    }

    private fun capture(name: String) {
        SystemClock.sleep(400)
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/mapexit-$name.png"
        )).use { it.readBytes() }
    }

    @Test fun coldLaunchWaitsForTapAndRetainsExistingSave() {
        val context = instrumentation.targetContext
        val save = File(context.filesDir, "mapexit-save-v1.json")
        File(save.path + ".bak").delete()
        File(save.path + ".new").delete()
        val engine = GameEngine(State(gold = 321))
        engine.enterFloor(3)
        val original = SaveCodec.encode(engine.state)
        save.writeText(original)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val bounds = startBounds()
            scenario.onActivity {
                assertTrue(it.introVisible)
                assertFalse("Dungeon must not tick behind intro", it.game.active)
                assertTrue("Selected intro music plays on the title screen", it.introMusicPlaying)
                assertEquals(3, it.game.engine.state.floor)
            }
            capture("intro-door-normal")
            val now = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0).also {
                    instrumentation.sendPointerSync(it); it.recycle()
                }
                SystemClock.sleep(70)
            }
            SystemClock.sleep(200)
            scenario.onActivity {
                assertFalse(it.introVisible)
                assertFalse("Starting the game releases intro audio", it.introMusicPlaying)
                assertTrue(it.game.active)
                assertEquals(3, it.game.engine.state.floor)
                assertEquals(321, it.game.engine.state.gold)
            }
            scenario.recreate()
            scenario.onActivity {
                assertFalse("Recreation must not replay intro", it.introVisible)
                assertEquals(3, it.game.engine.state.floor)
            }
        }
    }

    @Test fun backgroundResumeDoesNotDismissOrActivateIntro() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            startBounds()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { assertFalse("No music in background", it.introMusicPlaying) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            startBounds()
            scenario.onActivity {
                assertTrue(it.introVisible)
                assertFalse(it.game.active)
                assertTrue("Music resumes only while the intro is visible", it.introMusicPlaying)
            }
        }
    }

    @Test fun compactDisplayKeepsFullIntroAndStartAction() {
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
        shell("wm size 720x1280")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val start = startBounds()
                assertTrue(start.width() > 0 && start.height() > 0)
                capture("intro-door-compact")
                scenario.onActivity { assertTrue(it.introVisible); assertFalse(it.game.active) }
            }
        } finally { shell("wm size reset") }
    }
}
