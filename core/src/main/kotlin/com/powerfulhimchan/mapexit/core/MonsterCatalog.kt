package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.Serializable

@Serializable
data class MonsterRecord(
    val id: String,
    val name: String,
    val sprite: String,
    val traits: String,
    val firstFloor: Int,
    val lastFloor: Int,
    val encounters: Int = 0,
    val defeats: Int = 0,
    val deaths: Int = 0,
)

data class MonsterSpecies(val id: String, val name: String, val sprite: String, val family: String, val band: Int) {
    val traits: String get() = when (family) {
        "knight" -> "방패로 일반 공격을 줄입니다. 강공격으로 방패를 깨면 공격 기회를 얻습니다."
        "bat" -> "연속 돌진과 급강하를 사용합니다. 연속 공격은 방어, 큰 공격은 회피가 유리합니다."
        "spider" -> "빠른 연속 공격 뒤에 큰 공격을 사용합니다. 방어로 빈틈을 만드세요."
        "golem" -> "방패를 세운 뒤 무거운 공격을 합니다. 강공격과 회피로 대응하세요."
        "wisp" -> "연속 공격 후 잠시 쉽니다. 숨을 고를 때 공격하면 반격받지 않습니다."
        "boss" -> "방어·연속 공격·강공격을 번갈아 사용합니다. 공격 예고를 보고 대응하세요."
        else -> "일반 공격 뒤 몸을 부풀려 도약합니다. 큰 공격 후 숨을 고를 때가 공격 기회입니다."
    }
}

object MonsterCatalog {
    private val names = listOf(
        listOf("동굴 슬라임", "녹슨 기사", "이끼 박쥐", "동굴 거미", "돌 골렘", "이끼 도깨비불"),
        listOf("물결 슬라임", "침수 기사", "해무 박쥐", "물거미", "산호 골렘", "파도 도깨비불"),
        listOf("수정 슬라임", "자수정 기사", "유리 박쥐", "수정 거미", "보석 골렘", "수정 도깨비불"),
        listOf("용암 슬라임", "잿불 기사", "화염 박쥐", "잿빛 거미", "용암 골렘", "잿불 도깨비불"),
        listOf("심연 슬라임", "공허 기사", "심연 박쥐", "그림자 거미", "심연 골렘", "공허 도깨비불"),
    )
    private val families = listOf("slime", "knight", "bat", "spider", "golem", "wisp")
    fun band(floor: Int) = (floor.coerceIn(1, 50) - 1) / 10
    val all: List<MonsterSpecies> = names.flatMapIndexed { band, row -> row.mapIndexed { index, name ->
        val family = families[index]
        MonsterSpecies("$band-$family", name, "monster_${band}_$family", family, band)
    } }
    fun forFloor(floor: Int) = all.filter { it.band == band(floor) }
    fun special(floor: Int, boss: Boolean): MonsterSpecies {
        val b = band(floor)
        val title = if (boss && floor == 50) "마지막 문지기" else if (boss) "${(b + 1) * 10}층의 수문장" else "${b * 10 + 1}~${(b + 1) * 10}층의 정예 기사"
        return MonsterSpecies("${if (boss) "boss" else "elite"}-$b", title,
            "monster_${b}_${if (boss) "boss" else "knight"}", if (boss) "boss" else "knight", b)
    }
    fun find(id: String?) = all.firstOrNull { it.id == id }
    fun describe(enemy: Enemy, floor: Int): MonsterSpecies {
        if (enemy.boss && floor == 50 && enemy.name == "회수자의 문지기")
            return special(floor,true).copy(id = "possessed-4", name = enemy.name)
        find(enemy.speciesId)?.takeIf { enemy.name.removePrefix("상자 속 ") == it.name }?.let { return it }
        if (enemy.boss || enemy.elite) return special(floor, enemy.boss)
        val name = enemy.name.removePrefix("상자 속 ")
        all.firstOrNull { it.name == name }?.let { return it }
        val family = when { name.contains("기사") -> "knight"; name.contains("박쥐") -> "bat"; else -> "slime" }
        // Old/custom saves retain their real name and family without inventing past counts.
        return MonsterSpecies("legacy-$name", name, family, family, band(floor))
    }
    fun record(state: State, enemy: Enemy, encounter: Int = 0, defeat: Int = 0, death: Int = 0) {
        if (state.training || state.debug) return
        val species = describe(enemy, state.floor)
        val previous = state.bestiary[species.id] ?: MonsterRecord(species.id, species.name, species.sprite,
            species.traits, state.floor, state.floor, encounters = if (encounter == 0) 1 else 0)
        state.bestiary[species.id] = previous.copy(lastFloor = state.floor,
            encounters = previous.encounters + encounter, defeats = previous.defeats + defeat, deaths = previous.deaths + death)
    }
}
