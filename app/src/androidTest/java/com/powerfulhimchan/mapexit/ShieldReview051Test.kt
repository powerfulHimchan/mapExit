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

/** Review evidence comes from the compiled Android renderer and running Activity. */
class AShieldReview051Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun equip(e: GameEngine,kind: Kind,variant: String) {
        val item=ItemFactory.create(e.state,kind,variantId=variant).copy(tier=1)
        e.state.inventory.add(item)
        val slot=if(item.weapon) Slot.RIGHT else if(kind==Kind.SHIELD) Slot.LEFT else Equipment.allowedSlots(item).first()
        assertTrue(Equipment.equip(e.state,item.id,slot))
    }
    private fun battle(steel: Boolean=false,shield: String="shield"): GameEngine = GameEngine().also { e ->
        e.enterFloor(1);e.state.equipment.clear()
        equip(e,Kind.SWORD,"longsword");equip(e,Kind.SHIELD,shield)
        equip(e,Kind.HEAD,if(steel)"heavyhelm" else "leathercap")
        equip(e,Kind.BODY,if(steel)"platearmor" else "leatherarmor")
        equip(e,Kind.PANTS,if(steel)"platepants" else "scoutpants")
        equip(e,Kind.BOOTS,if(steel)"ironboots" else "windboots")
        e.startBattle(false);e.state.mode=Mode.COMBAT_ATTACK
        val monster=MonsterCatalog.forFloor(1).first { it.family=="golem" }
        e.state.enemy=e.state.enemy!!.copy(name=monster.name,speciesId=monster.id,intent=EnemyIntent.STRIKE,attack=1.0)
        e.state.hp=Equipment.stats(e.state).hp;e.state.rng=1L
    }
    private fun export(name: String,bitmap: Bitmap) {
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"mapexit-shieldreview051-$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
        }
        val resolver=context.contentResolver
        val uri=requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        resolver.update(uri,android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING,0) },null,null)
    }
    @Test fun compiledRendererCapturesAllShieldTypesAndActionPoses() {
        MotionArtwork.load(context.resources)
        for(steel in listOf(false,true)) for(shield in listOf("shield","buckler","tower","spiked")) {
            val e=battle(steel,shield);val saved=SaveCodec.encode(e.state)
            for(action in listOf("idle","attack","strong","guard","dodge")) {
                val defending=action in listOf("guard","dodge")
                val visual=if(action=="idle") null else BattleVisual(e.state.battleSnapshot(),action,1000,
                    BattleImpact(50,if(defending) ImpactTarget.PLAYER else ImpactTarget.ENEMY,0,ImpactOutcome.MISS))
                val image=ImageBitmap(480,640)
                CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(480f,640f)) {
                    drawScene(e.state,if(action=="idle")1000 else 1350,battle=visual,battleTop=.26f)
                }
                export("$steel-$shield-$action",image.asAndroidBitmap())
            }
            assertEquals(saved,SaveCodec.encode(e.state))
        }
    }
    private fun has(node: AccessibilityNodeInfo?,label: String): Boolean {
        if(node==null)return false
        if(node.text?.toString()?.contains(label)==true || node.contentDescription?.toString()==label)return true
        return (0 until node.childCount).any { has(node.getChild(it),label) }
    }
    private fun ready(label: String) {
        val end=SystemClock.uptimeMillis()+8000
        while(SystemClock.uptimeMillis()<end) {
            instrumentation.runOnMainSync { }
            if(has(instrumentation.uiAutomation.rootInActiveWindow,label)) { SystemClock.sleep(650);return }
            SystemClock.sleep(40)
        }
        error("Missing screen: $label")
    }
    @Test fun runningGameCapturesIdleAttackGuardAndNpcOnActualPhone() {
        for(steel in listOf(false,true)) for(action in listOf("idle","attack","guard","npc")) {
            launchGame().use { scenario ->
                scenario.onGameActivity { a ->
                    val e=battle(steel)
                    if(action=="guard")e.state.mode=Mode.COMBAT_DEFEND
                    if(action=="npc") {
                        e.state.enemy=null;e.state.mode=Mode.ENCOUNTER;e.state.encounter=Event.NPC
                        e.state.dungeon!!.here().npcRole=NpcRole.QUEST
                    }
                    a.game.engine=e;a.game.active=false;a.game.storyPlaybackEnabled=false;a.game.refresh()
                }
                ready(if(action=="npc")"의뢰" else "몬스터 HP")
                if(action in listOf("attack","guard")) {
                    scenario.onGameActivity { a -> a.game.choose(Choice(action,action)) }
                    SystemClock.sleep(if(action=="guard")300 else 180)
                }
                export("phone-$steel-$action",requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
            }
        }
    }
}
