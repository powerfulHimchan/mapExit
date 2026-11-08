package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.powerfulhimchan.mapexit.core.*
import org.junit.Assert.*
import org.junit.Test

/** Capture the production UI and a real attack, rather than a composited concept mockup. */
class Refined045UiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun find(node: AccessibilityNodeInfo?, label: String): Rect? {
        if (node == null) return null
        if (node.text?.toString() == label || node.contentDescription?.toString() == label)
            return Rect().also { node.getBoundsInScreen(it) }
        for (i in 0 until node.childCount) find(node.getChild(i), label)?.let { return it }
        return null
    }
    private fun await(label: String): Rect {
        val end = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < end) {
            instrumentation.runOnMainSync { }
            find(instrumentation.uiAutomation.rootInActiveWindow, label)?.let { return it }
            SystemClock.sleep(40)
        }
        error("Missing production control: $label")
    }
    private fun capture(name: String) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val resolver = instrumentation.targetContext.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "mapexit-refined045-$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }
    @Test fun referenceStyleRemainsDuringRealWeaponSwingOnBothScreenSizes() {
        for (compact in listOf(false, true)) {
            if (compact) ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("wm size 720x1280")).use { it.readBytes() }
            try {
                launchGame().use { scenario ->
                    scenario.onGameActivity { a ->
                        val e = GameEngine(); e.enterFloor(1)
                        val sword = ItemFactory.create(e.state, Kind.SWORD, variantId = "longsword").copy(tier = 1)
                        val shield = ItemFactory.create(e.state, Kind.SHIELD, variantId = "shield").copy(tier = 1)
                        e.state.inventory.addAll(listOf(sword, shield))
                        Equipment.equip(e.state, sword.id, Slot.RIGHT)
                        Equipment.equip(e.state, shield.id, Slot.LEFT)
                        e.startBattle(false)
                        val species = MonsterCatalog.forFloor(1).first { it.family == "wisp" }
                        e.state.enemy = e.state.enemy!!.copy(name = species.name, speciesId = species.id, hp = 326.0, maxHp = 326.0)
                        e.state.message = "${species.name} 조우"
                        e.state.hp = Equipment.stats(e.state).hp * .08
                        a.game.engine = e; a.game.active = false; a.game.refresh()
                    }
                    val attack = await("일반 공격"); await("강공격"); await("체력 위험 경고")
                    val suffix = if (compact) "compact" else "normal"
                    capture("idle-$suffix")
                    scenario.onGameActivity { it.game.active = true }
                    val down = SystemClock.uptimeMillis()
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, attack.exactCenterX(), attack.exactCenterY(), 0)
                        instrumentation.sendPointerSync(event); event.recycle()
                    }
                    SystemClock.sleep(100); capture("windup-$suffix")
                    SystemClock.sleep(220); capture("strike-$suffix")
                    SystemClock.sleep(340); capture("recovery-$suffix")
                    scenario.onGameActivity { a ->
                        assertNotNull(a.game.engine.state.enemy)
                        assertEquals(swordKind(a.game.engine.state), Kind.SWORD)
                    }
                }
            } finally {
                if (compact) ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("wm size reset")).use { it.readBytes() }
            }
        }
    }
    private fun swordKind(s: State) = Equipment.equipped(s).first { it.id == s.equipment[Slot.RIGHT] }.kind
}
