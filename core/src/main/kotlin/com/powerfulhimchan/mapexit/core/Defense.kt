package com.powerfulhimchan.mapexit.core

object Defense {
    /** Diminishing returns: defense and luck both help, with a hard 24% ceiling. */
    fun zeroDamageChance(stats: Stats): Double {
        val defense = stats.defense.coerceAtLeast(0.0)
        val luck = stats.luck.coerceAtLeast(0.0)
        return (.02 + .12 * defense / (defense + 200) + .10 * luck / (luck + 80)).coerceIn(.02, .24)
    }
}
