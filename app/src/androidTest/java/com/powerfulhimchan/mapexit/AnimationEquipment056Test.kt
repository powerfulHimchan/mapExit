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

/** Review captures use the production Android renderer and the real Activity. */
class AnimationEquipment056Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun equip(e: GameEngine,kind: Kind,variant: String) {
        val item=ItemFactory.create(e.state,kind,variantId=variant).copy(tier=1)
        e.state.inventory.add(item)
        val slot=if(item.weapon)Slot.RIGHT else if(kind==Kind.SHIELD)Slot.LEFT else Equipment.allowedSlots(item).first()
        assertTrue(Equipment.equip(e.state,item.id,slot))
    }
    private fun engine(kind: Kind=Kind.SWORD,steel: Boolean=true): GameEngine = GameEngine().also { e ->
        e.state.equipment.clear()
        equip(e,kind,when(kind) { Kind.SPEAR->"longspear";Kind.HAMMER->"warhammer";Kind.CLAW->"knuckle";Kind.WHIP->"leatherwhip";else->"longsword" })
        if(kind in listOf(Kind.SWORD,Kind.WHIP))equip(e,Kind.SHIELD,"shield")
        equip(e,Kind.HEAD,if(steel)"heavyhelm" else "leathercap")
        equip(e,Kind.BODY,if(steel)"platearmor" else "leatherarmor")
        equip(e,Kind.PANTS,if(steel)"platepants" else "scoutpants")
        equip(e,Kind.BOOTS,if(steel)"ironboots" else "windboots")
    }
    private fun battle(kind: Kind=Kind.SWORD): GameEngine = engine(kind).also { e ->
        e.enterFloor(1);e.startBattle(false);e.state.mode=Mode.COMBAT_ATTACK
        val monster=MonsterCatalog.forFloor(1).first { it.family=="golem" }
        e.state.enemy=e.state.enemy!!.copy(name=monster.name,speciesId=monster.id,intent=EnemyIntent.STRIKE,attack=1.0)
        e.state.hp=Equipment.stats(e.state).hp;e.state.rng=1L
    }
    private fun render(width: Int=480,height: Int=640,paint: androidx.compose.ui.graphics.drawscope.DrawScope.()->Unit): Bitmap {
        val image=ImageBitmap(width,height)
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(width.toFloat(),height.toFloat())) { paint() }
        return image.asAndroidBitmap()
    }
    private fun export(name: String,bitmap: Bitmap) {
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,if(name.startsWith("live-camp")) "mapexit-fix041-$name.png" else "mapexit-review056-$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
        }
        val resolver=context.contentResolver
        val uri=requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        resolver.update(uri,android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING,0) },null,null)
    }
    @Test fun campWalksBothWaysPracticesEachWeaponAndLoopsWithoutATeleport() {
        for(kind in listOf(Kind.SWORD,Kind.SPEAR,Kind.HAMMER,Kind.CLAW,Kind.WHIP)) {
            val samples=(0L until CampMotion056.PERIOD step 80).map { CampMotion056.sample(it,kind) }
            assertTrue(samples.minOf { it.travel }<-.10f)
            assertTrue(samples.maxOf { it.travel }>.10f)
            assertTrue(samples.maxOf { it.angle }-samples.minOf { it.angle }>35f)
            assertTrue(samples.any { it.stride<-.5f } && samples.any { it.stride>.5f })
            assertTrue(samples.zipWithNext().all { (a,b)->kotlin.math.abs(a.travel-b.travel)<.025f })
            assertEquals(CampMotion056.sample(0,kind),CampMotion056.sample(CampMotion056.PERIOD,kind))
        }
    }
    @Test fun damagingABlockKeepsItsGuardBodyWhileAPlainHitStillReacts() {
        for(ms in listOf(320L,350L,420L,520L)) {
            assertTrue(BattlePose056.frame("guard",Kind.SWORD,ms,1000,true,false).startsWith("guard_"))
            assertTrue(BattlePose056.frame(null,Kind.SWORD,ms,1000,true,false).startsWith("hit_"))
        }
    }
    @Test fun armSocketsStayInsideThePaintedSleevesThroughEveryBodyPose() {
        MotionArtwork.load(context.resources)
        val sheet=requireNotNull(BitmapFactory.decodeResource(context.resources,R.drawable.combat_hero_motion,BitmapFactory.Options().apply { inScaled=false }))
        for((index,pose) in MotionAtlas.heroPoses.withIndex()) {
            for(socket in listOf(MotionArtwork.shoulder(pose),MotionArtwork.leftShoulder(pose))) {
                val x=index%12*192+(socket.x*4+32).toInt()
                val y=index/12*192+(socket.y*4).toInt()
                assertTrue("$pose arm socket must overlap its native sleeve",sheet.getPixel(x,y) ushr 24>128)
            }
        }
        sheet.recycle()
    }
    @Test fun obstacleClothingPreservesEveryNativeAlphaAndEachPoseHasItsOwnFeet() {
        MotionArtwork.load(context.resources)
        val sheet=requireNotNull(BitmapFactory.decodeResource(context.resources,R.drawable.obstacle_hero_046,BitmapFactory.Options().apply { inScaled=false }))
        val feet=mutableSetOf<List<List<Float>>>()
        for((i,pose) in ObstacleAtlas.poses.withIndex()) {
            val fit=ObstacleFit056.forPose(pose);feet.add(fit.boots)
            assertEquals(2,fit.boots.size)
            for(steel in listOf(false,true)) {
                val e=engine(steel=steel)
                val armor=Equipment.equipped(e.state).first { it.kind==Kind.BODY }
                val raw=Bitmap.createBitmap(sheet,i%4*192,i/4*192,192,192)
                val dressed=requireNotNull(PreparationArtwork.dressedObstacleBody(armor,raw,pose))
                for(y in 0 until 192)for(x in 0 until 192)assertEquals("$pose clothing contour",raw.getPixel(x,y) ushr 24,dressed.getPixel(x,y) ushr 24)
                raw.recycle();dressed.recycle()
                val saved=SaveCodec.encode(e.state)
                export("obstacle-${if(steel)"steel" else "leather"}-$pose",render(360,360) {
                    drawRect(ink);with(ObstacleArtwork) { hero(e.state,180f,340f,208f,pose) }
                })
                assertEquals(saved,SaveCodec.encode(e.state))
            }
        }
        assertTrue("Jumping, crouching and pushing must not share a generic boot box",feet.size>=10)
        sheet.recycle()
    }
    @Test fun productionCampAndEveryCombatActionRenderWithoutAdvancingTheGame() {
        MotionArtwork.load(context.resources)
        val camp=engine();val savedCamp=SaveCodec.encode(camp.state)
        for(i in 0 until 64)export("camp-${i.toString().padStart(2,'0')}",render(640,400) { drawScene(camp.state,i*250L) })
        assertEquals(savedCamp,SaveCodec.encode(camp.state))
        for(kind in listOf(Kind.SWORD,Kind.SPEAR,Kind.HAMMER,Kind.CLAW,Kind.WHIP))for(action in listOf("attack","strong","guard")) {
            val e=battle(kind);val saved=SaveCodec.encode(e.state)
            val visual=BattleVisual(e.state.battleSnapshot(),action,1000,
                BattleImpact(50,if(action=="guard")ImpactTarget.PLAYER else ImpactTarget.ENEMY,if(action=="guard")1 else 0,
                    if(action=="guard")ImpactOutcome.DAMAGE else ImpactOutcome.MISS),selectedAt=500)
            for(ms in listOf(80L,190L,320L,390L,550L,720L))export("${kind.name.lowercase()}-$action-$ms",render {
                drawScene(e.state,1000+ms,battle=visual,battleTop=.26f)
            })
            assertEquals(saved,SaveCodec.encode(e.state))
        }
    }
    @Test fun bootsRemainTheForegroundLayerRegardlessOfEquipmentInsertionOrder() {
        MotionArtwork.load(context.resources)
        val e=battle();val state=e.state
        val entries=state.equipment.toList()
        fun frame()=render { drawScene(state,1000,battleTop=.26f) }
        state.equipment.clear();entries.sortedBy { if(it.first==Slot.BOOTS)0 else 1 }.forEach { state.equipment[it.first]=it.second }
        val a=frame()
        state.equipment.clear();entries.sortedBy { if(it.first==Slot.BOOTS)1 else 0 }.forEach { state.equipment[it.first]=it.second }
        val b=frame()
        assertTrue("Changing only slot insertion order must not put trousers over boots",a.sameAs(b))
    }
    @Test fun actualActivityShowsCampMotionCombatNpcAndFittedObstacleGear() {
        launchGame().use { scenario ->
            scenario.onGameActivity { a -> a.game.engine=engine();a.game.active=true;a.game.storyPlaybackEnabled=false;a.game.refresh() }
            for(i in 0..2) { SystemClock.sleep(1600);export("phone-camp-$i",requireNotNull(instrumentation.uiAutomation.takeScreenshot())) }
            scenario.onGameActivity { a -> a.game.engine=battle();a.game.active=false;a.game.refresh() }
            SystemClock.sleep(650);export("phone-battle",requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
            scenario.onGameActivity { a -> a.game.engine.state.mode=Mode.COMBAT_DEFEND;a.game.refresh();a.game.choose(Choice("방어","guard")) }
            SystemClock.sleep(650);export("phone-guard",requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
            scenario.onGameActivity { a ->
                val e=battle(Kind.SPEAR);e.state.enemy=null;e.state.mode=Mode.ENCOUNTER;e.state.encounter=Event.NPC
                e.state.dungeon!!.here().npcRole=NpcRole.QUEST
                a.game.engine=e;a.game.refresh()
            }
            SystemClock.sleep(650);export("phone-two-handed-npc",requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
            scenario.onGameActivity { a ->
                val e=engine();e.enterFloor(1);e.state.mode=Mode.TRAP
                e.state.trap=Traps.create(e.state,2).copy(phase=TrapPhase.ACTIVE,elapsedMs=1000,stepStartedMs=600)
                a.game.engine=e;a.game.refresh()
            }
            SystemClock.sleep(650);export("phone-obstacle",requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
        }
    }

    @Test fun actualPreparationAnimatesWithoutInputAndResumesAfterBackground() {
        launchGame().use { scenario ->
            scenario.onGameActivity { a ->
                a.game.engine=engine()
                a.game.storyPlaybackEnabled=false
                a.startGame()
                a.game.refresh()
                assertTrue(a.game.active)
                assertEquals(Mode.SURFACE,a.game.engine.state.mode)
            }
            fun sceneBounds(): android.graphics.Rect {
                fun find(node: android.view.accessibility.AccessibilityNodeInfo): android.graphics.Rect? {
                    if(node.contentDescription?.toString()=="메인 장면")
                        return android.graphics.Rect().also { node.getBoundsInScreen(it) }
                    for(i in 0 until node.childCount) {
                        val child=node.getChild(i) ?: continue
                        find(child)?.let { return it }
                    }
                    return null
                }
                return requireNotNull(find(requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)))
            }
            fun changesOverTime(label: String) {
                SystemClock.sleep(800)
                val bounds=sceneBounds()
                assertTrue(bounds.width()>100 && bounds.height()>100)
                val frames=mutableListOf<Bitmap>()
                repeat(8) { index ->
                    // The live Activity advances its own clock; never set a render time or send input.
                    SystemClock.sleep(380)
                    val screen=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                    export("live-camp-$label-$index",screen)
                    frames+=Bitmap.createBitmap(screen,
                        bounds.left+bounds.width()/5,bounds.top+bounds.height()/5,
                        bounds.width()*3/5,bounds.height()*3/4)
                    screen.recycle()
                }
                val first=frames.first()
                val largestChange=frames.drop(1).maxOf { frame ->
                    var changed=0
                    for(y in 0 until first.height step 2)for(x in 0 until first.width step 2) {
                        val a=first.getPixel(x,y);val b=frame.getPixel(x,y)
                        // Ignore the small brightness pulse of the background torches.
                        val difference=maxOf(kotlin.math.abs((a shr 16 and 255)-(b shr 16 and 255)),
                            kotlin.math.abs((a shr 8 and 255)-(b shr 8 and 255)),
                            kotlin.math.abs((a and 255)-(b and 255)))
                        if(difference>32)changed++
                    }
                    changed
                }
                frames.forEach { it.recycle() }
                assertTrue("Live preparation hero must animate without input ($label): $largestChange changed pixels",largestChange>100)
                scenario.onGameActivity { a -> assertEquals(Mode.SURFACE,a.game.engine.state.mode) }
            }
            changesOverTime("initial")
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            scenario.onGameActivity { a -> assertFalse(a.game.active) }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            scenario.onGameActivity { a -> assertTrue(a.game.active) }
            changesOverTime("resumed")
        }
    }
}

