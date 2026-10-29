package com.powerfulhimchan.mapexit.core

object Growth {
    fun amount(stat: Stat): Double =
        when (stat) {
            Stat.HP -> 25.0
            Stat.ATTACK -> 6.0
            Stat.DEFENSE -> 5.0
            Stat.AGILITY -> 3.0
            Stat.CRIT -> .015
            Stat.CRIT_DAMAGE -> .08
            Stat.LUCK -> 3.0
        }

    /** Includes equipped percentage bonuses and caps without spending points or changing HP. */
    fun preview(state: State, stat: Stat): Stats =
        Equipment.stats(state.copy(base = state.base.add(stat, amount(stat))))
}
