package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.Serializable

@Serializable
enum class PrefixIntensity(val label: String, val multiplier: Double) {
    NONE("", 1.0),
    LITTLE("조금", 1.25),
    SLIGHT("약간", 1.50),
    VERY("매우", 2.0),
}

/** Authored bonuses stay separate from the original item and boss rolls. */
data class ItemPrefix(val id: String, val name: String, val bonus: Stats) {
    fun stats(rank: Int, intensity: PrefixIntensity = PrefixIntensity.NONE): Stats = Stat.entries.fold(Stats.zero()) { result, stat ->
        val value = bonus.value(stat)
        result.add(stat, if (value > 0) value * rank.coerceAtLeast(1) * intensity.multiplier else value)
    }
}

object PrefixCatalog {
    private fun p(id: String, name: String, attack: Double = 0.0, defense: Double = 0.0,
        agility: Double = 0.0, crit: Double = 0.0, damage: Double = 0.0,
        hp: Double = 0.0, luck: Double = 0.0) =
        ItemPrefix(id, name, Stats(hp, attack, defense, agility, crit, damage, luck))
    val byKind = mapOf(
        Kind.SWORD to listOf(
            p("sharp", "예리한", attack=2.0), p("agile", "날렵한", agility=2.0),
            p("blunt", "뭉툭한", defense=1.0, damage=-.01),
            p("rusty", "녹슨", attack=-2.0, crit=.20), p("light", "가벼운", agility=1.0, crit=.05)),
        Kind.SPEAR to listOf(
            p("light", "가벼운", agility=2.0, crit=.05, damage=-.05),
            p("long", "긴", attack=1.0, agility=-1.0, crit=.10), p("solid", "단단한", attack=3.0),
            p("short", "짧은", crit=.10, damage=.10), p("heavy", "무거운", attack=3.0, agility=-1.0)),
        Kind.HAMMER to listOf(
            p("weighty", "묵직한", attack=5.0, agility=-2.0), p("solid", "단단한", attack=3.0, defense=1.0),
            p("pointed", "뾰족한", crit=.05, damage=.10), p("balanced", "균형 잡힌", agility=3.0, damage=-.05),
            p("destructive", "파괴적인", attack=3.0, damage=.15, crit=-.05)),
        Kind.CLAW to listOf(
            p("sharp", "예리한", attack=2.0, damage=.05), p("agile", "날렵한", agility=3.0),
            p("fierce", "맹렬한", attack=3.0, defense=-1.0), p("precise", "정교한", crit=.10, attack=-1.0),
            p("cruel", "잔혹한", damage=.15, agility=-1.0)),
        Kind.WHIP to listOf(
            p("sharp", "날카로운", attack=2.0, damage=.05), p("elastic", "탄력 있는", agility=2.0, crit=.05),
            p("long", "긴", attack=3.0, agility=-1.0), p("light", "가벼운", agility=3.0, attack=-1.0),
            p("severe", "매서운", crit=.10, damage=.10, attack=-1.0)),
        Kind.SHIELD to listOf(
            p("solid", "단단한", defense=2.0), p("reliable", "든든한", hp=15.0),
            p("light", "가벼운", agility=2.0, defense=-1.0), p("offensive", "공격적인", attack=2.0, defense=-1.0),
            p("lucky", "행운의", luck=3.0, defense=-1.0)),
        Kind.HEAD to listOf(
            p("sturdy", "견고한", defense=1.0, hp=5.0), p("thick", "두꺼운", hp=15.0, agility=-1.0),
            p("light", "가벼운", agility=2.0), p("menacing", "위협적인", attack=2.0, defense=-1.0),
            p("lucky", "행운의", luck=3.0)),
        Kind.BODY to listOf(
            p("sturdy", "견고한", defense=2.0), p("thick", "두꺼운", hp=20.0, agility=-1.0),
            p("light", "가벼운", agility=2.0, defense=-1.0), p("warrior", "전사의", attack=2.0, hp=5.0),
            p("guardian", "수호의", hp=10.0, defense=1.0, agility=-1.0)),
        Kind.PANTS to listOf(
            p("tough", "질긴", hp=10.0), p("sturdy", "튼튼한", defense=2.0),
            p("light", "가벼운", agility=2.0, defense=-1.0), p("agile", "날렵한", agility=3.0, hp=-5.0),
            p("lucky", "행운의", luck=3.0, defense=-1.0)),
        Kind.BOOTS to listOf(
            p("agile", "날렵한", agility=2.0), p("solid", "단단한", defense=2.0, agility=-1.0),
            p("soft", "푹신한", hp=10.0), p("lucky", "행운의", luck=3.0),
            p("precise", "정교한", crit=.05, agility=-1.0)),
    )
    fun find(kind: Kind, id: String?) = byKind[kind]?.firstOrNull { it.id == id }
    fun roll(s: State, kind: Kind): String? {
        val entries = byKind[kind] ?: return null
        return (s.nextInt(entries.size + 1) - 1).takeIf { it >= 0 }?.let { entries[it].id }
    }
    fun apply(g: Gear, id: String?, intensity: PrefixIntensity = PrefixIntensity.NONE): Gear {
        val prefix = find(g.kind,id) ?: return g
        val modifier = if (intensity == PrefixIntensity.NONE) "" else "${intensity.label} "
        return g.copy(name="$modifier${prefix.name} ${g.name}", prefixId=prefix.id,
            prefixRank=g.tier, prefixIntensity=intensity)
    }
}

