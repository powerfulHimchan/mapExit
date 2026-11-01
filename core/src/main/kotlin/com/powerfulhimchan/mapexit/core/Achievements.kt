package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.Serializable

@Serializable
data class AchievementProgress(
    var initialized: Boolean = false,
    var chests: Int = 0,
    var traps: Int = 0,
    var quests: Int = 0,
    var donations: Int = 0,
    var clutchWins: Int = 0,
    val bossFloors: MutableSet<Int> = mutableSetOf(),
    val finalBossCycles: MutableSet<Int> = mutableSetOf(),
    val unlocked: MutableSet<String> = mutableSetOf(),
    val claimed: MutableSet<String> = mutableSetOf(),
    val notices: MutableList<String> = mutableListOf(),
    var selectedTitle: String? = null,
)

data class Achievement(
    val id: String,
    val name: String,
    val condition: String,
    val target: Int,
    val metric: String,
    val gold: Int = 0,
    val potions: Int = 0,
    val greaterPotions: Int = 0,
    val points: Int = 0,
    val title: String? = null,
    val specialGear: Boolean = false,
) {
    val rewardText: String get() = buildList {
        if (gold > 0) add("${gold}G")
        if (potions > 0) add("회복약 ${potions}개")
        if (greaterPotions > 0) add("상급 회복약 ${greaterPotions}개")
        if (points > 0) add("성장 포인트 ${points}P")
        if (title != null) add("칭호 ‘$title’")
        if (specialGear) add("특별 반지 ‘귀환자의 인장’")
    }.joinToString(" · ")
}

/** Save-wide milestones. Only confirmed normal-play events may change progress. */
object Achievements {
    val all = listOf(
        Achievement("first-victory", "첫 승리", "몬스터 첫 처치", 1, "kills", gold = 30),
        Achievement("hunter-50", "익숙한 사냥꾼", "몬스터 누적 50마리 처치", 50, "kills", greaterPotions = 1),
        Achievement("hunter-200", "숙련된 사냥꾼", "몬스터 누적 200마리 처치", 200, "kills", points = 2, title = "숙련된 사냥꾼"),
        Achievement("discover-10", "관찰의 시작", "몬스터 10종 발견", 10, "species", gold = 100),
        Achievement("discover-30", "도감 연구가", "몬스터 30종 발견", 30, "species", points = 2, title = "도감 연구가"),
        Achievement("chests-20", "보물 사냥꾼", "상자 20개 개봉 (상자 몬스터 포함)", 20, "chests", greaterPotions = 2),
        Achievement("traps-20", "함정 전문가", "함정 회피 20회 성공", 20, "traps", points = 1, title = "함정 전문가"),
        Achievement("quests-5", "믿음직한 해결사", "의뢰 보상 5회 수령", 5, "quests", gold = 200),
        Achievement("donations-3", "따뜻한 손길", "방랑자에게 3회 기부", 3, "donations", potions = 3, title = "따뜻한 손길"),
        Achievement("clutch-victory", "기사회생", "체력 10% 이하로 전투 승리", 1, "clutch", greaterPotions = 1),
        Achievement("boss-10", "첫 관문 돌파", "10층 보스 처치", 1, "boss10", points = 1),
        Achievement("boss-30", "심층 개척자", "30층 보스 처치", 1, "boss30", points = 2, title = "심층 개척자"),
        Achievement("boss-50", "출구를 찾은 자", "50층 보스 처치", 1, "boss50", points = 3, title = "출구를 찾은 자"),
        Achievement("next-cycle", "새로운 시작", "장비 계승 후 다음 회차 시작", 1, "cycles", gold = 200, greaterPotions = 2),
        Achievement("boss-50-twice", "다시 찾은 출구", "서로 다른 회차에서 50층 보스 2회 처치", 2, "finalBossCycles", title = "다시 찾은 출구", specialGear = true),
    )

    /** Keep catalogue indices for claim/title actions even when the visible order changes. */
    fun displayOrder(s: State): List<IndexedValue<Achievement>> = all.withIndex().sortedBy { (_, a) ->
        when (a.id) {
            in s.achievements.claimed -> 2
            in s.achievements.unlocked -> 0
            else -> 1
        }
    }

