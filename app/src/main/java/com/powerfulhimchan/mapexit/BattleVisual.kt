package com.powerfulhimchan.mapexit

import com.powerfulhimchan.mapexit.core.*

internal data class BattleVisual(
    val scene: State,
    val action: String,
    val startedAt: Long,
    val impact: BattleImpact,
) {
    fun active(now: Long) = now < startedAt + BattleAnimation.DURATION_MS
}

internal fun State.battleSnapshot() =
    copy(
        inventory = inventory.toMutableList(),
        equipment = equipment.toMutableMap(),
        enemy = enemy?.copy(),
    )
