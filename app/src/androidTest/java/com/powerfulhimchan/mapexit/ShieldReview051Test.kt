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
    @Test fun bodyArmorKeepsEveryNativeBodyPixelAndDoesNotCreateASecondSilhouette() {
        MotionArtwork.load(context.resources)
        val sheet=requireNotNull(android.graphics.BitmapFactory.decodeResource(context.resources,R.drawable.combat_hero_motion,
            android.graphics.BitmapFactory.Options().apply { inScaled=false }))
        val poses=MotionAtlas.heroPoses.withIndex().distinctBy { MotionAtlas.heroSourceCells[it.index] }
        for(variant in listOf("leatherarmor","chainmail","platearmor")) for((index,pose) in poses) {
            val e=battle();val item=ItemFactory.create(e.state,Kind.BODY,variantId=variant)
            val original=Bitmap.createBitmap(sheet,index%12*192,index/12*192,192,192)
            val dressed=requireNotNull(PreparationArtwork.dressedBody(item,original,pose))
            val head=MotionAtlas.worn[EquipmentFit.cell(pose)][0]
            var changed=0;var originalGreen=0;var remainingGreen=0
            fun green(color: Int)=color ushr 24>96 && android.graphics.Color.green(color)>25 &&
                android.graphics.Color.green(color)>android.graphics.Color.red(color)*1.06f &&
                android.graphics.Color.green(color)>android.graphics.Color.blue(color)*1.04f
            for(y in 0 until 192) for(x in 0 until 192) {
                val a=original.getPixel(x,y);val b=dressed.getPixel(x,y)
                assertEquals("$variant/$pose contour at $x,$y",a ushr 24,b ushr 24)
                if(x>=head[0] && x<head[0]+head[2] && y>=head[1] && y<head[1]+head[3]-5 &&
                    android.graphics.Color.green(a)<=android.graphics.Color.red(a)*1.05f)
                    assertEquals("Armor must preserve hair and skin",a,b)
                if(a!=b)changed++
                if(green(a)) { originalGreen++;if(green(b))remainingGreen++ }
            }
            assertTrue("Armor material must replace the native clothing",changed>150)
            if(variant!="leatherarmor")assertTrue("The old green coat must not protrude from metal armor: $remainingGreen/$originalGreen",
                remainingGreen<originalGreen*.02f)
            original.recycle();dressed.recycle()
        }
        sheet.recycle()
    }
    @Test fun everyShieldHandleAndForearmStrapShareTheActualArmAnchors() {
        MotionArtwork.load(context.resources)
        val e=battle()
        val hand=androidx.compose.ui.geometry.Offset(100f,100f)
        for(variant in listOf("shield","buckler","tower","spiked")) {
            val item=ItemFactory.create(e.state,Kind.SHIELD,variantId=variant)
            for(elbow in listOf(androidx.compose.ui.geometry.Offset(96f,80f),androidx.compose.ui.geometry.Offset(80f,90f),androidx.compose.ui.geometry.Offset(110f,90f))) {
                val mount=requireNotNull(PreparationArtwork.mountShield(item,hand,elbow))
                assertTrue((mount.project(mount.grip)-hand).getDistance()<.001f)
                assertTrue("The forearm strap must stay on the elbow, not float over the back",
                    (mount.project(mount.brace)-elbow).getDistance()<.001f)
            }
        }
    }
    @Test fun rearArmCuffsPointToTheirElbowsAcrossAllWeaponPoses() {
        val right=androidx.compose.ui.geometry.Offset(23f,23f)
        val left=androidx.compose.ui.geometry.Offset(12f,22f)
        for(kind in listOf(Kind.SWORD,Kind.SPEAR,Kind.HAMMER,Kind.CLAW,Kind.WHIP))
            for(strong in listOf(false,true)) for(ms in 0L..760L step 8) {
                val motion=BattleAnimation.pose(kind,ms,strong)
                val wanted=right+androidx.compose.ui.geometry.Offset(4+motion.reach*7,10-motion.advance*5-motion.armLift)
                val radians=Math.toRadians(motion.angle.toDouble())
                val shift=androidx.compose.ui.geometry.Offset(kotlin.math.sin(radians).toFloat()*4,-kotlin.math.cos(radians).toFloat()*4)
                fun point(p: androidx.compose.ui.geometry.Offset)=ArmKinematics.Point(p.x,p.y)
                val shared=ArmKinematics.sharedTarget(point(right),point(left),point(wanted),point(shift),12f)
                val hand=if(kind in listOf(Kind.SPEAR,Kind.HAMMER))androidx.compose.ui.geometry.Offset(shared.x,shared.y) else wanted
                val near=RearArmRig.solve(right,hand,1f,1f)
                val far=RearArmRig.solve(left,near.hand+shift,1f,-1f)
                for(arm in listOf(near,far)) {
                    assertTrue((arm.joints.elbow-arm.joints.shoulder).length()<=6.55f)
                    assertTrue((arm.joints.wrist-arm.joints.elbow).length()<=6.55f)
                    val e=androidx.compose.ui.geometry.Offset(arm.joints.elbow.x,arm.joints.elbow.y)
                    val towardElbow=(e-arm.hand)/(e-arm.hand).getDistance()
                    val angle=Math.toRadians(arm.gloveAngle.toDouble())
                    val cuff=androidx.compose.ui.geometry.Offset(-kotlin.math.sin(angle).toFloat(),kotlin.math.cos(angle).toFloat())
                    assertTrue("A rear glove cuff must meet the forearm, not follow the blade",cuff.x*towardElbow.x+cuff.y*towardElbow.y>.999f)
                }
                if(kind in listOf(Kind.SPEAR,Kind.HAMMER))assertTrue((far.hand-near.hand-shift).getDistance()<.001f)
            }
        val near=RearArmRig.solve(right,right+androidx.compose.ui.geometry.Offset(4f,10f),1f,1f)
        val far=RearArmRig.solve(left,left+androidx.compose.ui.geometry.Offset(-4f,10f),1f,-1f)
        assertTrue("Right elbow stays outside the back",near.joints.elbow.x>right.x)
        assertTrue("Left elbow bends the opposite way",far.joints.elbow.x<left.x)
    }
    @Test fun redrawingRearWristPreservesTheApprovedShieldAnchors() {
        val shoulder=androidx.compose.ui.geometry.Offset(100f,100f)
        for(guard in listOf(0f,.5f,1f)) {
            val offAngle=-6f+guard*18f
            val radians=Math.toRadians(offAngle.toDouble())
            val shift=androidx.compose.ui.geometry.Offset(-kotlin.math.sin(radians).toFloat(),kotlin.math.cos(radians).toFloat())*1.25f
            val target=shoulder+androidx.compose.ui.geometry.Offset(-4f+guard*5,11.5f-guard*4)
            val old=ArmKinematics.solve(ArmKinematics.Point(100f,100f),ArmKinematics.Point(target.x+shift.x,target.y+shift.y),6.5f,6.5f)
            val elbow=androidx.compose.ui.geometry.Offset(old.elbow.x,old.elbow.y)
            val hand=androidx.compose.ui.geometry.Offset(old.wrist.x,old.wrist.y)-shift
            val rear=RearArmRig.anchored(shoulder,elbow,hand,1f)
            assertEquals(hand,rear.hand)
            assertEquals(old.elbow,rear.joints.elbow)
        }
    }
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
    @Test fun rearArmsRenderTwoHandedWeaponActions() {
        MotionArtwork.load(context.resources)
        for(kind in listOf(Kind.SPEAR,Kind.HAMMER)) for(action in listOf("idle","attack","strong","guard","dodge")) {
            val e=battle(true)
            e.state.equipment.remove(Slot.LEFT)
            equip(e,kind,if(kind==Kind.SPEAR)"longspear" else "warhammer")
            val defending=action in listOf("guard","dodge")
            val visual=if(action=="idle")null else BattleVisual(e.state.battleSnapshot(),action,1000,
                BattleImpact(50,if(defending)ImpactTarget.PLAYER else ImpactTarget.ENEMY,0,ImpactOutcome.MISS))
            val image=ImageBitmap(480,640)
            CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(480f,640f)) {
                drawScene(e.state,if(action=="idle")1000 else 1350,battle=visual,battleTop=.26f)
            }
            export("rear-${kind.name.lowercase()}-$action",image.asAndroidBitmap())
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
