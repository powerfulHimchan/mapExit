package com.powerfulhimchan.mapexit

import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.powerfulhimchan.mapexit.core.*
import org.junit.Assert.*
import org.junit.Test

class StoryScreenTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun find(node: AccessibilityNodeInfo?, label: String): Rect? {
        if(node==null) return null
        if(node.contentDescription?.toString()==label || node.text?.toString()==label)
            return Rect().also { node.getBoundsInScreen(it) }
        for(i in 0 until node.childCount) find(node.getChild(i),label)?.let { return it }
        return null
    }
    private fun bounds(label: String): Rect {
        val deadline=SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<deadline) {
            find(instrumentation.uiAutomation.rootInActiveWindow,label)?.let { return it }
            SystemClock.sleep(80)
        }
        error("Missing story UI: $label")
    }
    private fun tap(label: String) {
        val b=bounds(label);val now=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,b.exactCenterX(),b.exactCenterY(),0).also {
                instrumentation.sendPointerSync(it);it.recycle()
            }
            SystemClock.sleep(70)
        }
        SystemClock.sleep(180)
    }
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).use { it.readBytes() }
    private fun capture(name: String) {
        SystemClock.sleep(350)
        var rendered = false
        for(attempt in 0..29) {
            val frame = instrumentation.uiAutomation.takeScreenshot() ?: error("No story frame")
            rendered = listOf(1,2,3).any { x ->
                val pixel=frame.getPixel(frame.width*x/4,frame.height*2/3)
                android.graphics.Color.red(pixel)<220 || android.graphics.Color.green(pixel)<220 || android.graphics.Color.blue(pixel)<220
            }
            frame.recycle()
            if(rendered) break
            SystemClock.sleep(100)
        }
        assertTrue("Story must render before capture: $name",rendered)
        shell("screencap -p /sdcard/Download/mapexit-$name.png")
    }
    private fun seed(a: MainActivity, id: String = "prologue", cycle: Int = 1) {
        val e=GameEngine(State(cycle=cycle))
        e.state.story.queue.clear()
        if(id !in e.state.story.unlocked) e.state.story.unlocked.add(id)
        e.state.story.queue.add(id);e.state.story.page=0
        a.game.engine=e;a.game.storyPlaybackEnabled=true;a.startGame();a.game.active=false;a.game.refresh()
    }

    @Test fun dialoguePagePersistsAndJournalReplayDoesNotChangeProgress() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { seed(it) }
            bounds("10층까지만");capture("story-prologue-normal")
            tap("이야기 다음")
            scenario.onActivity {
                assertEquals(1,it.game.storyPage)
                val restored=GameEngine(SaveCodec.decode(SaveCodec.encode(it.game.engine.state)))
                it.game.engine=restored;it.game.refresh()
            }
            scenario.recreate()
            scenario.onActivity { assertEquals(1,it.game.storyPage);assertEquals("prologue",it.game.storyScene?.id) }
            tap("나중에 읽기")
            scenario.onActivity {
                assertNull(it.game.storyScene)
                it.game.choose(Choice("기록","help"))
                it.game.choose(Choice("이야기","storyRecords"))
            }
            bounds("발견한 이야기 1개 · 새로 발견한 순서")
            capture("story-journal-normal")
            tap("이야기 다시 보기 · 10층까지만")
            scenario.onActivity { assertTrue(it.game.replayingStory);assertEquals(0,it.game.storyPage) }
            tap("기록으로")
            scenario.onActivity {
                assertNull(it.game.storyScene)
                assertEquals("storyRecords",it.game.engine.state.menu?.type)
                assertEquals(listOf("prologue"),it.game.engine.state.story.unlocked)
                assertTrue(it.game.engine.state.story.queue.isEmpty())
                it.game.active=false
            }
        }
    }
    @Test fun activeStoryBlocksGameInputAndFreezesMovementAndExpeditionTime() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val e=GameEngine();e.state.story.queue.clear();e.enterFloor(2)
                e.state.mode=Mode.MOVING;e.state.movementMs=123
                a.game.engine=e;a.game.storyPlaybackEnabled=true;a.startGame();a.game.refresh()
                val elapsed=e.state.runElapsedMs;val play=e.state.playMs
                a.game.tick(200);a.game.choose(Choice("멈추기","stop"));a.game.down(1);a.game.up(1)
                assertEquals(123L,e.state.movementMs)
                assertEquals(elapsed,e.state.runElapsedMs);assertEquals(play,e.state.playMs)
                assertEquals(Mode.MOVING,e.state.mode)
                assertEquals("floor_2",a.game.storyScene?.id)
                a.game.closeStory();assertNull(a.game.storyScene)
                a.game.tick(200);assertTrue(e.state.playMs>play)
                // Stop the resumed moving scene before ActivityScenario waits for teardown idleness.
                a.game.active=false
            }
        }
    }
    @Test fun plotScenesAndPossessedBossArtRenderInNormalAndCompactLayouts() {
        for(compact in listOf(false,true)) {
            if(compact) shell("wm size 720x1280")
            try {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    for(id in listOf("contract","revelation","inheritance","confession","possessed")) {
                        scenario.onActivity { seed(it,id,if(id in listOf("confession","possessed"))2 else 1) }
                        bounds(Story.find(id)!!.title)
                        val action=bounds("이야기 다음")
                        assertTrue(action.height()>0 && action.width()>0)
                        capture("story-$id-${if(compact)"compact" else "normal"}")
                    }
                }
            } finally { if(compact) shell("wm size reset") }
        }
    }
    @Test fun legacyFightRemainsVisibleUntilNextSurfaceRecap() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val source=GameEngine();source.enterFloor(20);source.startBattle(true)
                val legacy=source.state.copy(story=StoryProgress())
                a.game.engine=GameEngine(legacy);a.game.storyPlaybackEnabled=true;a.startGame();a.game.active=false;a.game.refresh()
                assertNull(a.game.storyScene)
                assertNotNull(a.game.engine.state.enemy)
                a.game.engine.die();a.game.refresh()
                assertNull(a.game.storyScene)
                a.game.choose(Choice("준비","runContinue"))
                assertEquals("resurrection",a.game.storyScene?.id)
                a.game.closeStory()
                assertEquals("recap_contract",a.game.storyScene?.id)
            }
            bounds("지금까지의 이야기");capture("story-legacy-recap")
        }
    }
}
