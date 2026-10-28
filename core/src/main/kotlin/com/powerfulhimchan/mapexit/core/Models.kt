package com.powerfulhimchan.mapexit.core

import kotlin.math.max
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class Mode {
    SURFACE,
    EXPLORE,
    MOVING,
    COMBAT_ATTACK,
    COMBAT_DEFEND,
    VICTORY,
    TRAP_GUIDE,
    TRAP,
    FLOOR_CLEAR,
    INHERIT,
    MENU,
    NOTICE,
}

@Serializable
enum class Event {
    NONE,
    MONSTER,
    CHEST,
    HEAL,
    MERCHANT,
    NPC,
    EXIT,
    TRAP,
}

@Serializable
enum class Slot(val label: String) {
    HEAD("머리"),
    LEFT("왼손"),
    RIGHT("오른손"),
    BODY("몸통"),
    PANTS("바지"),
    BOOTS("신발"),
    NECK("목걸이"),
    RING1("반지 1"),
    RING2("반지 2"),
}

@Serializable
enum class Kind(val label: String) {
    SWORD("검"),
    SPEAR("창"),
    HAMMER("해머"),
    CLAW("클로"),
    WHIP("채찍"),
    SHIELD("방패"),
    HEAD("투구"),
    BODY("갑옷"),
    PANTS("바지"),
    BOOTS("신발"),
    RING("반지"),
    NECKLACE("목걸이"),
    POTION("회복약"),
}

@Serializable
enum class Charm {
    NONE,
    MAP,
    CHEST,
    MERCHANT,
    TRAP,
    QUEST,
}

@Serializable
enum class Stat(val label: String) {
    HP("최대 체력"),
    ATTACK("공격력"),
    DEFENSE("방어력"),
    AGILITY("민첩"),
    CRIT("치명타 확률"),
    CRIT_DAMAGE("치명타 공격력"),
    LUCK("행운"),
}

