package com.powerfulhimchan.mapexit.core

import kotlin.math.exp

/** Pure presentation values: never change HP, equipment, turns or save data. */
object HealthWarning {
    const val THRESHOLD = .10
    const val PERIOD_MS = 1100L

    fun active(hp: Double, maxHp: Double, floor: Int): Boolean =
        floor > 0 && hp.isFinite() && maxHp.isFinite() && maxHp > 0 && hp > 0 && hp <= maxHp * THRESHOLD

    /** Two soft beats per cycle, with a long quiet interval instead of flashing. */
    fun pulse(clock: Long, animate: Boolean = true): Float {
        if (!animate) return .45f
        val phase = Math.floorMod(clock, PERIOD_MS).toDouble() / PERIOD_MS
        fun beat(center: Double, width: Double) = exp(-((phase - center) / width).let { it * it })
        return (.14 + .66 * beat(.16, .075) + .38 * beat(.35, .085)).toFloat().coerceIn(0f, 1f)
    }
}
