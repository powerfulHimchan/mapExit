package com.powerfulhimchan.mapexit.core

/** Art keys share the existing hit clock; selecting a picture never advances a turn. */
object SpriteFrames {
    fun phase(elapsed: Long): Int = when {
        elapsed < 120 -> 0
        elapsed < 210 -> 1
        elapsed < BattleAnimation.HIT_MS -> 2
        elapsed < 430 -> 3
        elapsed < 620 -> 4
        else -> 5
    }

    fun hero(action: String?, kind: Kind?, elapsed: Long, clock: Long, damaged: Boolean = false, dead: Boolean = false): String {
        val age = elapsed - BattleAnimation.HIT_MS
        if (dead && age >= 0) return "death_${(age / 90).coerceIn(0, 3)}"
        if (damaged && age in 0..280) return "hit_${(age / 75).coerceAtMost(3)}"
        if (action == null || elapsed !in 0 until BattleAnimation.DURATION_MS)
            return "idle_${Math.floorMod(clock, 1200L) / 600}"
        val phase = phase(elapsed)
        return when (action) {
            "attack", "strong" -> "${action}_${(kind ?: Kind.SWORD).name.lowercase()}_$phase"
            "guard", "dodge" -> "${action}_$phase"
            else -> "idle_${Math.floorMod(clock, 1200L) / 600}"
        }
    }

    fun monster(attacking: Boolean, elapsed: Long, clock: Long, hit: Boolean = false, dying: Boolean = false, guarding: Boolean = false): String {
        val age = elapsed - BattleAnimation.HIT_MS
        if (dying && age >= 0) return "death_${(age / 90).coerceIn(0, 3)}"
        if (hit && age in 0..280) return "hit_${(age / 75).coerceAtMost(3)}"
        if (attacking && elapsed in 0 until BattleAnimation.DURATION_MS) return "attack_${phase(elapsed)}"
        val idle = Math.floorMod(clock, 1200L) / 600
        return if (guarding) "guard_$idle" else "idle_$idle"
    }
}
