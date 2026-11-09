package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.powerfulhimchan.mapexit.core.*
import org.junit.Assert.*
import org.junit.Test

class Presentation047Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun export(name: String, bitmap: Bitmap) {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"mapexit-refined047-$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
        }
        val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        resolver.update(uri,android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING,0) },null,null)
    }
    private fun has(node: AccessibilityNodeInfo?, label: String): Boolean {
        if (node == null) return false
        if (node.text?.toString() == label || node.contentDescription?.toString() == label) return true
        return (0 until node.childCount).any { has(node.getChild(it),label) }
    }
    private fun capture(name: String, label: String) {
        val deadline = SystemClock.uptimeMillis()+10000
        while (SystemClock.uptimeMillis()<deadline) {
            instrumentation.runOnMainSync { }
            if (has(instrumentation.uiAutomation.rootInActiveWindow,label)) {
                SystemClock.sleep(160)
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                val colors = mutableSetOf<Int>()
                for (y in bitmap.height/5 until bitmap.height*2/3 step 12)
                    for (x in bitmap.width/4 until bitmap.width*3/4 step 12) colors.add(bitmap.getPixel(x,y))
                if (colors.size>30) { export(name,bitmap);return }
            }
            SystemClock.sleep(40)
        }
        error("Screen did not draw: $label")
    }
    private fun battle(): GameEngine = GameEngine().also {
        it.enterFloor(1);it.startBattle(false);it.state.mode=Mode.COMBAT_DEFEND
        it.state.hp=Equipment.stats(it.state).hp
        it.state.enemy=it.state.enemy!!.copy(intent=EnemyIntent.STRIKE,attack=80.0)
        it.state.rng=1L
    }

    @Test fun defensePreparationDelaysDamageAndRejectsRepeatedInputWhileAttackStillStartsImmediately() {
        for (action in listOf("guard","dodge")) launchGame().use { scenario ->
            scenario.onGameActivity { a ->
                val e=battle();a.game.engine=e;a.game.active=false;a.game.refresh()
                val hp=e.state.hp
                a.game.choose(Choice(action,action))
                val visual=requireNotNull(a.game.battleVisual)
                assertEquals(500L,visual.startedAt-visual.selectedAt)
                val impactAt=visual.startedAt+BattleAnimation.HIT_MS
                for (time in listOf(visual.selectedAt,visual.selectedAt+499,impactAt-1)) {
                    assertEquals(hp,a.game.presentationState(time).hp,.00001)
                    assertTrue(a.game.battleBusy(time))
                }
                assertEquals(e.state.hp,a.game.presentationState(impactAt).hp,.00001)
                val serial=e.state.impactSequence
                a.game.choose(Choice("일반 공격","attack"))
                assertEquals(serial,e.state.impactSequence)
                assertFalse(a.game.battleBusy(visual.startedAt+visual.duration))
                val stored=SaveCodec.encode(e.state)
                for (time in listOf(visual.selectedAt+250,visual.startedAt+160,impactAt)) {
                    val image=ImageBitmap(480,640)
                    CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(480f,640f)) {
                        drawScene(a.game.presentationState(time),time,battle=visual.frame(time))
                    }
                    export("$action-${time-visual.selectedAt}",image.asAndroidBitmap())
                }
                assertEquals(stored,SaveCodec.encode(e.state))
            }
        }
        launchGame().use { scenario -> scenario.onGameActivity { a ->
            val e=battle();e.state.mode=Mode.COMBAT_ATTACK;a.game.engine=e;a.game.active=false;a.game.refresh()
            a.game.choose(Choice("일반 공격","attack"))
            val visual=requireNotNull(a.game.battleVisual)
            assertEquals(visual.selectedAt,visual.startedAt)
        } }
    }

    @Test fun everyStoryPictureAndChestStageRendersWithoutChangingSaveState() {
        MotionArtwork.load(context.resources)
        assertEquals(Story.scenes.map { it.art }.toSet(),NarrativeArtwork.storyKeys.toSet())
        val e=GameEngine();e.enterFloor(1)
        val saved=SaveCodec.encode(e.state)
        val hashes=mutableSetOf<Int>()
        for (key in NarrativeArtwork.storyKeys) {
            val image=ImageBitmap(480,280)
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(480f,280f)) {
                storyArt(key,e.state)
            }
            val bitmap=image.asAndroidBitmap();val pixels=IntArray(480*280)
            bitmap.getPixels(pixels,0,480,0,0,480,280);hashes.add(pixels.contentHashCode())
            export("story-$key",bitmap)
        }
        assertEquals(21,hashes.size)
        e.state.mode=Mode.ENCOUNTER;e.state.encounter=Event.CHEST
        for (height in listOf(200,640)) {
            val before=SaveCodec.encode(e.state)
            val image=ImageBitmap(480,height)
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(480f,height.toFloat())) { drawScene(e.state,3000,battleTop=.28f) }
            export("chest-$height",image.asAndroidBitmap())
            assertEquals(before,SaveCodec.encode(e.state))
        }
        e.state.mode=Mode.EXPLORE;e.state.encounter=null
        assertEquals(saved,SaveCodec.encode(e.state))
    }

    @Test fun chestStoryAndMenuButtonsRemainVisibleOnNormalAndCompactPhones() {
        for (compact in listOf(false,true)) {
            if (compact) shell("wm size 720x1280")
            try { launchGame().use { scenario ->
                scenario.onGameActivity { a ->
                    val e=GameEngine();e.enterFloor(1);e.state.mode=Mode.ENCOUNTER;e.state.encounter=Event.CHEST
                    a.game.engine=e;a.game.active=false;a.game.storyPlaybackEnabled=false;a.game.refresh()
                }
                capture("chest-ui-$compact","상자 발견")
                for (action in listOf("equipment","inventory","shop","growth","bank","help")) {
                    scenario.onGameActivity { a ->
                        a.game.engine=GameEngine();a.game.active=false;a.game.refresh()
                        val choice=a.game.engine.choices().first { it.action==action }
                        a.game.choose(choice)
                    }
                    capture("menu-$action-$compact","돌아가기")
                }
                scenario.onGameActivity { a ->
                    val e=GameEngine();e.state.story.queue.clear();e.state.story.queue.add("prologue")
                    a.game.engine=e;a.game.storyPlaybackEnabled=true;a.game.active=false;a.game.refresh()
                }
                capture("story-ui-$compact","이야기 다음")
            } } finally { if(compact)shell("wm size reset") }
        }
    }
    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
}
