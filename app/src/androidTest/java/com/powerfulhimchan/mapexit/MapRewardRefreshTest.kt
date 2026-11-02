package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.powerfulhimchan.mapexit.core.*
import org.junit.Assert.*
import org.junit.Test

class MapRewardRefreshTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun find(node:AccessibilityNodeInfo?,label:String,textOnly:Boolean=false):Rect? {
        if(node==null)return null
        if((!textOnly && node.contentDescription?.toString()==label) || node.text?.toString()==label)
            return Rect().also{node.getBoundsInScreen(it)}
        for(i in 0 until node.childCount)find(node.getChild(i),label,textOnly)?.let{return it}
        return null
    }
    private fun scroll(node:AccessibilityNodeInfo?):Boolean {
        if(node==null)return false
        if(node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true
        for(i in 0 until node.childCount)if(scroll(node.getChild(i)))return true
        return false
    }
    private fun bounds(label:String,allowScroll:Boolean=false,textOnly:Boolean=false):Rect {
        val end=SystemClock.uptimeMillis()+8000
        while(SystemClock.uptimeMillis()<end){
            instrumentation.runOnMainSync{}
            val root=instrumentation.uiAutomation.rootInActiveWindow
            find(root,label,textOnly)?.let{if(it.width()>0 && it.height()>0)return it}
            if(allowScroll)scroll(root)
            SystemClock.sleep(50)
        }
        error("Missing UI: $label")
    }
    private fun mapFrame():Bitmap {
        SystemClock.sleep(150)
        val r=bounds("주변 미니맵")
        val frame=instrumentation.uiAutomation.takeScreenshot()!!
        return Bitmap.createBitmap(frame,r.left,r.top,r.width(),r.height()).also{frame.recycle()}
    }
    private fun seed(a:MainActivity,e:GameEngine){
        a.game.engine=e;a.game.storyPlaybackEnabled=false;a.startGame();a.game.active=false;a.game.refresh()
    }
    @Test fun movingAndTurningRepaintTheExistingMapCanvasWithoutNavigatingAway(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            lateinit var original:State
            scenario.onActivity{a->
                val e=GameEngine();e.enterFloor(1)
                val m=Dungeon(9,9,MutableList(81){Cell(seen=true)},1,1,1)
                for(x in 1..6)m.cell(x,1).wall=false
                for(y in 1..6)m.cell(6,y).wall=false
                e.state.dungeon=m;e.state.mode=Mode.EXPLORE;original=e.state
                seed(a,e)
            }
            bounds("주변 미니맵");val frame=bounds("주변 미니맵")
            val east=mapFrame()
            scenario.onActivity{a->a.game.engine.turnBack();a.game.refresh()}
            val west=mapFrame()
            assertFalse("Direction changes must invalidate pixels in the same Canvas",east.sameAs(west))
            assertEquals(frame,bounds("주변 미니맵"))
            scenario.onActivity{a->
                a.game.engine.turnBack();a.game.engine.state.mode=Mode.MOVING
                a.game.engine.state.movementMs=0;a.game.refresh()
            }
            val before=mapFrame()
            scenario.onActivity{a->
                a.game.active=true;repeat(3){a.game.tick(250)};a.game.active=false
                assertSame("The mutable State reference must stay identical",original,a.game.engine.state)
                assertEquals(Mode.MOVING,a.game.engine.state.mode)
                assertEquals(2,a.game.engine.state.dungeon!!.x)
            }
            val after=mapFrame()
            assertFalse("Walking must repaint map cells before any screen transition",before.sameAs(after))
            assertEquals(frame,bounds("주변 미니맵"))
            listOf(east,west,before,after).forEach{it.recycle()}
        }
    }
    @Test fun rewardCountUpdatesInPlaceAndNeverIncludesAlreadyClaimedRewards(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{a->
                val e=GameEngine(State(bootstrapped=true,mode=Mode.MENU,menu=Menu("help")))
                e.state.achievements.unlocked.addAll(listOf("first-victory","traps-20","quests-5"))
                e.state.achievements.claimed.add("first-victory");e.state.achievements.notices.clear()
                seed(a,e)
            }
            bounds("업적 (2)",allowScroll=true)
            scenario.onActivity{it.game.engine.state.achievements.claimed.add("traps-20");it.game.refresh()}
            bounds("업적 (1)")
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow,"업적 (2)"))
            scenario.onActivity{it.game.engine.state.achievements.claimed.add("quests-5");it.game.refresh()}
            bounds("업적",textOnly=true)
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow,"업적 (1)"))
            scenario.onActivity{it.game.choose(Choice("업적","achievements"))}
            bounds("칭호: 없음")
            bounds("업적",textOnly=true)
            scenario.onActivity{it.game.engine.state.achievements.claimed.remove("quests-5");it.game.refresh()}
            bounds("업적 (1)")
        }
    }
}