@Serializable
data class Stats(
    val hp: Double = 360.0,
    val attack: Double = 100.0,
    val defense: Double = 35.0,
    val agility: Double = 20.0,
    val crit: Double = .2,
    val critDamage: Double = 1.5,
    val luck: Double = 0.0,
) {
    fun value(stat: Stat): Double =
        when (stat) {
            Stat.HP -> hp
            Stat.ATTACK -> attack
            Stat.DEFENSE -> defense
            Stat.AGILITY -> agility
            Stat.CRIT -> crit
            Stat.CRIT_DAMAGE -> critDamage
            Stat.LUCK -> luck
        }

    fun add(stat: Stat, amount: Double): Stats =
        when (stat) {
            Stat.HP -> copy(hp = hp + amount)
            Stat.ATTACK -> copy(attack = attack + amount)
            Stat.DEFENSE -> copy(defense = defense + amount)
            Stat.AGILITY -> copy(agility = agility + amount)
            Stat.CRIT -> copy(crit = crit + amount)
            Stat.CRIT_DAMAGE -> copy(critDamage = critDamage + amount)
            Stat.LUCK -> copy(luck = luck + amount)
        }

    companion object {
        fun zero() = Stats(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

@Serializable
data class Gear(
    val id: Long,
    val kind: Kind,
    val name: String,
    val tier: Int = 1,
    val flat: Stats = Stats.zero(),
    val percent: Stats = Stats.zero(),
    val charm: Charm = Charm.NONE,
    var inherited: Boolean = false,
    val price: Int = 30,
) {
    val twoHanded: Boolean
        get() = kind == Kind.SPEAR || kind == Kind.HAMMER

    val weapon: Boolean
        get() = kind in listOf(Kind.SWORD, Kind.SPEAR, Kind.HAMMER, Kind.CLAW, Kind.WHIP)

    fun summary(): String {
        if (kind == Kind.POTION) return "최대 HP의 35% 회복"
        if (kind == Kind.NECKLACE)
            return when (charm) {
                Charm.MAP -> "주변 시설 탐지"
                Charm.CHEST -> "상자 추가 아이템 확률 +25%"
                Charm.MERCHANT -> "구매 가격 20% 할인"
                Charm.TRAP -> "함정 시간·목표 구간 +25%"
                Charm.QUEST -> "미션 골드 보상 +25%"
                else -> ""
            }
        return Stat.entries
            .mapNotNull { st ->
                val f = flat.value(st)
                val p = percent.value(st)
                if (f == 0.0 && p == 0.0) null
                else
                    st.label +
                        when {
                            p != 0.0 -> " ${if(p>0) "+" else ""}${(p*100).toInt()}%"
                            st == Stat.CRIT -> " ${if(f>0) "+" else ""}${(f*100).toInt()}%p"
                            st == Stat.CRIT_DAMAGE -> " ${if(f>0) "+" else ""}${(f*100).toInt()}%p"
                            else -> " ${if(f>0) "+" else ""}${f.toInt()}"
                        }
            }
            .joinToString(" / ")
    }
}

@Serializable
data class Cell(
    var wall: Boolean = true,
    var event: Event = Event.NONE,
    var seen: Boolean = false,
    var resolved: Boolean = false,
    var trapType: Int = 0,
)

@Serializable
data class Dungeon(
    val width: Int,
    val height: Int,
    val cells: MutableList<Cell>,
    var x: Int = 1,
    var y: Int = 1,
    var direction: Int = 1,
    var bossCleared: Boolean = false,
    var arrivalDirection: Int = -1,
) {
    fun cell(x: Int, y: Int): Cell =
        if (x in 0 until width && y in 0 until height) cells[y * width + x] else Cell()

    fun here(): Cell = cell(x, y)

    fun reveal() {
        for (yy in y - 1..y + 1) for (xx in x - 1..x + 1) if (
            xx in 0 until width && yy in 0 until height
        )
            cell(xx, yy).seen = true
    }
}

@Serializable
data class Enemy(
    val name: String,
    val maxHp: Double,
    var hp: Double,
    val attack: Double,
    val defense: Double,
    val precision: Double,
    val boss: Boolean = false,
)

@Serializable
enum class ImpactTarget {
    PLAYER,
    ENEMY,
}

@Serializable
enum class ImpactOutcome {
    DAMAGE,
    GUARD,
    DODGE,
    MISS,
}

@Serializable
data class BattleImpact(
    val serial: Long,
    val target: ImpactTarget,
    val amount: Int,
    val outcome: ImpactOutcome,
    val critical: Boolean = false,
) {
    fun label(): String =
        when (outcome) {
            ImpactOutcome.MISS -> "빗나감"
            ImpactOutcome.DODGE -> "회피"
            ImpactOutcome.GUARD -> "${if(critical) "치명타 · " else ""}방어 -$amount"
            else -> "${if(critical) "치명타 " else ""}-$amount"
        }
}

@Serializable
data class BattleResult(
    val enemy: Enemy,
    val gold: Int,
    val experience: Int,
    val items: List<String>,
    val returnMode: Mode,
)

@Serializable
data class Quest(
    val title: String,
    val kind: String,
    val target: Int,
    var progress: Int = 0,
    val reward: Int = 100,
) {
    val complete: Boolean
        get() = progress >= target
}

@Serializable
data class Menu(val type: String, val argument: Long = 0L, val returnMode: Mode = Mode.SURFACE)

data class Choice(val label: String, val action: String, val argument: Long = 0L)

@Serializable
data class State(
    val version: Int = 1,
    var rng: Long = 20261028L,
    var nextId: Long = 1L,
    var cycle: Int = 1,
    var clears: Int = 0,
    var level: Int = 1,
    var experience: Int = 0,
    var points: Int = 0,
    var base: Stats = Stats(),
    var hp: Double = 360.0,
    var gold: Int = 0,
    var bank: Int = 0,
    var floor: Int = 0,
    var highest: Int = 0,
    var devices: MutableSet<Int> = mutableSetOf(),
    var inventory: MutableList<Gear> = mutableListOf(),
    var equipment: MutableMap<Slot, Long> = mutableMapOf(),
    var dungeon: Dungeon? = null,
    var mode: Mode = Mode.SURFACE,
    var selection: Int = 0,
    var enemy: Enemy? = null,
    var battleId: Long = 0L,
    var battleResult: BattleResult? = null,
    var impactSequence: Long = 0L,
    var lastImpact: BattleImpact? = null,
    var trap: TrapState? = null,
    var quest: Quest? = null,
    var menu: Menu? = null,
    var menuStack: MutableList<Menu> = mutableListOf(),
    var log: MutableList<String> = mutableListOf(),
    var message: String = "지상 / 출발 준비",
    var shop: MutableList<Gear> = mutableListOf(),
    var movementMs: Long = 0L,
    var playMs: Long = 0L,
    var debug: Boolean = false,
    var bootstrapped: Boolean = false,
) {
    fun random(): Double {
        rng = rng * 6364136223846793005L + 1442695040888963407L
        return (rng ushr 11).toDouble() / 9007199254740992.0
    }

    fun nextInt(bound: Int): Int =
        (random() * max(1, bound)).toInt().coerceAtMost(max(1, bound) - 1)

    fun note(text: String) {
        message = text
        log.add(text)
        while (log.size > 18) log.removeAt(0)
    }
}

object SaveCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(state: State): String = json.encodeToString(state)

    fun decode(text: String): State =
        json.decodeFromString<State>(text).also {
            require(it.version == 1) { "지원하지 않는 저장 버전" }
            require(it.level >= 1 && it.cycle >= 1 && it.gold >= 0 && it.bank >= 0)
            require(it.inventory.map { item -> item.id }.distinct().size == it.inventory.size)
            require(it.equipment.values.all { id -> it.inventory.any { item -> item.id == id } })
        }
}
