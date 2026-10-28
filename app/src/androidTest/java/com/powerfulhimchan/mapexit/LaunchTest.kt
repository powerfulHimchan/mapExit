package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
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
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val game = activity.game
                game.active = false
                game.engine = GameEngine(State())
                game.down(1)
                game.up(1)
                assertEquals(Mode.EXPLORE, game.engine.state.mode)
                assertEquals(1, game.engine.state.floor)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            fun capture(name: String) {
                requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()).let {
                    bitmap ->
                    assertTrue(bitmap.width > 0 && bitmap.height > 0)
                    File(context.getExternalFilesDir(null), name).outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
            capture("explore.png")
            scenario.onActivity {
                it.game.engine.startBattle(false)
                it.game.down(0)
                it.game.up(0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
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
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            capture("trap.png")
        }
    }
}
