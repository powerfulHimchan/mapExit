package com.powerfulhimchan.mapexit.core

data class AttackLuck(val attackBoost: Boolean = false, val criticalBoost: Boolean = false)

object Luck {
    /** Helpful and harmful event rolls keep their original odds at zero luck. */
    fun good(base: Double, luck: Double, ceiling: Double = .95): Double =
        (base + (1-base) * .25 * luck.coerceAtLeast(0.0) / (luck.coerceAtLeast(0.0)+80)).coerceIn(base, maxOf(base,ceiling))
    fun bad(base: Double, luck: Double, floor: Double = .01): Double =
        (base / (1 + luck.coerceAtLeast(0.0)/100)).coerceIn(minOf(base,floor),base)
    fun doubleAttack(stats: Stats): Double = if (stats.agility < 40 || stats.luck <= 0) 0.0 else
        (.30 * (stats.agility-35)/(stats.agility+120) * stats.luck/(stats.luck+50)).coerceIn(0.0,.30)
    fun ambush(stats: Stats, precision: Double): Double =
        (.025 / (1 + (stats.agility-precision).coerceAtLeast(0.0)/40 + stats.luck/50)).coerceIn(.001,.03)
    fun activationChance(luck: Double): Double =
        (luck.coerceAtLeast(0.0) * GameBalance.model("luck_proc_per_point")).coerceAtMost(
            GameBalance.model("luck_proc_max")
        )

    /** Separate rolls for this hit only. Equipment and growth stats stay unchanged. */
    fun roll(s: State, stats: Stats): AttackLuck {
        val chance = activationChance(stats.luck)
        if (chance <= 0) return AttackLuck()
        val attack = s.random() < chance
        val critical = s.random() < chance && stats.crit < GameBalance.model("crit_chance_max")
        return AttackLuck(attack, critical)
    }

    fun attackMultiplier(roll: AttackLuck): Double =
        if (roll.attackBoost) 1 + GameBalance.model("luck_attack_bonus") else 1.0

    fun criticalChance(base: Double, roll: AttackLuck): Double =
        (base + if (roll.criticalBoost) GameBalance.model("luck_crit_bonus") else 0.0).coerceIn(
            GameBalance.model("crit_chance_min"),
            GameBalance.model("crit_chance_max"),
        )
}
