package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
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

class Battle042UiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun export(name: String, bitmap: Bitmap) {
        val resolver = instrumentation.targetContext.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "mapexit-battle042-$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }

    private fun has(node: AccessibilityNodeInfo?, label: String): Boolean {
        if (node == null) return false
        if (node.contentDescription?.toString() == label || node.text?.toString() == label) return true
        return (0 until node.childCount).any { has(node.getChild(it), label) }
    }

    private fun await(label: String) {
        val deadline = SystemClock.uptimeMillis() + 6000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync { }
            if (has(instrumentation.uiAutomation.rootInActiveWindow, label)) return
            SystemClock.sleep(50)
        }
        fail("Missing $label")
    }

    @Test fun artworkCoversAllRegionsGearAndMotionWithoutMutatingTheGame() {
        BattleArtwork.load(instrumentation.targetContext.resources)
        assertTrue(BattleArtwork.has("heroBack"))
        for (row in 0..4) for (family in listOf("slime", "knight", "bat", "spider", "golem", "wisp", "boss"))
            assertTrue(BattleArtwork.has("monster_${row}_$family"))
        for (weapon in WeaponCatalog.all) assertTrue(BattleArtwork.has("weapon_${weapon.id}"))
        for (gear in ArmorCatalog.all) assertTrue(BattleArtwork.has(if (gear.id == gear.kind.name.lowercase()) gear.id else "armor_${gear.id}"))
        for (floor in listOf(1, 11, 21, 31, 41)) {
            val e = GameEngine()
            e.enterFloor(floor); e.startBattle(false)
            e.state.hp = Equipment.stats(e.state).hp * .08
            val saved = SaveCodec.encode(e.state)
            for (height in listOf(200, 640)) {
                val bitmap = ImageBitmap(480, height)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(480f, height.toFloat())) {
                    drawScene(e.state, 176)
                }
                export("region-$floor-$height", bitmap.asAndroidBitmap())
            }
            assertEquals(saved, SaveCodec.encode(e.state))
        }
    }

    @Test fun warningAndBattleControlsSurviveCompactScreensAndClearAfterHealing() {
        for (compact in listOf(false, true)) {
            if (compact) ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("wm size 720x1280")).use { it.readBytes() }
            try {
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { a ->
                        val e = GameEngine(); e.enterFloor(1); e.startBattle(false)
                        val species = MonsterCatalog.forFloor(1).first { it.family == "wisp" }
                        e.state.enemy = e.state.enemy!!.copy(name = species.name, speciesId = species.id, opening = true)
                        e.state.hp = Equipment.stats(e.state).hp * .08
                        a.game.storyPlaybackEnabled = false; a.game.engine = e
                        a.startGame(); a.game.active = false; a.game.refresh()
                    }
                    await("체력 위험 경고"); await("일반 공격"); await("강공격")
                    SystemClock.sleep(250)
                    export("low-${if (compact) "compact" else "normal"}", requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
                    scenario.onActivity { a ->
                        a.game.engine.state.mode = Mode.COMBAT_DEFEND; a.game.refresh()
                    }
                    await("방어"); await("회피")
                    export("defense-${if (compact) "compact" else "normal"}", requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
                    scenario.onActivity { a ->
                        val s = a.game.engine.state; s.hp = Equipment.stats(s).hp * .5; a.game.refresh()
                    }
                    await("체력")
                    assertFalse(has(instrumentation.uiAutomation.rootInActiveWindow, "체력 위험 경고"))
                    export("healed-${if (compact) "compact" else "normal"}", requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
                }
            } finally {
                if (compact) ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("wm size reset")).use { it.readBytes() }
            }
        }
    }
}
