package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.powerfulhimchan.mapexit.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Assertions inspect the compiled native sprite pixels and actual floor shadow. */
class GroundContact053Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun render(w: Int=480,h: Int=400,paint: DrawScope.()->Unit): Bitmap {
        val image=ImageBitmap(w,h)
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(w.toFloat(),h.toFloat())) { paint() }
        return image.asAndroidBitmap()
    }
    private fun engine(boots: String?="ironboots")=GameEngine().also { e ->
        if(boots!=null) {
            val item=ItemFactory.create(e.state,Kind.BOOTS,variantId=boots)
            e.state.inventory.add(item);assertTrue(Equipment.equip(e.state,item.id,Slot.BOOTS))
        }
    }
    private fun export(name: String,bitmap: Bitmap) {
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"mapexit-ground053-$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
        }
        val resolver=context.contentResolver
        val uri=requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        resolver.update(uri,android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING,0) },null,null)
    }
    private fun pixels(bitmap: Bitmap)=IntArray(bitmap.width*bitmap.height).also {
        bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height)
    }
    @Test fun groundedObstacleSolesTouchTheFloorInEveryNativePoseAndBootMaterial() {
        MotionArtwork.load(context.resources)
        for(boots in listOf("ironboots","windboots")) for(width in listOf(64f,128f)) for(pose in ObstacleAtlas.poses) {
            val e=engine(boots)
            val bitmap=render { with(ObstacleArtwork) { hero(e.state,240f,320f,width,pose,Color.White) } }
            val rows=pixels(bitmap).withIndex().filter { (_,c) ->
                c ushr 24>96 && (c shr 16 and 255)>245 && (c shr 8 and 255)>245 && (c and 255)>245
            }
            assertTrue(rows.isNotEmpty())
            val sole=rows.maxOf { it.index/bitmap.width }+1f
            assertTrue("$pose/$boots/$width visible sole at $sole must touch floor 320",abs(sole-320f)<=1.5f)
            bitmap.recycle()
        }
    }
    @Test fun frontAndSideBootPixelsStayPlantedWhileTheCampHeroLeans() {
        MotionArtwork.load(context.resources)
        val e=engine()
        for(direction in listOf(-1,0,1)) for(frame in 0..3) for(angle in listOf(-6f,0f,6f)) for(width in listOf(64f,128f)) {
            val clock=1600L+frame*140
            val scale=width/128;val left=240f-width/2-if(direction==0)0f else scale*32
            val pivot=Offset(240f,320f)
            val p=GroundContact053.place(PresentationArtwork.campFootprint(clock,direction),left,scale,320f,pivot.x,angle)
            val baseline=p.top+192*scale
            val bitmap=render {
                withTransform({translate(0f,p.shiftY);rotate(angle,pivot)}) {
                    with(PresentationArtwork) {
                        frontBody(e.state,240f-width/2,baseline,width,clock=clock,direction=direction)
                        frontBoots(e.state,240f-width/2,baseline,width,clock,direction)
                    }
                }
            }
            val visible=pixels(bitmap).withIndex().filter { it.value ushr 24>96 }
            val sole=visible.maxOf { it.index/bitmap.width }+1f
            assertTrue("Camp $direction/$frame/$angle/$width sole $sole must stay on floor",abs(sole-320f)<=1.5f)
            bitmap.recycle()
        }
    }
    @Test fun jumpingHasOneSoftFloorShadowAndNoShadowAttachedToTheAirborneFeet() {
        MotionArtwork.load(context.resources)
        val e=engine();val masses=mutableListOf<Long>()
        for(lift in listOf(0f,35f,90f)) {
            val bitmap=render { with(ObstacleArtwork) { hero(e.state,240f,320f-lift,100f,"jump",Color.White,ground=320f) } }
            val dark=pixels(bitmap).withIndex().filter { (_,c) -> c ushr 24>0 &&
                (c shr 16 and 255)<20 && (c shr 8 and 255)<20 && (c and 255)<20 }
            assertTrue("A jump must retain a visible floor shadow",dark.isNotEmpty())
            assertTrue("No floating or duplicate shadow may follow the jumping sprite",
                dark.all { it.index/bitmap.width in 310..328 })
            assertTrue("Shadows blend with the floor instead of covering it with an opaque bar",dark.all { it.value ushr 24<200 })
            val cast=render {
                val extent=150f
                val p=GroundContact053.place(ObstacleArtwork.footprint("jump"),240f-extent/2,extent/192,320f,elevation=lift)
                with(GroundContact053) { shadow(p) }
            }
            masses+=pixels(cast).sumOf { (it ushr 24).toLong() };cast.recycle()
            export("isolated-jump-${lift.toInt()}",bitmap);bitmap.recycle()
        }
        assertTrue(masses[0]>masses[1] && masses[1]>masses[2])
    }
    @Test fun productionScenesCoverWalkingStandingJumpingCrouchingAndLandingWithoutChangingTheGame() {
        MotionArtwork.load(context.resources)
        assertTrue(BuildConfig.VERSION_CODE>=53)
        val camp=engine();val saved=SaveCodec.encode(camp.state)
        for(ms in listOf(0L,2400L,3800L,4800L,6800L,7200L,9300L,10000L,13000L))
            export("camp-$ms",render(640,400) { drawScene(camp.state,ms) })
        assertEquals(saved,SaveCodec.encode(camp.state))
        for(type in 0..4) for(height in listOf(200,640)) {
            val e=engine();e.enterFloor(1);e.state.mode=Mode.TRAP
            for(ms in listOf(0L,320L,650L)) {
                e.state.trap=Traps.create(e.state,type).copy(phase=TrapPhase.RESULT,success=true,
                    action=if(type==4)1 else 0,elapsedMs=ms,pattern=listOf(0),progress=1)
                val before=SaveCodec.encode(e.state)
                export("trap-$type-$height-$ms",render(480,height) { drawScene(e.state,1000) })
                assertEquals(before,SaveCodec.encode(e.state))
            }
        }
    }
    @Test fun actualActivityShowsGroundContactOnNormalAndCompactPhones() {
        for(compact in listOf(false,true)) {
            fun shell(command: String) { ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() } }
            if(compact)shell("wm size 720x1280")
            try { launchGame().use { scenario ->
                scenario.onGameActivity { a -> a.game.engine=engine();a.game.active=true;a.game.storyPlaybackEnabled=false;a.game.refresh() }
                repeat(3) { i ->
                    SystemClock.sleep(700)
                    export("phone-${if(compact)"compact" else "normal"}-camp-$i",requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
                }
                for(type in 0..4) {
                    scenario.onGameActivity { a ->
                        val e=engine();e.enterFloor(1);e.state.mode=Mode.TRAP
                        e.state.trap=Traps.create(e.state,type).copy(phase=TrapPhase.ACTIVE,elapsedMs=1000,stepStartedMs=600,pattern=listOf(0))
                        a.game.engine=e;a.game.active=false;a.game.refresh()
                    }
                    SystemClock.sleep(250)
                    export("phone-${if(compact)"compact" else "normal"}-trap-$type",requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
                }
                for(ms in listOf(0L,320L,650L)) {
                    scenario.onGameActivity { a ->
                        a.game.engine.state.trap=Traps.create(a.game.engine.state,1).copy(phase=TrapPhase.RESULT,success=true,action=0,elapsedMs=ms)
                        a.game.refresh()
                    }
                    SystemClock.sleep(180)
                    export("phone-${if(compact)"compact" else "normal"}-jump-$ms",requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
                }
            } } finally { if(compact)shell("wm size reset") }
        }
    }
}
