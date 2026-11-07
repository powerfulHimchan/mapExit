package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
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

class Motion044Test {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val times = listOf(0L, 120L, 240L, 320L, 480L, 700L)
    private fun export(name: String, bitmap: Bitmap) {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "mapexit-motion044-$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }
    private fun render(s: State, action: String, ms: Long, height: Int, player: Boolean = false, death: Boolean = false, playerDeath: Boolean = false): Bitmap {
        val before = SaveCodec.encode(s)
        val scene = s.battleSnapshot()
        val impact = BattleImpact(44, if (player) ImpactTarget.PLAYER else ImpactTarget.ENEMY, 17, if (action == "dodge") ImpactOutcome.DODGE else if (player) ImpactOutcome.GUARD else ImpactOutcome.DAMAGE)
        val visual = BattleVisual(scene, action, 1000, impact)
        val shown = if (death) s.copy(mode = Mode.VICTORY) else if (playerDeath) s.copy(hp = 0.0) else s
        val bitmap = ImageBitmap(480, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(480f, height.toFloat())) { drawScene(shown, 1000 + ms, battle = visual) }
        assertEquals("Rendering must not change combat/save state", before, SaveCodec.encode(s))
        return bitmap.asAndroidBitmap()
    }

    @Test fun allAuthoredCellsAreVisibleAndFitMobileTextureLimits() {
        MotionArtwork.load(context.resources)
        assertTrue(MotionArtwork.loaded)
        for ((id, columns, count) in listOf(Triple(R.drawable.combat_hero_motion, 12, MotionAtlas.heroPoses.size), Triple(R.drawable.combat_monster_motion, 24, MotionAtlas.monsters.size * MotionAtlas.monsterPoses.size), Triple(R.drawable.combat_equipment_motion, 10, MotionAtlas.gear.size))) {
            val atlas = requireNotNull(BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inScaled = false }))
            assertTrue(atlas.width <= 4096 && atlas.height <= 4096)
            for (i in 0 until count) {
                val pixels = IntArray(64 * 96)
                atlas.getPixels(pixels, 0, 64, i % columns * 64, i / columns * 96, 64, 96)
                assertTrue("Empty authored frame $id/$i", pixels.count { it ushr 24 > 0 } > 32)
            }
        }
        for (pose in MotionAtlas.heroPoses) {
            assertTrue(MotionArtwork.has("heroBack", pose))
            val shoulder = MotionArtwork.shoulder(pose)
            assertTrue(shoulder.x in 0f..32f && shoulder.y in 0f..48f)
        }
    }

    @Test fun weaponDefenseAndEquipmentLayersKeepTheirDetailAcrossTheWholeSwing() {
        MotionArtwork.load(context.resources)
        for (kind in listOf(Kind.SWORD, Kind.SPEAR, Kind.HAMMER, Kind.CLAW, Kind.WHIP)) {
            val e = GameEngine(); e.enterFloor(1)
            val weapon = ItemFactory.create(e.state, kind)
            e.state.inventory.add(weapon); Equipment.equip(e.state, weapon.id, Slot.RIGHT); e.startBattle(false)
            for (action in listOf("attack", "strong")) {
                val fingerprints = mutableSetOf<Int>()
                for ((index, ms) in times.withIndex()) {
                    val bitmap = render(e.state, action, ms, 640)
                    val pixels = IntArray(480 * 640); bitmap.getPixels(pixels, 0, 480, 0, 0, 480, 640)
                    fingerprints.add(pixels.contentHashCode())
                    if (action == "attack" || kind == Kind.HAMMER) export("${kind.name.lowercase()}-$action-$index", bitmap)
                }
                assertEquals("Every stage must show its own rendered pose", 6, fingerprints.size)
                render(e.state, action, 320, 200)
            }
            if (kind == Kind.SWORD) {
                for (action in listOf("guard", "dodge")) for ((index, ms) in times.withIndex())
                    export("$action-$index", render(e.state, action, ms, 640, player = true))
                val equipped = e.state.inventory.first { it.id == e.state.equipment[Slot.RIGHT] }
                val first = render(e.state, "attack", 320, 640)
                e.state.inventory[e.state.inventory.indexOf(equipped)] = equipped.copy(tier = if (equipped.tier == 7) 1 else 7)
                assertFalse("Equipped tier colour must affect the held weapon", first.sameAs(render(e.state, "attack", 320, 640)))
                for ((armorKind, slot) in listOf(Kind.HEAD to Slot.HEAD, Kind.BODY to Slot.BODY, Kind.PANTS to Slot.PANTS, Kind.BOOTS to Slot.BOOTS)) {
                    val item = ItemFactory.create(e.state, armorKind)
                    e.state.inventory.add(item); Equipment.equip(e.state, item.id, slot)
                    assertTrue(MotionArtwork.has(gearSprite(item)))
                }
                for (action in listOf("guard", "dodge")) for ((index, ms) in times.withIndex())
                    export("armor-$action-$index", render(e.state, action, ms, 640, player = true))
                for ((index, ms) in listOf(320L, 410L, 500L, 590L).withIndex()) {
                    export("player-hit-$index", render(e.state, "guard", ms, 640, player = true))
                    export("player-death-$index", render(e.state, "guard", ms, 640, player = true, playerDeath = true))
                }
            }
        }
    }

    @Test fun everyRegionalMonsterRetainsItsIdentityDuringAttackImpactAndDeath() {
        MotionArtwork.load(context.resources)
        for (region in 0..4) {
            val floor = region * 10 + 1
            for (species in MonsterCatalog.forFloor(floor) + MonsterCatalog.special(floor, true)) {
                val e = GameEngine(); e.enterFloor(floor); e.startBattle(false)
                e.state.enemy = e.state.enemy!!.copy(name = species.name, speciesId = species.id, boss = species.family == "boss")
                for (pose in MotionAtlas.monsterPoses) assertTrue("${species.sprite} $pose", MotionArtwork.has(species.sprite, pose))
                for ((index, ms) in listOf(120L, 320L, 600L).withIndex()) {
                    val bitmap = render(e.state, "guard", ms, 640, player = true)
                    if (region == 0) export("monster-${species.family}-$index", bitmap)
                    render(e.state, "attack", ms, 200, death = true)
                }
            }
        }
    }
}
