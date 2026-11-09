package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import android.graphics.Rect
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

class Presentation048Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun render(width: Int = 480, height: Int = 640, paint: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit): Bitmap {
        val image = ImageBitmap(width,height)
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(width.toFloat(),height.toFloat()),paint)
        return image.asAndroidBitmap()
    }
    private fun export(name: String, bitmap: Bitmap) {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"mapexit-refined048-$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
        }
        val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        resolver.update(uri,android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING,0) },null,null)
    }
    private fun gearSet(e: GameEngine, steel: Boolean) {
        val specs = listOf(Kind.HEAD to if(steel)"heavyhelm" else "leathercap",
            Kind.BODY to if(steel)"platearmor" else "leatherarmor",
            Kind.PANTS to if(steel)"platepants" else "scoutpants",
            Kind.BOOTS to if(steel)"ironboots" else "windboots",Kind.SHIELD to "shield")
        for ((kind,variant) in specs) {
            val item = ItemFactory.create(e.state,kind,variantId=variant).copy(tier=1)
            e.state.inventory.add(item)
            Equipment.equip(e.state,item.id,Equipment.allowedSlots(item).first())
        }
        val sword = ItemFactory.create(e.state,Kind.SWORD,variantId="longsword").copy(tier=1)
        e.state.inventory.add(sword);Equipment.equip(e.state,sword.id,Slot.RIGHT)
    }
    @Test fun everyArmorVariantUsesDistinctRearArtworkAndPreservesTierColorAndFrontIcon() {
        MotionArtwork.load(context.resources)
        assertTrue(PreparationArtwork.loaded)
        val e = GameEngine()
        for (variant in ArmorCatalog.all) {
            val item = ItemFactory.create(e.state,variant.kind,variantId=variant.id).copy(tier=1)
            assertTrue(PreparationArtwork.rearKey(item) in PreparationAtlas.rearKeys)
            fun painted(back: Boolean, tier: Int = 1) = render(160,200) {
                assertTrue(with(MotionArtwork) { paintWorn(item.copy(tier=tier),16f,12f,128f,176f,null,back=back) })
            }
            val rear = painted(true)
            assertFalse("Rear ${variant.id} must not reuse its front icon",rear.sameAs(painted(false)))
            val alpha = IntArray(160*200);rear.getPixels(alpha,0,160,0,0,160,200)
            assertTrue(alpha.count { it ushr 24 > 0 } > 300)
            if (variant.id in listOf("head","platearmor","ironboots"))
                assertFalse("Rear steel should retain tier tint",rear.sameAs(painted(true,7)))
        }
    }
    @Test fun rearEquipmentFollowsAttackDefenseAndFallenPosesWithoutChangingSaveOrBattleClocks() {
        MotionArtwork.load(context.resources)
        for (steel in listOf(false,true)) {
            val e = GameEngine();e.enterFloor(1);gearSet(e,steel);e.startBattle(false)
            for (action in listOf("attack","strong","guard","dodge")) {
                val before = SaveCodec.encode(e.state)
                val visual = BattleVisual(e.state.battleSnapshot(),action,1000,
                    BattleImpact(48,if(action in listOf("guard","dodge"))ImpactTarget.PLAYER else ImpactTarget.ENEMY,17,ImpactOutcome.DAMAGE))
                for (ms in listOf(120L,320L,600L)) {
                    export("rear-$steel-$action-$ms",render { drawScene(e.state,1000+ms,battle=visual,battleTop=.28f) })
                }
                assertEquals(before,SaveCodec.encode(e.state))
            }
            val visual = BattleVisual(e.state.battleSnapshot(),"guard",1000,BattleImpact(49,ImpactTarget.PLAYER,100,ImpactOutcome.DAMAGE))
            export("rear-$steel-fallen",render { drawScene(e.state.copy(hp=0.0),1500,battle=visual,battleTop=.28f) })
        }
    }
    private fun bounds(node: AccessibilityNodeInfo?,label: String): Rect? {
        if(node==null)return null
        if(node.text?.toString()==label || node.contentDescription?.toString()==label)return Rect().also { node.getBoundsInScreen(it) }
        for(i in 0 until node.childCount)bounds(node.getChild(i),label)?.let { return it }
        return null
    }
    private fun capture(name: String,label: String) {
        val end = SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<end) {
            instrumentation.runOnMainSync { }
            val root=instrumentation.uiAutomation.rootInActiveWindow
            val stage=bounds(root,"메인 장면")
            if(bounds(root,label)!=null && stage!=null && stage.width()>0 && stage.height()>0) {
                SystemClock.sleep(150)
                val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                val colors=mutableSetOf<Int>()
                for(y in stage.top.coerceAtLeast(0) until stage.bottom.coerceAtMost(bitmap.height) step 8)
                    for(x in stage.left.coerceAtLeast(0) until stage.right.coerceAtMost(bitmap.width) step 8)colors.add(bitmap.getPixel(x,y))
                if(colors.size>40) { export(name,bitmap);return }
            }
            SystemClock.sleep(40)
        }
        error("Scene not ready: $name/$label")
    }
    @Test fun preparationAndEnlargedMonstersRemainUsableOnBothPhoneSizes() {
        for(compact in listOf(false,true)) {
            if(compact)shell("wm size 720x1280")
            try { launchGame().use { scenario ->
                for(steel in listOf(false,true)) {
                    scenario.onGameActivity { a ->
                        val e=GameEngine();gearSet(e,steel)
                        a.game.engine=e;a.game.active=false;a.game.storyPlaybackEnabled=false;a.game.refresh()
                    }
                    capture("camp-$steel-$compact","출정 준비")
                }
                val species=MonsterCatalog.forFloor(1)+MonsterCatalog.special(10,true)
                for(monster in species) {
                    scenario.onGameActivity { a ->
                        val e=GameEngine();e.enterFloor(if(monster.family=="boss")10 else 1);gearSet(e,true);e.startBattle(false)
                        e.state.mode=Mode.COMBAT_ATTACK
                        e.state.enemy=e.state.enemy!!.copy(name=monster.name,speciesId=monster.id,boss=monster.family=="boss",intent=EnemyIntent.STRIKE)
                        a.game.engine=e;a.game.active=false;a.game.refresh()
                    }
                    capture("monster-${monster.family}-$compact","일반 공격")
                }
            } } finally { if(compact)shell("wm size reset") }
        }
    }
    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
}
