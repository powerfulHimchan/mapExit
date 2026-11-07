package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import android.os.Environment
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import org.junit.Assert.*
import org.junit.Test

class Progression041UiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun export(name: String, bitmap: Bitmap) {
        val resolver = instrumentation.targetContext.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "mapexit-fix041-$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }

    @Test fun everyMonsterFamilyHasFiveDifferentSilhouettesIncludingBosses() {
        for (family in listOf("slime", "knight", "bat", "spider", "golem", "wisp", "boss")) {
            val masks = (0..4).map { band ->
                FineSprites.all.getValue("monster_${band}_$family").rows.map { row -> row.map { it != '0' } }
            }
            assertEquals("$family must change shape in every region", 5, masks.distinct().size)
        }
    }

    @Test fun croppedArmorFitsTheHeroAndRemainsAlignedAcrossStridesAndBattleSizes() {
        val e = GameEngine()
        e.enterFloor(31)
        for ((kind, slot) in listOf(Kind.HEAD to Slot.HEAD, Kind.BODY to Slot.BODY, Kind.PANTS to Slot.PANTS, Kind.BOOTS to Slot.BOOTS)) {
            val gear = ItemFactory.create(e.state, kind, 31, prefixId = "light")
            e.state.inventory.add(gear)
            Equipment.equip(e.state, gear.id, slot)
            val art = wornArt(gear)
            assertTrue(art.width > 0 && art.height > 0)
            assertTrue("Crop the transparent icon margin", art.width < art.art.width || art.height < art.art.height)
            assertEquals(art, wornArt(gear.copy(prefixIntensity = PrefixIntensity.VERY)))
        }
        val saved = SaveCodec.encode(e.state)
        val poses = ImageBitmap(480, 240)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(poses), Size(480f, 240f)) {
            drawRect(ink)
            for ((index, stride) in listOf(-.8f, 0f, .8f).withIndex())
                adventurer(e.state, 32f + index * 144, 220f, 96f, back = false, articulated = true, stride = stride)
        }
        export("armor-strides", poses.asAndroidBitmap())
        assertEquals(saved, SaveCodec.encode(e.state))
        e.startBattle(false)
        val battleSaved = SaveCodec.encode(e.state)
        for (height in listOf(200, 640)) {
            val image = ImageBitmap(480, height)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(480f, height.toFloat())) { drawScene(e.state, 1000) }
            export("battle-$height", image.asAndroidBitmap())
        }
        assertEquals(battleSaved, SaveCodec.encode(e.state))
    }

    @Test fun unlockingAnAchievementShowsANativeToastWhileStoryIsOpen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val event = instrumentation.uiAutomation.executeAndWaitForEvent({
                scenario.onActivity { activity ->
                    val e = GameEngine()
                    e.enterFloor(10)
                    e.startBattle(true)
                    e.state.enemy!!.hp = .01
                    e.state.rng = 0
                    e.attack(false)
                    assertEquals(listOf("first-victory", "boss-10"), e.state.achievements.notices)
                    activity.game.engine = e
                    activity.game.storyPlaybackEnabled = true
                    activity.startGame()
                    activity.game.active = false
                    activity.game.refresh()
                    assertNotNull(activity.game.storyScene)
                }
            }, { observed ->
                observed.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                    observed.text.any { it.toString().contains("업적 달성 · 첫 승리") }
            }, 6000)
            assertNotNull(event)
            val second = instrumentation.uiAutomation.executeAndWaitForEvent({}, { observed ->
                observed.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                    observed.text.any { it.toString().contains("업적 달성 · 첫 관문 돌파") }
            }, 6000)
            assertNotNull(second)
            assertNotNull(instrumentation.uiAutomation.takeScreenshot())
        }
    }
}
