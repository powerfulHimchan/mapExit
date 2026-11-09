package com.powerfulhimchan.mapexit

import com.powerfulhimchan.mapexit.core.*

internal data class BattleVisual(
    val scene: State,
    val action: String,
    val startedAt: Long,
    val impact: BattleImpact,
    val selectedAt: Long = startedAt,
) {
    val duration get() = BattleAnimation.DURATION_MS * (impact.strikes.size.coerceAtLeast(1) + if(impact.counterHit!=null) 1 else 0)
    fun active(now: Long) = now < startedAt + duration
    fun frame(now: Long): BattleVisual {
        impact.counterHit?.let { hit ->
            if(now < startedAt+BattleAnimation.DURATION_MS) return copy(impact=impact.copy(counterHit=null))
            return copy(action="attack",startedAt=startedAt+BattleAnimation.DURATION_MS,
                impact=BattleImpact(serial=impact.serial*10+1,target=ImpactTarget.ENEMY,amount=hit.amount,
                    outcome=if(hit.damage>0) ImpactOutcome.DAMAGE else ImpactOutcome.MISS,
                    critical=hit.critical,luckyAttack=hit.luckyAttack,luckyCritical=hit.luckyCritical,tactic="자동 반격"))
        }
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
