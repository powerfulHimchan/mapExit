package com.powerfulhimchan.mapexit.core

import kotlin.math.*

/** Two fixed-length bones. The wrist is constrained before positioning a held item. */
object ArmKinematics {
    data class Point(val x: Float, val y: Float) {
        operator fun plus(p: Point) = Point(x + p.x, y + p.y)
        operator fun minus(p: Point) = Point(x - p.x, y - p.y)
        operator fun times(k: Float) = Point(x * k, y * k)
        fun length() = hypot(x, y)
    }
    data class Pose(val shoulder: Point, val elbow: Point, val wrist: Point)
    fun solve(shoulder: Point, target: Point, upper: Float, lower: Float, bend: Float = 1f): Pose {
        require(upper > 0 && lower > 0)
        val delta = target - shoulder
        val raw = delta.length()
        val direction = if (raw > .0001f) delta * (1 / raw) else Point(0f, 1f)
        val distance = raw.coerceIn(abs(upper - lower) + .001f, upper + lower - .001f)
        val wrist = shoulder + direction * distance
        val along = (upper * upper - lower * lower + distance * distance) / (2 * distance)
        val across = sqrt((upper * upper - along * along).coerceAtLeast(0f))
        // Perpendicular bend stays on the same side through the entire swing.
        val elbow = shoulder + direction * along + Point(-direction.y, direction.x) * (across * bend.sign)
        return Pose(shoulder, elbow, wrist)
    }

    /** Project a shared handle into both arms' reach; the support hand stays on that handle. */
    fun sharedTarget(right: Point, left: Point, target: Point, supportOffset: Point, reach: Float): Point {
        var p = target
        repeat(12) {
            for (origin in listOf(right, left - supportOffset)) {
                val d = p - origin
                val length = d.length()
                if (length > reach) p = origin + d * (reach / length)
            }
        }
        return p
    }
}
