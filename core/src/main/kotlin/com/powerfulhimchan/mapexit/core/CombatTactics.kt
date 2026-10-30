package com.powerfulhimchan.mapexit.core

/** A saved, visible plan. Choosing an action or reopening a save never rerolls it. */
object CombatTactics {
    fun pattern(enemy: Enemy): List<EnemyIntent> =
        when {
            enemy.boss ->
                listOf(
                    EnemyIntent.GUARD,
                    EnemyIntent.FLURRY,
                    EnemyIntent.HEAVY,
                    EnemyIntent.STRIKE,
                    EnemyIntent.RECOVER,
                )
            enemy.name.contains("기사") ->
                listOf(EnemyIntent.GUARD, EnemyIntent.HEAVY, EnemyIntent.FLURRY)
            enemy.name.contains("박쥐") ->
                listOf(EnemyIntent.FLURRY, EnemyIntent.HEAVY, EnemyIntent.RECOVER)
            enemy.name.contains("슬라임") ->
                listOf(EnemyIntent.STRIKE, EnemyIntent.HEAVY, EnemyIntent.RECOVER)
            else -> listOf(EnemyIntent.STRIKE, EnemyIntent.HEAVY, EnemyIntent.FLURRY)
        }

    fun advance(enemy: Enemy) {
        enemy.round = (enemy.round + 1) % pattern(enemy).size
        enemy.intent = pattern(enemy)[enemy.round]
    }

    fun cue(enemy: Enemy): String =
        when (enemy.intent) {
            EnemyIntent.STRIKE -> "공격 자세를 잡습니다"
            EnemyIntent.GUARD -> "방패를 세웁니다"
            EnemyIntent.HEAVY ->
                when {
                    enemy.boss -> "망치를 높이 들어 올립니다"
                    enemy.name.contains("기사") -> "검을 높이 들어 올립니다"
                    enemy.name.contains("박쥐") -> "몸을 낮추고 급강하를 준비합니다"
                    else -> "몸을 부풀려 도약을 준비합니다"
                }
            EnemyIntent.FLURRY ->
                if (enemy.name.contains("박쥐")) "빠른 연속 돌진을 준비합니다" else "짧은 연속 공격을 준비합니다"
            EnemyIntent.RECOVER -> "지쳐서 숨을 고릅니다"
        }

    fun hint(enemy: Enemy): String =
        when (enemy.intent) {
            EnemyIntent.STRIKE -> "방어로 피해 감소 · 회피로 피해 없이"
            EnemyIntent.HEAVY -> "회피하기 쉬운 큰 공격 · 회피 성공 시 빈틈"
            EnemyIntent.FLURRY -> "방어가 유리한 연속 공격 · 방어하면 빈틈"
            EnemyIntent.GUARD -> "일반 공격 피해 감소 · 강공격 명중 시 방패 파괴"
            EnemyIntent.RECOVER -> "공격 기회 · 이번에는 반격하지 않습니다"
        }

    fun incomingMultiplier(intent: EnemyIntent): Double =
        when (intent) {
            EnemyIntent.HEAVY -> 1.5
            EnemyIntent.FLURRY -> 1.1
            EnemyIntent.GUARD -> .8
            EnemyIntent.RECOVER -> 0.0
            else -> 1.0
        }
}
