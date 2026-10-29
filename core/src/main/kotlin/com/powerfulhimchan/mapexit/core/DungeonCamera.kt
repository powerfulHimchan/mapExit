package com.powerfulhimchan.mapexit.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToLong

/** Presentation only: the saved map and event resolution never wait for the camera. */
data class DungeonViewPose(val x: Float, val y: Float, val angle: Float)

class DungeonCamera {
    private var dungeon: Dungeon? = null
    private var target = DungeonViewPose(0f, 0f, 0f)
    private var path = listOf(target)
    private var startedAt = 0L
    private var duration = 0L
    private var length = 0f

    private fun turn(a: Float, b: Float): Float {
        var angle = b - a
        while (angle > PI) angle -= (2 * PI).toFloat()
        while (angle < -PI) angle += (2 * PI).toFloat()
        return angle
    }

    private fun weight(a: DungeonViewPose, b: DungeonViewPose): Float {
        val distance = hypot(b.x - a.x, b.y - a.y)
        return if (distance > .001f) distance
        else if (abs(turn(a.angle, b.angle)) > .001f) .4f else 0f
    }

    fun observe(state: State, now: Long, animate: Boolean = true) {
        val map = state.dungeon
        if (map == null) {
            dungeon = null
            duration = 0
            return
        }
        val next =
            DungeonViewPose(map.x + .5f, map.y + .5f, (-PI / 2 + map.direction * PI / 2).toFloat())
        if (
            !animate || map !== dungeon || abs(next.x - target.x) + abs(next.y - target.y) > 1.01f
        ) {
            dungeon = map
            target = next
            path = listOf(next)
            duration = 0
            return
        }
        if (next == target) return
        val (current, nextIndex) = position(now)
        // Retain unvisited cell centers when inputs arrive before the previous step ends.
        // This prevents a fast second turn from cutting diagonally through a corner wall.
        path = listOf(current) + path.drop(nextIndex) + next
        target = next
        length = path.zipWithNext().sumOf { (a, b) -> weight(a, b).toDouble() }.toFloat()
        startedAt = now
        duration = (length * 600).roundToLong().coerceIn(240L, 1200L)
    }

    private fun position(now: Long): Pair<DungeonViewPose, Int> {
        val t =
            if (duration == 0L) 1f else ((now - startedAt).toFloat() / duration).coerceIn(0f, 1f)
        if (t >= 1f) return target to path.size
        var distance = t * length
        for (i in 1 until path.size) {
            val a = path[i - 1]
            val b = path[i]
            val segment = weight(a, b)
            if (segment <= .00001f) continue
            if (distance <= segment || i == path.lastIndex) {
                val fraction = (distance / segment).coerceIn(0f, 1f)
                val eased = fraction * fraction * (3f - 2f * fraction)
                return DungeonViewPose(
                    a.x + (b.x - a.x) * fraction,
                    a.y + (b.y - a.y) * fraction,
                    a.angle + turn(a.angle, b.angle) * eased,
                ) to i
            }
            distance -= segment
        }
        return target to path.size
    }

    fun sample(now: Long): DungeonViewPose = position(now).first
}