    fun progress(s: State, a: Achievement): Int = when (a.metric) {
        "kills" -> s.bestiary.values.sumOf { it.defeats }
        "species" -> s.bestiary.size
        "chests" -> s.achievements.chests
        "traps" -> s.achievements.traps
        "quests" -> s.achievements.quests
        "donations" -> s.achievements.donations
        "clutch" -> s.achievements.clutchWins
        "boss10" -> if (10 in s.achievements.bossFloors) 1 else 0
        "boss30" -> if (30 in s.achievements.bossFloors) 1 else 0
        "boss50" -> if (50 in s.achievements.bossFloors) 1 else 0
        "cycles" -> s.clears
        "finalBossCycles" -> s.achievements.finalBossCycles.size
        else -> 0
    }.coerceIn(0, a.target)

    fun initialize(s: State) {
        if (s.training || s.debug || s.achievements.initialized) return
        val p = s.achievements
        // Earlier saves have no lifetime chest/trap/NPC counters. Preserve only
        // their actual current-run counts; never fabricate inaccessible history.
        p.chests = maxOf(p.chests, s.runChests)
        p.traps = maxOf(p.traps, s.runTraps)
        p.bossFloors.addAll(s.devices)
        for (floor in listOf(10, 20, 30, 40, 50)) {
            if ((s.bestiary["boss-${floor / 10 - 1}"]?.defeats ?: 0) > 0) p.bossFloors.add(floor)
        }
        if (s.clears > 0) p.bossFloors.addAll(listOf(10, 20, 30, 40, 50))
        p.finalBossCycles.addAll(1..minOf(s.clears, s.cycle - 1))
        if (50 in s.devices) p.finalBossCycles.add(s.cycle)
        p.initialized = true
        evaluate(s, notify = false)
    }

    fun evaluate(s: State, notify: Boolean = true) {
        if (s.training || s.debug) return
        for (a in all) if (progress(s, a) >= a.target && s.achievements.unlocked.add(a.id)) {
            if (notify) s.achievements.notices.add(a.id)
        }
    }

    fun victory(s: State, enemy: Enemy) {
        if (s.training || s.debug) return
        if (s.hp > 0 && s.hp <= Equipment.stats(s).hp * .10) s.achievements.clutchWins++
        if (enemy.boss) {
            s.achievements.bossFloors.add(s.floor)
            if (s.floor == 50) s.achievements.finalBossCycles.add(s.cycle)
        }
        evaluate(s)
    }

    fun event(s: State, metric: String) {
        if (s.training || s.debug) return
        when (metric) {
            "chests" -> s.achievements.chests++
            "traps" -> s.achievements.traps++
            "quests" -> s.achievements.quests++
            "donations" -> s.achievements.donations++
            else -> return
        }
        evaluate(s)
    }

    fun canClaim(s: State, a: Achievement): Boolean = !s.training && !s.debug && s.hp > 0 &&
        s.mode == Mode.MENU && s.menu?.type == "achievements" && s.menu?.returnMode == Mode.SURFACE &&
        s.dungeon == null && a.id in s.achievements.unlocked && a.id !in s.achievements.claimed

    fun claim(s: State, index: Int): Boolean {
        val a = all.getOrNull(index) ?: return false
        if (!canClaim(s, a)) return false
        s.achievements.claimed.add(a.id)
        s.gold += a.gold
        s.points += a.points
        repeat(a.potions) { s.inventory.add(ItemFactory.potion(s, PotionGrade.BASIC)) }
        repeat(a.greaterPotions) { s.inventory.add(ItemFactory.potion(s, PotionGrade.GREATER)) }
        if (a.specialGear) {
            val ring = specialRing(s.nextId++)
            s.inventory.add(ring)
            s.quickEquipIds = listOf(ring.id)
        }
        s.note("업적 보상 · ${a.name} · ${a.rewardText}")
        return true
    }

    fun specialRing(id: Long) = Gear(id, Kind.RING, "귀환자의 인장", tier = 7,
        percent = Stats.zero().copy(attack = .25, hp = .20, agility = -.15),
        price = 1000, variantId = "exit-seal")

    fun title(s: State): String? = all.firstOrNull {
        it.id == s.achievements.selectedTitle && it.id in s.achievements.claimed
    }?.title

    fun selectTitle(s: State, index: Int): Boolean {
        if (s.training || s.debug || s.mode != Mode.MENU || s.menu?.type != "achievements") return false
        if (index == -1) { s.achievements.selectedTitle = null; return true }
        val a = all.getOrNull(index) ?: return false
        if (a.title == null || a.id !in s.achievements.claimed) return false
        s.achievements.selectedTitle = a.id
        return true
    }
}
