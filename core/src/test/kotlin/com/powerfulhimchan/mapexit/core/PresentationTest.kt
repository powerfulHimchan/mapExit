package com.powerfulhimchan.mapexit.core

import kotlin.math.PI
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class PresentationTest {
    private fun state(): State =
        State(
            bootstrapped = true,
            floor = 1,
            dungeon =
                Dungeon(9, 11, MutableList(99) { Cell(wall = false) }, x = 4, y = 8, direction = 0),
            mode = Mode.EXPLORE,
        )

    @Test
    fun cameraMovesContinuouslyBetweenCentersWithoutChangingGameState() {
        val s = state()
        val camera = DungeonCamera()
        camera.observe(s, 1000)
        s.dungeon!!.y--
        val saved = SaveCodec.encode(s)
        camera.observe(s, 1000)
        assertEquals(8.5f, camera.sample(1000).y, .001f)
        assertEquals(8f, camera.sample(1300).y, .001f)
        assertEquals(7.5f, camera.sample(1600).y, .001f)
        assertEquals(saved, SaveCodec.encode(s))
    }

    @Test
    fun repeatedObservationsDoNotRestartTheStepAndNextStepHasNoJump() {
        val s = state()
        val camera = DungeonCamera()
        camera.observe(s, 1000)
        s.dungeon!!.y--
        camera.observe(s, 1000)
        camera.observe(s, 1300)
        assertEquals(8f, camera.sample(1300).y, .001f)
        val end = camera.sample(1600)
        s.dungeon!!.y--
        camera.observe(s, 1600)
        assertEquals(end.y, camera.sample(1600).y, .001f)
        assertEquals(6.5f, camera.sample(2200).y, .001f)
    }

    @Test
    fun backwardStepKeepsFacingAndTurnsUseTheShortestArc() {
        val s = state()
        val camera = DungeonCamera()
        camera.observe(s, 0)
        s.dungeon!!.y++
        camera.observe(s, 0)
        assertEquals(9f, camera.sample(300).y, .001f)
        assertEquals((-PI / 2).toFloat(), camera.sample(300).angle, .001f)
        camera.observe(s, 600)
        val before = camera.sample(600)
        s.dungeon!!.direction = 3
        camera.observe(s, 600)
        val start = camera.sample(600)
        val end = camera.sample(840)
        val arc = end.angle - start.angle
        assertEquals(
            (PI / 2).toFloat(),
            abs(kotlin.math.atan2(kotlin.math.sin(arc), kotlin.math.cos(arc))),
            .001f,
        )
        assertEquals(before.x, end.x, .001f)
        assertEquals(before.y, end.y, .001f)
    }

    @Test
    fun floorChangesTeleportsAndPauseSnapInsteadOfFlyingThroughWalls() {
        val s = state()
        val camera = DungeonCamera()
        camera.observe(s, 0)
        s.dungeon!!.y--
        camera.observe(s, 0)
        camera.observe(s, 100, animate = false)
        assertEquals(7.5f, camera.sample(100).y, .001f)
        s.dungeon!!.y = 1
        camera.observe(s, 200)
        assertEquals(1.5f, camera.sample(200).y, .001f)
        s.dungeon = s.dungeon!!.copy(x = 1, y = 4)
        camera.observe(s, 300)
        assertEquals(1.5f, camera.sample(300).x, .001f)
        assertEquals(4.5f, camera.sample(300).y, .001f)
    }

    @Test
    fun combinedTurnAndTranslationStaysInsideTheTraversedCellCenters() {
        val s = state()
        val camera = DungeonCamera()
        camera.observe(s, 0)
        s.dungeon!!.x++
        s.dungeon!!.direction = 1
        camera.observe(s, 0)
        for (time in 0L..600L step 10) {
            val pose = camera.sample(time)
            assertTrue(pose.x in 4.5f..5.5f)
            assertEquals(8.5f, pose.y, .001f)
            assertTrue(pose.angle.isFinite())
        }
        assertEquals(0f, camera.sample(600).angle, .001f)
    }

    @Test
    fun fastCornerInputsKeepTheIntermediateCenterInsteadOfCuttingAcrossWalls() {
        val s = state()
        val camera = DungeonCamera()
        camera.observe(s, 0)
        s.dungeon!!.y--
        camera.observe(s, 0)
        s.dungeon!!.x++
        s.dungeon!!.direction = 1
        camera.observe(s, 180)
        for (time in 180L..1200L step 10) {
            val pose = camera.sample(time)
            assertTrue(abs(pose.x - 4.5f) < .001f || abs(pose.y - 7.5f) < .001f)
        }
        assertEquals(5.5f, camera.sample(2000).x, .001f)
        assertEquals(7.5f, camera.sample(2000).y, .001f)
    }

    @Test
    fun everyGrowthPreviewMatchesActualSpendingWithPercentageEquipment() {
        for (stat in Stat.entries) {
            val e = GameEngine(State(points = 1))
            val ring =
                Gear(
                    e.state.nextId++,
                    Kind.RING,
                    "백분율 반지",
                    percent = Stats(.1, .2, .3, .2, .1, .1, .2),
                )
            e.state.inventory.add(ring)
            Equipment.equip(e.state, ring.id, Slot.RING1)
            val saved = SaveCodec.encode(e.state)
            val preview = Growth.preview(e.state, stat)
            assertEquals(saved, SaveCodec.encode(e.state))
            e.spendPoint(stat)
            assertEquals(preview, e.stats())
            assertEquals(0, e.state.points)
        }
    }

    @Test
    fun previewRespectsCritCapAndNoPointsCannotChangeStats() {
        val e = GameEngine(State(points = 0, base = Stats(crit = 100.0)))
        assertEquals(e.stats().crit, Growth.preview(e.state, Stat.CRIT).crit, .001)
        val before = e.stats()
        e.spendPoint(Stat.ATTACK)
        assertEquals(before, e.stats())
    }
}
