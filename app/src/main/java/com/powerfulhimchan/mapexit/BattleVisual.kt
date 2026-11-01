package com.powerfulhimchan.mapexit

import com.powerfulhimchan.mapexit.core.*

internal data class BattleVisual(
    val scene: State,
    val action: String,
    val startedAt: Long,
    val impact: BattleImpact,
) {
    val duration get() = BattleAnimation.DURATION_MS * impact.strikes.size.coerceAtLeast(1)
    fun active(now: Long) = now < startedAt + duration
    fun frame(now: Long): BattleVisual {
        if (impact.strikes.isEmpty()) return this
        val index=((now-startedAt).coerceAtLeast(0)/BattleAnimation.DURATION_MS).toInt().coerceAtMost(impact.strikes.lastIndex)
        val hit=impact.strikes[index]
        val enemy=scene.enemy?.let { it.copy(hp=(it.hp-impact.strikes.take(index).sumOf { hit -> hit.damage }).coerceAtLeast(0.0)) }
        return copy(scene=scene.copy(enemy=enemy),startedAt=startedAt+index*BattleAnimation.DURATION_MS,
            impact=impact.copy(serial=impact.serial*10+index,amount=hit.amount,critical=hit.critical,
                luckyAttack=hit.luckyAttack,luckyCritical=hit.luckyCritical,
                outcome=if(hit.damage>0) ImpactOutcome.DAMAGE else ImpactOutcome.MISS,
                tactic="연속 공격 · ${index+1}/2",strikes=emptyList()))
    }
}

internal fun State.battleSnapshot() =
    copy(
        inventory = inventory.toMutableList(),
        equipment = equipment.toMutableMap(),
        enemy = enemy?.copy(),
    )
