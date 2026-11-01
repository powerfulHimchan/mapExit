package com.powerfulhimchan.mapexit

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.powerfulhimchan.mapexit.core.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Captures Compose without waiting for a continuously animating game to become idle. */
class RestoredUiTest {
    @Test fun restoredMenusAndExperienceRenderInTheActivity() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val ctx=instrumentation.targetContext
        val monitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
        ctx.startActivity(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val a=monitor.waitForActivityWithTimeout(30000) as? MainActivity ?: error("No activity")
        instrumentation.removeMonitor(monitor)
        val folder=File(ctx.filesDir,"render022").apply { mkdirs() }
        fun capture(name:String) {
            Thread.sleep(2200)
            val b=instrumentation.uiAutomation.takeScreenshot() ?: error("No screenshot")
            File(folder,"ui-$name.png").outputStream().use { assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it)) }
        }
        instrumentation.runOnMainSync {
            a.startGame();a.game.active=false;a.game.engine=GameEngine();a.game.engine.state.experience=64;a.game.refresh()
        }
        capture("home-xp")
        instrumentation.runOnMainSync {
            val e=a.game.engine;e.state.gold=1000;e.choose(e.choices().first { it.action=="shop" });a.game.refresh()
        }
        capture("shop-names")
        instrumentation.runOnMainSync {
            val e=a.game.engine;e.choose(e.choices().first { it.action=="buy" && e.state.shop.first { g->g.id==it.argument }.kind!=Kind.POTION });a.game.refresh()
        }
        capture("shop-detail")
        instrumentation.runOnMainSync {
            val e=a.game.engine;e.purchase(e.state.menu!!.argument,1);a.game.refresh()
        }
        capture("shop-purchased")
        instrumentation.runOnMainSync {
            val e=a.game.engine;e.navigateBack();e.enterFloor(1);e.state.levelUpNotice=LevelUpNotice(1,2,3);e.state.points=3;e.state.level=2;e.state.experience=29;a.game.refresh()
        }
        capture("dungeon-level-banner")
        instrumentation.runOnMainSync { a.game.openLevelUpGrowth() }
        capture("growth")
        instrumentation.runOnMainSync { assertEquals("growth",a.game.engine.state.menu?.type);assertEquals(29,a.game.engine.state.experience);a.finish() }
    }
}
