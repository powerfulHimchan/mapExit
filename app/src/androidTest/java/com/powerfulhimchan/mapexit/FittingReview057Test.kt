package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import org.junit.Assert.*
import org.junit.Test

class FittingReview057Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun bitmap(id: Int)=requireNotNull(BitmapFactory.decodeResource(context.resources,id,BitmapFactory.Options().apply { inScaled=false }))
    private fun equip(e: GameEngine,kind: Kind,variant: String) {
        val item=ItemFactory.create(e.state,kind,variantId=variant).copy(tier=1);e.state.inventory.add(item)
        assertTrue(Equipment.equip(e.state,item.id,if(item.weapon)Slot.RIGHT else if(kind==Kind.SHIELD)Slot.LEFT else Equipment.allowedSlots(item).first()))
    }
    private fun engine(kind: Kind=Kind.SWORD,steel: Boolean=true,head: Boolean=true)=GameEngine().also { e ->
        e.state.equipment.clear()
        equip(e,kind,when(kind) { Kind.SPEAR->"longspear";Kind.HAMMER->"warhammer";Kind.CLAW->"knuckle";Kind.WHIP->"leatherwhip";else->"longsword" })
        if(kind in listOf(Kind.SWORD,Kind.WHIP))equip(e,Kind.SHIELD,"shield")
        if(head)equip(e,Kind.HEAD,if(steel)"heavyhelm" else "leathercap")
        equip(e,Kind.BODY,if(steel)"platearmor" else "leatherarmor")
        equip(e,Kind.PANTS,if(steel)"platepants" else "scoutpants")
        equip(e,Kind.BOOTS,if(steel)"ironboots" else "windboots")
    }
    private fun battle(kind: Kind=Kind.SWORD)=engine(kind).also { e ->
        e.enterFloor(1);e.startBattle(false);e.state.mode=Mode.COMBAT_ATTACK
        val m=MonsterCatalog.forFloor(1).first { it.family=="golem" }
        e.state.enemy=e.state.enemy!!.copy(name=m.name,speciesId=m.id,intent=EnemyIntent.STRIKE,attack=1.0)
        e.state.hp=Equipment.stats(e.state).hp;e.state.rng=1L
    }
    private fun render(w: Int=480,h: Int=640,paint: androidx.compose.ui.graphics.drawscope.DrawScope.()->Unit): Bitmap {
        val i=ImageBitmap(w,h);CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(i),Size(w.toFloat(),h.toFloat())) { paint() }
        return i.asAndroidBitmap()
    }
    private fun export(name: String,b: Bitmap) {
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"mapexit-review057-$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
        }
        val resolver=context.contentResolver;val uri=requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,it)) }
        resolver.update(uri,android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING,0) },null,null)
    }
    @Test fun walkingUsesFourRealBodyFramesInEachTravelDirectionAndFrontWhenStopped() {
        val walk=bitmap(R.drawable.camp_walk_057);assertEquals(768,walk.width);assertEquals(384,walk.height)
        assertEquals(-1,CampMotion056.direction(2400));assertEquals(1,CampMotion056.direction(4800));assertEquals(-1,CampMotion056.direction(7200))
        assertEquals(0,CampMotion056.direction(1000));assertEquals(0,CampMotion056.direction(9300))
        for(direction in listOf(-1,1)) {
            val ids=(0..3).map { PresentationArtwork.campFrame(1600+it*140L,direction) }
            assertEquals(4,ids.toSet().size);assertTrue(ids.all { if(direction<0)it in 4..7 else it in 0..3 })
            val frames=ids.map { Bitmap.createBitmap(walk,it%4*192,it/4*192,192,192) }
            assertFalse(frames[0].sameAs(frames[1]));frames.forEach { it.recycle() }
        };walk.recycle()
    }
    @Test fun armorAndBootsPreserveNativeAnatomyInFrontSideAndEveryObstaclePose() {
        MotionArtwork.load(context.resources)
        val people=bitmap(R.drawable.social_people_049);val side=bitmap(R.drawable.camp_walk_057);val obstacle=bitmap(R.drawable.obstacle_hero_046)
        val sources=mutableListOf<Pair<Bitmap,NativeEquipment057.Layout>>()
        sources+=Bitmap.createBitmap(people,0,0,128,192) to NativeEquipment057.front
        for(i in 0..7)sources+=Bitmap.createBitmap(side,i%4*192,i/4*192,192,192) to NativeEquipment057.side(i)
        for((i,pose) in ObstacleAtlas.poses.withIndex())sources+=Bitmap.createBitmap(obstacle,i%4*192,i/4*192,192,192) to NativeEquipment057.obstacle(pose)
        for((raw,layout) in sources)for(steel in listOf(false,true)) {
            val dressed=NativeEquipment057.fit(raw,Equipment.equipped(engine(steel=steel,head=false).state),layout)
            val body=dressed.body.asAndroidBitmap();val boots=dressed.boots.asAndroidBitmap();var changed=0;var bootPixels=0
            for(y in 0 until raw.height)for(x in 0 until raw.width) {
                val a=raw.getPixel(x,y);val b=body.getPixel(x,y);val boot=boots.getPixel(x,y)
                assertEquals("No armor or footwear may enlarge the native body",a ushr 24,b ushr 24)
                assertTrue("Foreground feet must use exactly the original foot silhouette",boot ushr 24<=a ushr 24)
                if(a!=b)changed++;if(boot ushr 24>96)bootPixels++
            }
            assertTrue(changed>100);assertTrue(bootPixels>30)
        }
        sources.forEach { it.first.recycle() };people.recycle();side.recycle();obstacle.recycle()
    }
    @Test fun tiltedHelmetsKeepTheOriginalNeckConnectedInAllBattleFrames() {
        val sheet=bitmap(R.drawable.combat_hero_motion)
        for((i,pose) in MotionAtlas.heroPoses.withIndex()) {
            val raw=Bitmap.createBitmap(sheet,i%12*192,i/12*192,192,192);val original=IntArray(192*192)
            raw.getPixels(original,0,192,0,0,192,192);val pixels=original.copyOf();val head=NativeEquipment057.battleHead(pose)
            NativeEquipment057.clearHead(pixels,original,192,192,head)
            val index=head.neck.y.toInt()*192+head.neck.x.toInt()
            assertTrue("$pose neck is on the native torso",original[index] ushr 24>128)
            assertEquals("$pose native neck must survive helmet replacement",original[index],pixels[index])
            raw.recycle()
        };sheet.recycle()
    }
    @Test fun captureCampLoopAndFittedObstacleBodiesWithBothArmorFamilies() {
        MotionArtwork.load(context.resources);val e=engine();val saved=SaveCodec.encode(e.state)
        for(i in 0..63)export("camp-${i.toString().padStart(2,'0')}",render(640,400) { drawScene(e.state,i*250L) })
        val bare=engine(head=false)
        for(ms in listOf(0L,2400L,4800L))export("camp-no-helmet-$ms",render(640,400) { drawScene(bare.state,ms) })
        for(steel in listOf(false,true))for(pose in ObstacleAtlas.poses) {
            val o=engine(steel=steel)
            export("obstacle-${if(steel)"steel" else "leather"}-$pose",render(360,360) { drawRect(ink);with(ObstacleArtwork) { hero(o.state,180f,340f,208f,pose) } })
        }
        assertEquals(saved,SaveCodec.encode(e.state))
    }
    @Test fun captureHelmetNeckConnectionDuringEveryWeaponAttackAndGuard() {
        MotionArtwork.load(context.resources)
        for(kind in listOf(Kind.SWORD,Kind.SPEAR,Kind.HAMMER,Kind.CLAW,Kind.WHIP))for(action in listOf("attack","strong","guard")) {
            val e=battle(kind);val saved=SaveCodec.encode(e.state)
            val visual=BattleVisual(e.state.battleSnapshot(),action,1000,
                BattleImpact(50,if(action=="guard")ImpactTarget.PLAYER else ImpactTarget.ENEMY,if(action=="guard")1 else 0,
                    if(action=="guard")ImpactOutcome.DAMAGE else ImpactOutcome.MISS),selectedAt=500)
            for(ms in listOf(80L,190L,320L,390L,550L,720L))export("${kind.name.lowercase()}-$action-$ms",render { drawScene(e.state,1000+ms,battle=visual,battleTop=.26f) })
            assertEquals(saved,SaveCodec.encode(e.state))
        }
    }
    @Test fun captureActualActivityWithCampCombatAndObstacleEquipment() {
        launchGame().use { scenario ->
            scenario.onGameActivity { a ->a.game.engine=engine();a.game.active=true;a.game.storyPlaybackEnabled=false;a.game.refresh() }
            for(i in 0..3) { SystemClock.sleep(1600);export("phone-camp-$i",requireNotNull(instrumentation.uiAutomation.takeScreenshot())) }
            scenario.onGameActivity { a ->a.game.engine=battle();a.game.active=false;a.game.refresh() }
            SystemClock.sleep(650);export("phone-battle",requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
            scenario.onGameActivity { a ->a.game.engine.state.mode=Mode.COMBAT_DEFEND;a.game.refresh();a.game.choose(Choice("방어","guard")) }
            SystemClock.sleep(650);export("phone-guard",requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
            scenario.onGameActivity { a ->
                val e=engine();e.enterFloor(1);e.state.mode=Mode.TRAP
                e.state.trap=Traps.create(e.state,2).copy(phase=TrapPhase.ACTIVE,elapsedMs=1000,stepStartedMs=600)
                a.game.engine=e;a.game.refresh()
            }
            SystemClock.sleep(650);export("phone-obstacle",requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
        }
    }
}
