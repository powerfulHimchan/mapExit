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

class Presentation049Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun render(width: Int=480,height: Int=640,paint: androidx.compose.ui.graphics.drawscope.DrawScope.()->Unit): Bitmap {
        val image=ImageBitmap(width,height)
        CanvasDrawScope().draw(Density(1f),LayoutDirection.Ltr,Canvas(image),Size(width.toFloat(),height.toFloat()),paint)
        return image.asAndroidBitmap()
    }
    private fun export(name: String,bitmap: Bitmap) {
        val resolver=context.contentResolver
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"mapexit-refined049-$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
        }
        val uri=requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        resolver.update(uri,android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING,0) },null,null)
    }
    private fun fingerprint(bitmap: Bitmap): Int {
        val pixels=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        return pixels.contentHashCode()
    }
    private fun npc(role: NpcRole): GameEngine = GameEngine(State(gold=100,hp=100.0)).also { e ->
        e.enterFloor(4)
        e.state.dungeon!!.here().also { it.event=Event.NPC;it.npcRole=role;it.questOffer=Quest("길을 지켜 주세요","kill",3) }
        e.state.mode=Mode.ENCOUNTER;e.state.encounter=Event.NPC
    }
    @Test fun frontGearAndEveryDialogueRoleRenderWithoutChangingSaveAndGrowthIconsAreDistinct() {
        MotionArtwork.load(context.resources)
        assertTrue(PresentationArtwork.loaded)
        val icons=SocialAtlas.icons.map { key ->
            val bitmap=render(96,96) { assertTrue(with(PresentationArtwork) { growthIcon(key) }) }
            export("growth-$key",bitmap);fingerprint(bitmap)
        }
        assertEquals(8,icons.toSet().size)
        for(kind in listOf(Kind.SWORD,Kind.SPEAR,Kind.HAMMER,Kind.CLAW,Kind.WHIP)) {
            val e=GameEngine();e.state.equipment.clear()
            val weapon=ItemFactory.create(e.state,kind).copy(tier=1)
            e.state.inventory.add(weapon);Equipment.equip(e.state,weapon.id,Slot.RIGHT)
            val before=SaveCodec.encode(e.state)
            export("front-${kind.name.lowercase()}",render(height=200) { drawScene(e.state,1000) })
            assertEquals(before,SaveCodec.encode(e.state))
        }
        val roles=NpcRole.entries.map { role ->
            val e=npc(role);val before=SaveCodec.encode(e.state)
            val bitmap=render { drawScene(e.state,1000,battleTop=.30f) }
            export("npc-${role.name.lowercase()}",bitmap)
            assertEquals(before,SaveCodec.encode(e.state));fingerprint(bitmap)
        }
        assertEquals(4,roles.toSet().size)
    }
    private fun find(node: AccessibilityNodeInfo?,label: String): AccessibilityNodeInfo? {
        if(node==null)return null
        if(node.text?.toString()==label || node.contentDescription?.toString()==label)return node
        for(i in 0 until node.childCount)find(node.getChild(i),label)?.let { return it }
        return null
    }
    private fun await(label: String): AccessibilityNodeInfo {
        val end=SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<end) {
            instrumentation.runOnMainSync { }
            find(instrumentation.uiAutomation.rootInActiveWindow,label)?.let { return it }
            SystemClock.sleep(50)
        }
        error("Missing control: $label")
    }
    private fun capture(name: String,label: String) {
        await(label);SystemClock.sleep(500)
        export(name,requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
    }
    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
    @Test fun frontPreparationNpcDialoguesAndGrowthFitNormalAndCompactScreens() {
        for(compact in listOf(false,true)) {
            if(compact)shell("wm size 720x1280")
            try { launchGame().use { scenario ->
                scenario.onGameActivity { a -> a.game.engine=GameEngine();a.game.active=false;a.game.storyPlaybackEnabled=false;a.game.refresh() }
                capture("camp-$compact","출정 준비")
                for(role in NpcRole.entries) {
                    scenario.onGameActivity { a -> a.game.engine=npc(role);a.game.refresh() }
                    capture("npc-${role.name.lowercase()}-$compact",role.title)
                }
                scenario.onGameActivity { a ->
                    val e=npc(NpcRole.QUEST);e.state.encounter=Event.MERCHANT;e.state.dungeon!!.here().event=Event.MERCHANT
                    a.game.engine=e;a.game.refresh()
                }
                capture("merchant-$compact","떠돌이 상인")
                scenario.onGameActivity { a ->
                    val e=GameEngine();e.state.points=3;e.state.hp=100.0
                    e.choose(Choice("성장","growth"));a.game.engine=e;a.game.refresh()
                }
                capture("growth-$compact","능력치 성장")
            } } finally { if(compact)shell("wm size reset") }
        }
    }
    @Test fun shopCardsShowTheDiscountedAmountActuallyChargedAndKeepTheNameAction() {
        for(compact in listOf(false,true)) {
            if(compact)shell("wm size 720x1280")
            try { for(discount in listOf(false,true)) launchGame().use { scenario ->
                var itemId=0L;var expected=0;var name=""
                scenario.onGameActivity { a ->
                    val e=GameEngine(State(gold=5000))
                    if(discount) {
                        val charm=Gear(e.state.nextId++,Kind.NECKLACE,"상인의 목걸이",charm=Charm.MERCHANT)
                        e.state.inventory.add(charm);Equipment.equip(e.state,charm.id,Slot.NECK)
                    }
                    val gear=ItemFactory.create(e.state,Kind.SWORD,variantId="longsword").copy(name="가격 확인용 검",price=125)
                    e.state.shop.clear();e.state.shop.add(gear);itemId=gear.id;name=gear.name
                    expected=Equipment.buyPrice(e.state,gear)
                    assertEquals(if(discount)100 else 125,expected)
                    e.choose(Choice("상점","shop"));a.game.engine=e;a.game.active=false;a.game.storyPlaybackEnabled=false;a.game.refresh()
                }
                val card=await(name)
                if(android.os.Build.VERSION.SDK_INT>=30)assertEquals("구매 가격 ${expected}G",card.stateDescription?.toString())
                capture("shop-$discount-$compact",name)
                assertTrue(card.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                val purchase=await("구매")
                SystemClock.sleep(180);assertTrue(purchase.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                await("소지 ${5000-expected}G")
                scenario.onGameActivity { a ->
                    assertEquals(5000-expected,a.game.engine.state.gold)
                    assertTrue(a.game.engine.state.inventory.any { it.id==itemId })
                }
            } } finally { if(compact)shell("wm size reset") }
        }
    }
}
