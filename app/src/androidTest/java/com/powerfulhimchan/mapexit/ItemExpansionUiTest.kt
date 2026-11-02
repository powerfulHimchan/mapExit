package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Environment
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.powerfulhimchan.mapexit.core.*
import org.junit.Assert.*
import org.junit.Test

class ItemExpansionUiTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun exportBitmap(name:String,bitmap:Bitmap) {
        val resolver=instrumentation.targetContext.contentResolver
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"mapexit-items040-$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
        }
        val uri=requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        resolver.update(uri,android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING,0) },null,null)
    }
    private fun screenshot(name:String) {
        instrumentation.waitForIdleSync();SystemClock.sleep(250)
        val b=instrumentation.uiAutomation.takeScreenshot()!!
        exportBitmap(name,b)
    }
    private fun seed(a:MainActivity,e:GameEngine) {
        a.game.storyPlaybackEnabled=false;a.game.engine=e;a.startGame();a.game.active=false;a.game.refresh()
    }
    @Test fun allExpandedIconsRenderAndEveryPrefixChangesTheVisibleSprite() {
        val s=State(bootstrapped=true,nextId=100)
        val gear=mutableListOf<Gear>()
        for(v in WeaponCatalog.all) gear.add(ItemFactory.create(s,v.kind,1,v.id))
        for(v in ArmorCatalog.all) gear.add(ItemFactory.create(s,v.kind,1,v.id))
        for(kind in PrefixCatalog.byKind.keys) {
            val base=gear.first { it.kind==kind }
            val keys=PrefixCatalog.byKind.getValue(kind).map { prefix ->
                val g=PrefixCatalog.apply(base.copy(id=s.nextId++),prefix.id);gear.add(g)
                GearSprites.get(gearSprite(g))!!.rows
            }
            assertEquals(5,keys.distinct().size)
        }
        val rings=mutableMapOf<String,Gear>()
        repeat(1000) { val g=ItemFactory.create(s,Kind.RING,1);rings[g.name.substringBefore(" (+")]=g }
        assertEquals(12,rings.size);gear.addAll(rings.values)
        gear.add(Achievements.specialRing(s.nextId++));gear.add(Achievements.abyssRing(s.nextId++))
        for((charm,name) in NecklaceCatalog.names) gear.add(Gear(s.nextId++,Kind.NECKLACE,name,charm=charm))
        for(spec in Consumables.all) gear.add(Consumables.create(s,spec))
        val bitmap=Bitmap.createBitmap(960,((gear.size+7)/8)*156,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.drawColor(0xff0b1820.toInt())
        val paint=Paint().apply { color=0xffd7f4c7.toInt();textSize=10f;isFilterBitmap=false }
        gear.forEachIndexed { i,g ->
            val key=gearSprite(g);val art=GearSprites.get(key)!!
            assertNotNull("Missing artwork $key",FineSprites.all[GearAppearance.base(g)])
            assertTrue(art.rows.any { row -> row.any { it!='0' } })
            val x=(i%8)*120;val y=(i/8)*156
            canvas.drawBitmap(art.bitmap.asAndroidBitmap(),null,android.graphics.Rect(x+12,y+4,x+108,y+124),paint)
            canvas.drawText(g.name.take(19),x+3f,y+139f,paint)
        }
        exportBitmap("atlas",bitmap)
    }
    @Test fun categorizedBagShowsConsumableAndUseSpendsCombatTurn() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val e=GameEngine();e.enterFloor(1)
                e.state.inventory.add(Consumables.create(e.state,Consumables.find("timed_attack")!!))
                e.choose(Choice("가방","inventory"));e.choose(Choice("시간 강화","inventoryFilter",5))
                seed(a,e)
            }
            screenshot("bag")
            scenario.onActivity { a ->
                val e=a.game.engine
                assertEquals(1,e.choices().count { it.action=="item" })
                e.navigateBack();e.startBattle(false);e.state.mode=Mode.COMBAT_ATTACK
                e.choose(Choice("강화","combatItems"));e.choose(e.choices().first { it.action=="item" })
                a.game.refresh()
            }
            screenshot("detail")
            scenario.onActivity { a ->
                val e=a.game.engine;e.choose(e.choices().first { it.action=="useItem" })
                assertEquals(Mode.COMBAT_DEFEND,e.state.mode)
                assertEquals("timed_attack",e.state.effects.single().itemId)
                assertTrue(Consumables.labels(e.state).single().contains("2:00"))
                a.game.refresh()
            }
            screenshot("buff")
        }
    }
}
