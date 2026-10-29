package com.powerfulhimchan.mapexit.core

object Defense {
    /** Armor absorbs a larger fraction of weak attacks and a smaller fraction of strong ones. */
    fun damageAfterArmor(attack: Double, defense: Double): Double {
        val incoming = attack.coerceAtLeast(0.0)
        if (incoming == 0.0) return 0.0
        return incoming * (incoming / (incoming + defense.coerceAtLeast(0.0)))
    }

    /** Diminishing returns: defense and luck both help, with a hard 24% ceiling. */
    fun zeroDamageChance(stats: Stats, incomingAttack: Double = 100.0): Double {
        val defense = stats.defense.coerceAtLeast(0.0)
        val luck = stats.luck.coerceAtLeast(0.0)
        return (.02 +
                .12 * defense / (defense + incomingAttack.coerceAtLeast(1.0) * 2) +
                .10 * luck / (luck + 80))
            .coerceIn(.02, .24)
    }
}
