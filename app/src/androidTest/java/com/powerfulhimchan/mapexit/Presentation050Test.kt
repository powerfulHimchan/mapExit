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
import com.powerfulhimchan.mapexit.core.State
import org.junit.Assert.*
import org.junit.Test

class APresentation050Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun render(width: Int=480,height: Int=640,paint: androidx.compose.ui.graphics.drawscope.DrawScope.()->Unit): Bitmap {
        val image=ImageBitmap(width,height)
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(width.toFloat(),height.toFloat()),paint)
        return image.asAndroidBitmap()
    }
    private fun export(name: String,bitmap: Bitmap) {
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"mapexit-refined050-$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
        }
        val resolver=context.contentResolver
        val uri=requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        resolver.update(uri,android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING,0) },null,null)
    }
    private fun equip(e: GameEngine,kind: Kind,variant: String) {
        val item=ItemFactory.create(e.state,kind,variantId=variant).copy(tier=1)
        val slot=if(item.weapon)Slot.RIGHT else if(kind==Kind.SHIELD)Slot.LEFT else Equipment.allowedSlots(item).first()
        e.state.inventory.add(item);assertTrue(Equipment.equip(e.state,item.id,slot))
    }
    private fun battle(shield: String="shield",steel: Boolean=false): GameEngine = GameEngine().also { e ->
        e.enterFloor(1);e.state.equipment.clear()
        equip(e,Kind.SWORD,"longsword");equip(e,Kind.SHIELD,shield)
        equip(e,Kind.HEAD,if(steel)"heavyhelm" else "head")
        equip(e,Kind.BODY,if(steel)"platearmor" else "leatherarmor")
        equip(e,Kind.PANTS,if(steel)"platepants" else "scoutpants")
        equip(e,Kind.BOOTS,if(steel)"ironboots" else "windboots")
        e.startBattle(false);e.state.mode=Mode.COMBAT_ATTACK
        val monster=MonsterCatalog.forFloor(1).first { it.family=="golem" }
        e.state.enemy=e.state.enemy!!.copy(name=monster.name,speciesId=monster.id,intent=EnemyIntent.STRIKE)
    }
    @Test fun shieldsAndAllArmorStayAttachedAcrossEveryBodyPoseWithoutMutatingState() {
        MotionArtwork.load(context.resources)
        for(steel in listOf(false,true)) for(shield in listOf("shield","buckler","tower","spiked")) {
            val e=battle(shield,steel);val saved=SaveCodec.encode(e.state)
            for(action in listOf("attack","strong","guard","dodge")) {
                val visual=BattleVisual(e.state.battleSnapshot(),action,1000,
                    BattleImpact(50,if(action in listOf("guard","dodge"))ImpactTarget.PLAYER else ImpactTarget.ENEMY,0,ImpactOutcome.MISS))
                for(ms in listOf(120L,260L,350L,540L,700L)) {
                    val bitmap=render { drawScene(e.state,1000+ms,battle=visual,battleTop=.26f) }
                    if(shield=="shield" || action=="guard" && ms==260L)export("$steel-$shield-$action-$ms",bitmap)
                }
            }
            assertEquals(saved,SaveCodec.encode(e.state))
        }
        for(armor in ArmorCatalog.all.filter { it.kind in listOf(Kind.HEAD,Kind.BODY,Kind.PANTS) }) {
            val e=battle();equip(e,armor.kind,armor.id)
            export("variant-${armor.id}",render { drawScene(e.state,1000,battleTop=.26f) })
        }
    }
    @Test fun playerSwordPixelsRemainInFrontOfMonsterAndDialogueCharacter() {
        MotionArtwork.load(context.resources)
        val e=battle();e.state.equipment.remove(Slot.LEFT)
        val empty=e.state.copy(mode=Mode.VICTORY)
        val bare=empty.copy(equipment=empty.equipment.toMutableMap().apply { remove(Slot.RIGHT) })
        val near=render { drawScene(empty,1000,battleTop=.1f) }
        val noSword=render { drawScene(bare,1000,battleTop=.1f) }
        val npc=e.state.copy(enemy=null,mode=Mode.ENCOUNTER,encounter=Event.NPC)
        npc.dungeon!!.here().npcRole=NpcRole.QUEST
        fun distance(a: Int,b: Int) = kotlin.math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(b))+
            kotlin.math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(b))+
            kotlin.math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b))
        for((name,state) in listOf("monster" to e.state,"npc" to npc)) {
            val actual=render { drawScene(state,1000,battleTop=.1f) }
            val actualBare=render { drawScene(state.copy(equipment=state.equipment.toMutableMap().apply { remove(Slot.RIGHT) }),1000,battleTop=.1f) }
            export("overlap-$name",actual)
            export("overlap-$name-without-sword",actualBare)
            var samples=0;var referenceContrast=0L;var visibleContrast=0L
            for(y in 0 until near.height) for(x in 180 until near.width) {
                val a=near.getPixel(x,y);val b=noSword.getPixel(x,y)
                val contrast=distance(a,b)
                // Select metal pixels where the far actor really crosses the sword silhouette.
                if(contrast>160 && android.graphics.Color.red(a)>150 && distance(actualBare.getPixel(x,y),b)>40) {
                    samples++;referenceContrast+=contrast
                    visibleContrast+=distance(actual.getPixel(x,y),actualBare.getPixel(x,y))
                }
            }
            assertTrue("Expected a real sword/$name overlap, got $samples pixels",samples>10)
            // Painted blade pixels have alpha. Their RGB depends on the far actor underneath;
            // measure visible weapon contribution instead of demanding identical RGB values.
            assertTrue("Far $name must not hide the near sword: $visibleContrast/$referenceContrast",
                visibleContrast>referenceContrast*.25)
        }
    }

    @Test fun warningLeavesTheSceneCentreClearAndRemovesTheOldHeroRing() {
        MotionArtwork.load(context.resources)
        val e=battle();val maximum=Equipment.stats(e.state).hp
        val healthy=render { drawScene(e.state.copy(hp=maximum),1000,battleTop=.26f) }
        val low=render { drawScene(e.state.copy(hp=maximum*.08),1000,battleTop=.26f) }
        assertTrue("HP alone must no longer draw a ring or exclamation over the hero",healthy.sameAs(low))
        val edges=render { drawWarningEdges(1f) }
        assertEquals(0,android.graphics.Color.alpha(edges.getPixel(240,320)))
        assertTrue(android.graphics.Color.alpha(edges.getPixel(2,2))>0)
        export("edge-mask",edges)
    }
    private fun has(node: AccessibilityNodeInfo?,label: String): Boolean {
        if(node==null)return false
        if(node.text?.toString()==label || node.contentDescription?.toString()==label)return true
        return (0 until node.childCount).any { has(node.getChild(it),label) }
    }
    private fun capture(name: String,label: String) {
        val end=SystemClock.uptimeMillis()+8000
        while(SystemClock.uptimeMillis()<end) {
            instrumentation.runOnMainSync { }
            if(has(instrumentation.uiAutomation.rootInActiveWindow,label)) {
                SystemClock.sleep(200);export(name,requireNotNull(instrumentation.uiAutomation.takeScreenshot()));return
            }
            SystemClock.sleep(50)
        }
        error("Missing $label")
    }
    @Test fun lowHealthAndHealingRenderOnBothPhoneSizes() {
        for(compact in listOf(false,true)) {
            if(compact)shell("wm size 720x1280")
            try { launchGame().use { scenario ->
                scenario.onGameActivity { a ->
                    val e=battle(steel=true);e.state.hp=Equipment.stats(e.state).hp*.08
                    a.game.engine=e;a.game.active=false;a.game.storyPlaybackEnabled=false;a.game.refresh()
                }
                capture("danger-$compact","체력 위험 경고")
                scenario.onGameActivity { a -> a.game.engine.state.hp=Equipment.stats(a.game.engine.state).hp*.5;a.game.refresh() }
                capture("healed-$compact","체력")
                assertFalse(has(instrumentation.uiAutomation.rootInActiveWindow,"체력 위험 경고"))
            } } finally { if(compact)shell("wm size reset") }
        }
    }
    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
}
