package com.powerfulhimchan.mapexit.core

import kotlin.math.round

data class WeaponVariant(
    val id: String,
    val kind: Kind,
    val name: String,
    val attack: Double = 1.0,
    val defense: Double = 1.0,
    val agility: Double = 1.0,
    val crit: Double = 1.0,
    val critDamage: Double = 1.0,
) {
    fun stats(tier: Int): Stats {
        val base = GameBalance.weapon(kind, tier.toDouble())
        return base.copy(
            attack = round(base.attack * attack),
            defense = round(base.defense * defense),
            agility = round(base.agility * agility),
            crit = round(base.crit * crit * 1000) / 1000,
            critDamage = round(base.critDamage * critDamage * 1000) / 1000,
        )
    }
}

object WeaponCatalog {
    val all =
        listOf(
            WeaponVariant("longsword", Kind.SWORD, "장검"),
            WeaponVariant("rapier", Kind.SWORD, "레이피어", attack = .82, critDamage = 1.45),
            WeaponVariant("sabre", Kind.SWORD, "군도", attack = 1.18, critDamage = .65),
            WeaponVariant("falchion", Kind.SWORD, "팔시온", attack = 1.10, critDamage = .80),
            WeaponVariant("longspear", Kind.SPEAR, "장창"),
            WeaponVariant(
                "halberd",
                Kind.SPEAR,
                "할버드",
                attack = 1.18,
                defense = 1.50,
                crit = 1.20,
                critDamage = .90,
            ),
            WeaponVariant(
                "trident",
                Kind.SPEAR,
                "삼지창",
                attack = .90,
                defense = .70,
                crit = .70,
                critDamage = 1.20,
            ),
            WeaponVariant(
                "lance",
                Kind.SPEAR,
                "랜스",
                attack = 1.08,
                defense = 1.20,
                crit = 1.40,
                critDamage = 1.10,
            ),
            WeaponVariant("warhammer", Kind.HAMMER, "전투망치"),
            WeaponVariant(
                "sledgehammer",
                Kind.HAMMER,
                "대형 망치",
                attack = 1.18,
                defense = 1.25,
                agility = 1.20,
                crit = 1.20,
                critDamage = 1.05,
            ),
            WeaponVariant(
                "mace",
                Kind.HAMMER,
                "철퇴",
                attack = .88,
                defense = .65,
                agility = .65,
                crit = .75,
                critDamage = .80,
            ),
            WeaponVariant(
                "morningstar",
                Kind.HAMMER,
                "모닝스타",
                attack = .95,
                crit = 1.15,
                critDamage = 1.25,
            ),
            WeaponVariant("knuckle", Kind.CLAW, "너클클로"),
            WeaponVariant("hookclaw", Kind.CLAW, "갈고리 클로", attack = 1.30, agility = .70),
            WeaponVariant("windclaw", Kind.CLAW, "바람발톱", attack = .75, agility = 1.35),
            WeaponVariant("katar", Kind.CLAW, "카타르", attack = 1.15, agility = .85),
            WeaponVariant("leatherwhip", Kind.WHIP, "가죽채찍"),
            WeaponVariant(
                "chainwhip",
                Kind.WHIP,
                "사슬채찍",
                attack = 1.30,
                agility = .70,
                crit = 1.30,
                critDamage = 1.05,
            ),
            WeaponVariant(
                "thornwhip",
                Kind.WHIP,
                "가시채찍",
                attack = .95,
                agility = .90,
                crit = 1.20,
                critDamage = 1.40,
            ),
            WeaponVariant(
                "flexiblewhip",
                Kind.WHIP,
                "유연한 채찍",
                attack = .75,
                agility = 1.40,
                crit = .70,
                critDamage = .75,
            ),
        )

    fun of(kind: Kind): List<WeaponVariant> = all.filter { it.kind == kind }
}
