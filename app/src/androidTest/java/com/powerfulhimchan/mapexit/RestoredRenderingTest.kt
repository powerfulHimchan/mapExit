package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.PI

class RestoredRenderingTest {
    private val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"render022").apply { mkdirs() }
    private fun export(name:String,b:Bitmap) {
        File(folder,"$name.png").outputStream().use { assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it)) }
    }
    private fun frame(name:String,s:State,h:Int,clock:Long=1000,motion:BattleVisual?=null) {
        val saved=SaveCodec.encode(s)
        val img=ImageBitmap(480,h)
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(img),Size(480f,h.toFloat())) { drawScene(s,clock,battle=motion) }
        assertEquals(saved,SaveCodec.encode(s))
        val p=IntArray(480*h);img.asAndroidBitmap().getPixels(p,0,480,0,0,480,h)
        assertTrue(p.all { it ushr 24==255 })
        assertTrue(p.distinct().size>50)
        export(name,img.asAndroidBitmap())
    }
    private fun corridor(event:Event):Dungeon {
        val m=Dungeon(9,9,MutableList(81){Cell()},4,7,0)
        for(y in 2..7)m.cell(4,y).wall=false
        m.cell(4,2).event=event
        return m
    }
    @Test fun fixturesUsePerspectiveAndRespectOcclusionAndResolution() {
        for(h in listOf(200,640)) for(event in listOf(Event.EXIT,Event.HEAL,Event.CHEST,Event.MERCHANT,Event.ELITE,Event.TRAP)) {
            val map=corridor(event);val r=DungeonRaster(480,h)
            for(y in listOf(6.5f,4.5f,3.5f)) {
                val view=DungeonViewPose(4.5f,y,(-PI/2).toFloat())
                export("world-$h-$event-$y",r.render(map,view).asAndroidBitmap())
            }
            val view=DungeonViewPose(4.5f,4.5f,(-PI/2).toFloat())
            val with=r.render(map,view).asAndroidBitmap().copy(Bitmap.Config.ARGB_8888,false)
            map.cell(4,2).resolved=true
            val without=r.render(map,view).asAndroidBitmap().copy(Bitmap.Config.ARGB_8888,false)
            assertFalse("Resolving $event must update its art",with.sameAs(without))
            if (event == Event.HEAL) {
                map.cell(4,2).event = Event.NONE
                assertFalse("Used crystal must remain visible",without.sameAs(r.render(map,view).asAndroidBitmap()))
                map.cell(4,2).event = Event.HEAL
            }
            map.cell(4,3).wall=true;map.cell(4,2).resolved=false
            val hidden=r.render(map,view).asAndroidBitmap().copy(Bitmap.Config.ARGB_8888,false)
            map.cell(4,2).resolved=true
            assertTrue("Wall hides $event",hidden.sameAs(r.render(map,view).asAndroidBitmap()))
        }
        val map=corridor(Event.HEAL);map.y=2;map.arrivalDirection=0
        for(floor in listOf(1,11,21,31,41)) frame("theme-$floor",State(bootstrapped=true,floor=floor,dungeon=map,mode=Mode.EXPLORE),640)
    }
    @Test fun bladesAndEveryOtherObstacleLandAtTheCenter() {
        for(h in listOf(200,640)) for(type in 0..4) {
            val e=GameEngine();e.enterFloor(1)
            val t=Traps.create(e.state,type);e.state.mode=Mode.TRAP;e.state.trap=t
            frame("trap-$h-$type-start",e.state,h)
            t.phase=TrapPhase.ACTIVE;t.elapsedMs=t.limitMs/2
            frame("trap-$h-$type-active",e.state,h)
            t.phase=TrapPhase.RESULT;t.action=if(type==4)1 else 0;t.actionAtMs=t.limitMs/2
            for(success in listOf(true,false)) {
                t.success=success;t.elapsedMs=325
                frame("trap-$h-$type-$success-mid",e.state,h)
                t.elapsedMs=899
                frame("trap-$h-$type-$success-land",e.state,h)
            }
        }
    }
    @Test fun allWeaponsAndEnemyActionsRenderDuringContactAndRecovery() {
        for(kind in listOf(Kind.SWORD,Kind.SPEAR,Kind.HAMMER,Kind.CLAW,Kind.WHIP)) {
            val e=GameEngine();e.enterFloor(5)
            val g=ItemFactory.create(e.state,kind);e.state.inventory.add(g);Equipment.equip(e.state,g.id,Slot.RIGHT)
            e.startBattle(false,elite=true)
            for(strong in listOf(false,true)) {
                val scene=e.state.battleSnapshot()
                val impact=BattleImpact(1,ImpactTarget.ENEMY,123,ImpactOutcome.DAMAGE,critical=strong)
                val visual=BattleVisual(scene,if(strong)"strong" else "attack",1000,impact)
                for(ms in listOf(120L,320L,430L,700L)) frame("combat-$kind-$strong-$ms",e.state,640,1000+ms,visual)
            }
        }
        for(h in listOf(200,640)) for(intent in EnemyIntent.entries) {
            val e=GameEngine();e.enterFloor(10);e.startBattle(true);e.state.enemy!!.intent=intent
            val impact=BattleImpact(1,ImpactTarget.PLAYER,21,ImpactOutcome.GUARD)
            val visual=BattleVisual(e.state.battleSnapshot(),"guard",1000,impact)
            for(ms in listOf(120L,210L,320L,700L)) frame("boss-$h-$intent-$ms",e.state,h,1000+ms,visual)
        }
        RestoredUiTest().restoredMenusAndExperienceRenderInTheActivity()
    }
    @Test fun reviewRepresentativeScenes() {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("wm density 240")
        ).use { it.readBytes() }
        for(event in listOf(Event.EXIT,Event.HEAL,Event.CHEST,Event.ELITE,Event.TRAP)) {
            val map=corridor(event);val raster=DungeonRaster(480,640)
            for(y in listOf(4.5f,3.5f)) export("review-world-$event-$y",raster.render(map,DungeonViewPose(4.5f,y,(-PI/2).toFloat())).asAndroidBitmap())
        }
        for(h in listOf(200,640)) {
            val e=GameEngine();e.enterFloor(1);e.state.mode=Mode.TRAP
            for(type in listOf(0,1,2,4)) {
                val t=Traps.create(e.state,type);e.state.trap=t
                if(type==1)frame("review-blade-start-$h",e.state,h)
                t.phase=TrapPhase.RESULT;t.success=true;t.action=if(type==4)1 else 0;t.elapsedMs=899
                frame("review-trap-$type-land-$h",e.state,h)
            }
            e.startBattle(true);e.state.enemy!!.intent=EnemyIntent.HEAVY
            val visual=BattleVisual(e.state.battleSnapshot(),"guard",1000,BattleImpact(1,ImpactTarget.PLAYER,40,ImpactOutcome.GUARD))
            frame("review-boss-wind-$h",e.state,h,1210,visual)
            frame("review-boss-hit-$h",e.state,h,1320,visual)
        }
        RestoredUiTest().restoredMenusAndExperienceRenderInTheActivity()
    }

}
