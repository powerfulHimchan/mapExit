package com.powerfulhimchan.mapexit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

/** Validates production resources and captures the actual Activity, not a concept mockup. */
class Graphics052Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun load() = MotionArtwork.load(context.resources)
    private fun decode(id: Int) = requireNotNull(BitmapFactory.decodeResource(context.resources, id,
        BitmapFactory.Options().apply { inScaled = false }))
    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    private fun export(name: String, bitmap: Bitmap) {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "mapexit-review052-$name.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        requireNotNull(resolver.openOutputStream(uri)).use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }
    private fun has(node: AccessibilityNodeInfo?, label: String): Boolean {
        if (node == null) return false
        if (node.contentDescription?.toString()?.contains(label) == true || node.text?.toString()?.contains(label) == true) return true
        return (0 until node.childCount).any { has(node.getChild(it), label) }
    }
    private fun await(label: String) {
        val end = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < end) {
            instrumentation.runOnMainSync { }
            if (has(instrumentation.uiAutomation.rootInActiveWindow, label)) return
            SystemClock.sleep(30)
        }
        error("Missing $label")
    }
    @Test fun individualNumeralsAndEveryBattleWordHaveTransparentGutters() {
        load()
        for ((id, bounds) in listOf(R.drawable.battle_digits_052 to FeedbackAtlas052.digits,
            R.drawable.battle_words_052 to FeedbackAtlas052.labels)) {
            val bitmap = decode(id)
            for (b in bounds) {
                assertTrue(b[2] > 10 && b[3] > 20)
                var ink = 0
                for (y in b[1] until b[1] + b[3]) for (x in b[0] until b[0] + b[2])
                    if (bitmap.getPixel(x,y) ushr 24 > 96) ink++
                assertTrue("Every glyph needs visible ink", ink > 100)
                assertEquals("No background panel between glyphs", 0, bitmap.getPixel(b[0] - 1, b[1]) ushr 24)
            }
            bitmap.recycle()
        }
        val labels = FeedbackArtwork052.labels(BattleImpact(1, ImpactTarget.PLAYER, 37, ImpactOutcome.GUARD,
            critical = true, luckyAttack = true, luckyCritical = true, tactic = "빈틈 발생 · 다음 공격 강화"))
        assertEquals(listOf("방어", "치명타!", "빈틈 발생", "행운"), labels)
        for (outcome in listOf(ImpactOutcome.BLOCK, ImpactOutcome.DODGE, ImpactOutcome.MISS))
            assertTrue(FeedbackArtwork052.labels(BattleImpact(1, ImpactTarget.PLAYER, 0, outcome)).isNotEmpty())
        val image = ImageBitmap(320, 80)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(320f, 80f)) {
            with(FeedbackArtwork052) { numbers(BattleImpact(1, ImpactTarget.ENEMY, Int.MAX_VALUE, ImpactOutcome.DAMAGE, critical = true)) }
        }
        val rendered = image.asAndroidBitmap()
        assertTrue(pixels(rendered).count { it ushr 24 > 96 } > 1000)
        assertEquals("Floating damage must not have a rectangular panel", 0, rendered.getPixel(0, 0) ushr 24)
        export("maximum-damage-transparent", rendered)
    }
    @Test fun explorationUsesTheExactCombatMonsterCropAndDistinctTrapScenes() {
        load()
        for (monster in MotionAtlas.monsters) {
            val actual = requireNotNull(ExplorationArtwork052.all[monster])
            val expected = requireNotNull(MotionArtwork.explorationMonster(monster))
            assertTrue("Exploration must reuse combat art for $monster", actual.sameAs(expected))
            assertTrue(actual.width > 30 && actual.height > 30)
            expected.recycle()
        }
        val hashes = (0..4).map { pixels(ExplorationArtwork052.all.getValue("trap_$it")).contentHashCode() }
        assertEquals(5, hashes.toSet().size)
        assertFalse(ExplorationArtwork052.all.getValue("heal").sameAs(ExplorationArtwork052.all.getValue("heal_used")))
    }
    private fun corridor(event: Event, type: Int = 0): GameEngine = GameEngine().also { e ->
        e.enterFloor(1)
        val map = Dungeon(9, 9, MutableList(81) { Cell() }, 4, 6, 0)
        for (y in 1..7) map.cell(4, y).wall = false
        map.cell(4, 5).apply { this.event = event; trapType = type; monsterId = MonsterCatalog.forFloor(1).first { it.family == "golem" }.id }
        e.state.dungeon = map; e.state.mode = Mode.EXPLORE; e.state.enemy = null
    }
    @Test fun trapTypeInvalidatesRasterAndWallsStillHideDetailedBillboards() {
        load()
        val e = corridor(Event.TRAP); val map = e.state.dungeon!!
        val camera = DungeonViewPose(4.5f, 6.5f, (-Math.PI / 2).toFloat())
        val raster = DungeonRaster(480, 640)
        val hashes = (0..4).map { type ->
            map.cell(4,5).trapType = type
            val bitmap = raster.render(map, camera).asAndroidBitmap()
            export("dungeon-trap-$type", bitmap)
            pixels(bitmap).contentHashCode()
        }
        assertEquals("Changing trap type on the same map must refresh the cached image", 5, hashes.toSet().size)
        for (event in listOf(Event.MONSTER, Event.HEAL)) {
            map.cell(4,5).event = event
            export("dungeon-${event.name.lowercase()}", raster.render(map,camera).asAndroidBitmap())
        }
        map.cell(4,5).wall = true
        val hidden = pixels(raster.render(map, camera).asAndroidBitmap())
        map.cell(4,5).event = Event.NONE
        assertArrayEquals(hidden, pixels(raster.render(map,camera).asAndroidBitmap()))
    }
    @Test fun preparationTravelsMoreThanTwiceTheOldRangeWithoutTeleporting() {
        load()
        for (kind in listOf(Kind.SWORD, Kind.SPEAR, Kind.HAMMER, Kind.CLAW, Kind.WHIP)) {
            val poses = (0L..16000L step 40).map { CampMotion056.sample(it,kind) }
            assertEquals(-.24f, poses.minOf { it.travel }, .001f)
            assertEquals(.24f, poses.maxOf { it.travel }, .001f)
            assertTrue(poses.zipWithNext().all { (a,b) -> kotlin.math.abs(a.travel-b.travel) < .013f })
            assertEquals(poses.first(), poses.last())
        }
        val e = GameEngine()
        val saved = SaveCodec.encode(e.state)
        for (time in listOf(3800L, 6800L, 10000L)) {
            val image = ImageBitmap(640, 400)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(640f, 400f)) {
                drawScene(e.state, time)
            }
            export("camp-$time", image.asAndroidBitmap())
        }
        assertEquals(saved, SaveCodec.encode(e.state))
    }
    @Test fun selectedStaircaseIconIsPackagedAsTheLauncherIcon() {
        assertTrue(BuildConfig.VERSION_CODE >= 52)
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
        val bitmap = decode(R.drawable.launcher_stairs_052)
        assertEquals(512, bitmap.width); assertEquals(512, bitmap.height)
        export("launcher-icon", bitmap)
    }
    @Test fun runningActivityShowsImageOnlyBattleFeedbackAndDetailedExploration() {
        val impacts = listOf(
            "normal" to BattleImpact(1,ImpactTarget.ENEMY,128,ImpactOutcome.DAMAGE),
            "critical" to BattleImpact(2,ImpactTarget.ENEMY,256,ImpactOutcome.DAMAGE,critical=true),
            "received" to BattleImpact(3,ImpactTarget.PLAYER,37,ImpactOutcome.DAMAGE),
            "guard" to BattleImpact(4,ImpactTarget.PLAYER,12,ImpactOutcome.GUARD),
            "block" to BattleImpact(5,ImpactTarget.PLAYER,0,ImpactOutcome.BLOCK),
            "dodge" to BattleImpact(6,ImpactTarget.PLAYER,0,ImpactOutcome.DODGE),
            "miss" to BattleImpact(7,ImpactTarget.ENEMY,0,ImpactOutcome.MISS),
            "tactic" to BattleImpact(8,ImpactTarget.ENEMY,115,ImpactOutcome.DAMAGE,tactic="방패 파괴 · 연속 공격 · 2회",luckyAttack=true),
        )
        launchGame().use { scenario ->
            scenario.onGameActivity { a ->
                val e = GameEngine(); e.enterFloor(1); e.startBattle(false)
                a.game.engine = e; a.game.active = false; a.game.storyPlaybackEnabled = false; a.game.refresh()
            }
            await("몬스터 HP"); SystemClock.sleep(450)
            for ((name, impact) in impacts) {
                scenario.onGameActivity { a ->
                    a.game.engine.state.lastImpact = impact
                    a.game.engine.state.impactSequence = impact.serial
                    a.game.refresh()
                }
                await("피해 ${impact.label()}"); SystemClock.sleep(120)
                export("phone-$name", requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
            }
            for (event in listOf(Event.MONSTER, Event.HEAL, Event.TRAP)) {
                scenario.onGameActivity { a -> a.game.engine = corridor(event); a.game.refresh() }
                await("메인 장면"); SystemClock.sleep(300)
                export("phone-explore-${event.name.lowercase()}", requireNotNull(instrumentation.uiAutomation.takeScreenshot()))
            }
        }
    }
}
