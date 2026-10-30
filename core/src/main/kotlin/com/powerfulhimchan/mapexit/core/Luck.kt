package com.powerfulhimchan.mapexit.core

data class AttackLuck(val attackBoost: Boolean = false, val criticalBoost: Boolean = false)

object Luck {
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
