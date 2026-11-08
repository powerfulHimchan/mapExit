package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import org.junit.Assert.*
import org.junit.Test

class Obstacle046Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun export(name:String,bitmap:Bitmap) {
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"mapexit-obstacle046-$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
        }
        val resolver=context.contentResolver
        val uri=requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        resolver.update(uri,android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING,0) },null,null)
    }
    @Test fun allObstaclePosesRenderWithoutChangingCollisionTimersOrSaveState() {
        MotionArtwork.load(context.resources)
        for(type in 0..4)for(height in listOf(200,640)) {
            val e=GameEngine();e.enterFloor(1);e.state.mode=Mode.TRAP
            val t=Traps.create(e.state,type).copy(phase=TrapPhase.ACTIVE,elapsedMs=1000,stepStartedMs=600)
            e.state.trap=t
            val hashes=mutableSetOf<Int>()
            for(phase in listOf(TrapPhase.PREPARE,TrapPhase.ACTIVE,TrapPhase.RESULT)) {
                t.phase=phase;t.success=true;t.action=if(type==4)1 else 0;t.actionAtMs=1000
                val save=SaveCodec.encode(e.state)
                val image=ImageBitmap(480,height)
                CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(480f,height.toFloat())) { drawScene(e.state,2000) }
                val bitmap=image.asAndroidBitmap();val pixels=IntArray(480*height);bitmap.getPixels(pixels,0,480,0,0,480,height)
                hashes.add(pixels.contentHashCode());assertEquals(save,SaveCodec.encode(e.state))
                export("render-$type-${phase.name.lowercase()}-$height",bitmap)
            }
            assertTrue("Mechanism $type must visibly change state",hashes.size>=2)
        }
    }
    @Test fun actualObstacleUiUsesRefinedArtworkOnBothPhoneSizes() {
        for(compact in listOf(false,true)) {
            if(compact)shell("wm size 720x1280")
            try { launchGame().use { scenario ->
                for(type in 0..4) {
                    scenario.onGameActivity { a ->
                        val e=GameEngine();e.enterFloor(1);e.state.mode=Mode.TRAP
                        e.state.trap=Traps.create(e.state,type).copy(pattern=listOf(0),phase=if(type==3)TrapPhase.REVEAL else TrapPhase.ACTIVE,elapsedMs=1000,stepStartedMs=600)
                        a.game.engine=e;a.game.active=false;a.game.refresh()
                    }
                    SystemClock.sleep(180)
                    export("ui-$type-active-${if(compact)"compact" else "normal"}",requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
                    scenario.onGameActivity { a ->
                        val t=requireNotNull(a.game.engine.state.trap)
                        t.phase=TrapPhase.ACTIVE;t.elapsedMs=1000
                        repeat(if(type==2)t.required else 1) { a.game.engine.press(if(type==4)1 else 0) }
                        assertTrue("Obstacle $type should accept its existing correct action",t.success)
                        t.elapsedMs=320;a.game.refresh()
                    }
                    SystemClock.sleep(100)
                    export("ui-$type-result-${if(compact)"compact" else "normal"}",requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
                }
            } } finally { if(compact)shell("wm size reset") }
        }
    }
    @Test fun newAtlasesHaveRealAlphaAndFitDeviceTextureLimits() {
        for(id in listOf(R.drawable.obstacle_parts_046,R.drawable.obstacle_hero_046,R.drawable.combat_arms_046)) {
            val image=requireNotNull(BitmapFactory.decodeResource(context.resources,id,BitmapFactory.Options().apply { inScaled=false }))
            assertTrue(image.width<=4096 && image.height<=4096);assertTrue(image.hasAlpha())
            assertEquals(0,image.getPixel(image.width-1,image.height-1) ushr 24)
        }
    }
    private fun shell(command:String) { ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() } }
}