data class ArmorVariant(val id: String, val kind: Kind, val name: String, val base: Stats) {
    fun stats(tier: Int) = Stat.entries.fold(Stats.zero()) { result, stat ->
        result.add(stat, base.value(stat) * if(stat in listOf(Stat.CRIT,Stat.CRIT_DAMAGE)) 1 else tier)
    }
}
object ArmorCatalog {
    private fun a(id:String,kind:Kind,name:String,hp:Double=0.0,defense:Double=0.0,agility:Double=0.0,
        attack:Double=0.0,crit:Double=0.0)=ArmorVariant(id,kind,name,Stats(hp,attack,defense,agility,crit,0.0,0.0))
    val all = listOf(
        a("shield",Kind.SHIELD,"방패",defense=16.0), a("buckler",Kind.SHIELD,"소형 방패",defense=10.0,agility=4.0),
        a("tower",Kind.SHIELD,"대형 방패",defense=24.0,agility=-4.0),a("spiked",Kind.SHIELD,"가시 방패",defense=12.0,attack=4.0),
        a("head",Kind.HEAD,"투구",hp=15.0,defense=4.0),a("leathercap",Kind.HEAD,"가죽 모자",hp=10.0,defense=2.0,agility=2.0),
        a("headband",Kind.HEAD,"전투 두건",hp=5.0,defense=1.0,crit=.03),a("heavyhelm",Kind.HEAD,"중갑 투구",hp=25.0,defense=6.0,agility=-2.0),
        a("body",Kind.BODY,"갑옷",hp=15.0,defense=4.0),a("leatherarmor",Kind.BODY,"가죽 갑옷",hp=10.0,defense=2.0,agility=3.0),
        a("chainmail",Kind.BODY,"사슬 갑옷",hp=20.0,defense=6.0,agility=-2.0),a("platearmor",Kind.BODY,"철갑옷",hp=30.0,defense=9.0,agility=-5.0),
        a("pants",Kind.PANTS,"바지",hp=15.0,defense=4.0),a("leatherpants",Kind.PANTS,"가죽 바지",hp=10.0,defense=2.0,agility=2.0),
        a("scoutpants",Kind.PANTS,"정찰 바지",hp=5.0,defense=1.0,agility=4.0),a("platepants",Kind.PANTS,"판금 바지",hp=25.0,defense=7.0,agility=-3.0),
        a("boots",Kind.BOOTS,"신발",defense=2.0,agility=3.0),a("leatherboots",Kind.BOOTS,"가죽 장화",hp=5.0,defense=1.0,agility=4.0),
        a("ironboots",Kind.BOOTS,"철제 장화",defense=5.0,agility=-1.0),a("windboots",Kind.BOOTS,"바람걸음 신발",hp=-5.0,agility=6.0),
    )
    fun of(kind:Kind)=all.filter { it.kind==kind }
}

object NecklaceCatalog {
    val names = mapOf(Charm.MAP to "탐색의 목걸이", Charm.CHEST to "보물의 목걸이", Charm.MERCHANT to "상인의 목걸이",
        Charm.TRAP to "함정의 목걸이", Charm.QUEST to "의뢰의 목걸이", Charm.HEALING to "치유의 목걸이",
        Charm.TRAINING to "수련의 목걸이", Charm.GOLD to "황금의 목걸이", Charm.DURATION to "지속의 목걸이", Charm.UNYIELDING to "불굴의 목걸이")
    fun summary(g:Gear):String {
        val bonus=g.refinements*.05
        fun percent(value:Double)=kotlin.math.round(value*100).toInt()
        return when(g.charm) {
            Charm.MAP -> "주변 시설 탐지 · 거리 ${4+g.refinements}칸"
            Charm.CHEST -> "상자 추가 아이템 확률 ${percent((.25+bonus).coerceAtMost(.65))}% · 행운 추가 적용"
            Charm.MERCHANT -> "구매 가격 ${percent((.20+bonus).coerceAtMost(.60))}% 할인"
            Charm.TRAP -> "함정 시간·목표 구간 +${percent(.25+bonus)}%"
            Charm.QUEST -> "미션 골드 보상 +${percent(.25+bonus)}%"
            Charm.HEALING -> "회복약 회복량 +${percent(.15+bonus)}%"
            Charm.TRAINING -> "전투 경험치 +${percent(.15+bonus)}%"
            Charm.GOLD -> "전투·상자 골드 +${percent(.15+bonus)}%"
            Charm.DURATION -> "시간제 물약 지속시간 +${percent(.25+bonus)}%"
            Charm.UNYIELDING -> "출정당 1회 치명 피해 생존 · HP ${percent((.20+bonus).coerceAtMost(.50))}%"
            else -> ""
        }
    }
    fun multiplier(s:State,charm:Charm,base:Double):Double =
        if(Equipment.charm(s)==charm) 1+base+Equipment.charmRefinement(s) else 1.0
}
