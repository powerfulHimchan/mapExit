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

    fun description(stat: Stat): String = when (stat) {
        Stat.HP -> "버틸 수 있는 최대 체력입니다. 성장 시 최대 체력과 현재 체력이 함께 25 증가합니다."
        Stat.ATTACK -> "공격 명중 시 주는 피해의 기준입니다. 몬스터 방어력과 공격 방식에 따라 실제 피해가 달라집니다."
        Stat.DEFENSE -> "받는 피해와 완전 방어 확률에 영향을 줍니다. 같은 방어력이라도 약한 공격을 더 큰 비율로 줄입니다."
        Stat.AGILITY -> "몬스터의 정밀도와 비교해 회피 성공률과 도주 성공률을 높입니다."
        Stat.CRIT -> "명중한 공격이 치명타가 될 확률입니다. 일반 공격과 강공격의 명중률과는 별개입니다."
        Stat.CRIT_DAMAGE -> "치명타일 때 적용되는 피해 배율입니다. 150%라면 일반 피해의 1.5배를 줍니다."
        Stat.LUCK -> "공격력·치명타 확률의 일시 강화 발동 확률, 완전 방어와 전리품 획득 확률을 높입니다. 매번 발동하지는 않습니다."
    }

    /** Includes equipped percentage bonuses and caps without spending points or changing HP. */
    fun preview(state: State, stat: Stat): Stats =
        Equipment.stats(state.copy(base = state.base.add(stat, amount(stat))))
}
