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
  ENCOUNTER,
  TRAP_GUIDE,
  TRAP,
  FLOOR_CLEAR,
  INHERIT,
  MENU,
  NOTICE,
  RUN_RESULT,
  CHALLENGE,
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
  ELITE,
  PORTAL,
  CHALLENGE,
  FORGE,
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
  SCROLL("강화서"),
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

@Serializable data class WeaponAffix(val stat: Stat, val amount: Double)

@Serializable
enum class PotionGrade(val label: String, val healRatio: Double, val price: Int) {
  BASIC("회복약", .35, 25),
  GREATER("상급 회복약", .70, 75),
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
    val variantId: String? = null,
    val bossOriginFloor: Int? = null,
    val affixes: List<WeaponAffix> = emptyList(),
    val potionGrade: PotionGrade = PotionGrade.BASIC,
    val refinements: Int = 0,
) {
  val equippable: Boolean get() = kind != Kind.POTION && kind != Kind.SCROLL
  val twoHanded: Boolean
    get() = kind == Kind.SPEAR || kind == Kind.HAMMER

  val weapon: Boolean
    get() = kind in listOf(Kind.SWORD, Kind.SPEAR, Kind.HAMMER, Kind.CLAW, Kind.WHIP)

  fun flatValue(stat: Stat): Double =
      flat.value(stat) + affixes.filter { it.stat == stat }.sumOf { it.amount }

  fun affixSummary(): String =
      affixes.joinToString(" / ") { option ->
        val value =
            if (option.stat in listOf(Stat.CRIT, Stat.CRIT_DAMAGE)) option.amount * 100
            else option.amount
        "${option.stat.label} +${number(value)}${if (option.stat in listOf(Stat.CRIT, Stat.CRIT_DAMAGE)) "%p" else ""}"
      }

  private fun number(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10
    return if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString()
    else rounded.toString()
  }

  fun summary(): String =
      if (affixes.isEmpty()) baseSummary()
      else "보스 전리품 · ${bossOriginFloor}층\n기본: ${baseSummary()}\n추가 옵션: ${affixSummary()}"

  fun baseSummary(): String {
    if (kind == Kind.SCROLL) return "장비 하나를 선택해 +등급 1단계 강화 · 사용 시 1개 소모"
    if (kind == Kind.POTION) return "최대 HP의 ${(potionGrade.healRatio * 100).toInt()}% 회복"
    if (kind == Kind.NECKLACE)
        return when (charm) {
          Charm.MAP -> "주변 시설 탐지"
          Charm.CHEST -> "상자 추가 아이템 확률 +25%"
          Charm.MERCHANT -> "구매 가격 20% 할인"
          Charm.TRAP -> "함정 시간·목표 구간 +25%"
          Charm.QUEST -> "미션 골드 보상 +25%"
          else -> ""
        } + if (refinements > 0) { if (charm == Charm.MAP) " · 탐지 범위 +${refinements}칸" else " · 강화 효과 +${refinements * 5}%p" } else ""
    return Stat.entries
        .mapNotNull { st ->
          val f = flat.value(st)
          val p = percent.value(st)
          if (f == 0.0 && p == 0.0) null
          else
              st.label +
                  when {
                    p != 0.0 -> " ${if(p>0) "+" else ""}${number(p*100)}%"
                    st == Stat.CRIT -> " ${if(f>0) "+" else ""}${number(f*100)}%p"
                    st == Stat.CRIT_DAMAGE -> " ${if(f>0) "+" else ""}${number(f*100)}%p"
                    else -> " ${if(f>0) "+" else ""}${number(f)}"
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
    var questOffer: Quest? = null,
    var offerChecked: Boolean = false,
    var monsterId: String? = null,
    var npcRole: NpcRole = NpcRole.QUEST,
    var npcGift: Boolean? = null,
    var challengeMark: Int = 0,
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
    var eliteCleared: Boolean = false,
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
enum class EnemyIntent {
  STRIKE,
  HEAVY,
  FLURRY,
  GUARD,
  RECOVER,
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
    var intent: EnemyIntent = EnemyIntent.STRIKE,
    var round: Int = 0,
    var opening: Boolean = false,
    val elite: Boolean = false,
    val speciesId: String? = null,
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
  BLOCK,
  DODGE,
  MISS,
}

@Serializable
data class AttackHit(val damage: Double, val critical: Boolean = false, val luckyAttack: Boolean = false, val luckyCritical: Boolean = false) {
  val amount: Int get() = if (damage > 0) kotlin.math.round(damage).toInt().coerceAtLeast(1) else 0
}

@Serializable
data class BattleImpact(
    val serial: Long,
    val target: ImpactTarget,
    val amount: Int,
    val outcome: ImpactOutcome,
    val critical: Boolean = false,
    val luckyAttack: Boolean = false,
    val luckyCritical: Boolean = false,
    val tactic: String = "",
    val strikes: List<AttackHit> = emptyList(),
) {
  fun label(): String {
    val base =
        when (outcome) {
          ImpactOutcome.MISS -> "빗나감"
          ImpactOutcome.DODGE -> "회피"
          ImpactOutcome.BLOCK -> "완전 방어 · 0 피해"
          ImpactOutcome.GUARD -> "${if(critical) "치명타 · " else ""}방어 -$amount"
          else -> "${if(critical) "치명타 " else ""}-$amount"
        }
    val effects =
        listOfNotNull(
            if (luckyAttack) "행운 공격 강화" else null,
            if (luckyCritical) "행운 치명 확률 강화" else null,
            tactic.takeIf { it.isNotEmpty() },
        )
    return if (effects.isEmpty()) base else "$base · ${effects.joinToString(" · ")}"
  }
}

@Serializable
data class BattleResult(
    val enemy: Enemy,
    val gold: Int,
    val experience: Int,
    val items: List<String>,
    val returnMode: Mode,
    val rewards: List<Gear> = emptyList(),
)

@Serializable
data class RunResult(
    val retreated: Boolean,
    val floor: Int,
    val deepestFloor: Int,
    val kills: Int,
    val chests: Int,
    val traps: Int,
    val elapsedMs: Long,
    val lostGold: Int,
    val lostItems: Int,
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

@Serializable data class LevelUpNotice(val from: Int, val to: Int, val gainedPoints: Int)

@Serializable data class ChestLootNotice(val items: List<Gear>, val gold: Int)

@Serializable
data class Menu(val type: String, val argument: Long = 0L, val returnMode: Mode = Mode.SURFACE)

data class Choice(val label: String, val action: String, val argument: Long = 0L)

@Serializable
data class State(
    val version: Int = 1,
    var rng: Long = 20261028L,
    var visitorSeed: Long? = null,
    var nextId: Long = 1L,
    var cycle: Int = 1,
    var clears: Int = 0,
    var level: Int = 1,
    var experience: Int = 0,
    var levelUpNotice: LevelUpNotice? = null,
    var chestLoot: ChestLootNotice? = null,
    var quickEquipIds: List<Long> = emptyList(),
    var points: Int = 0,
    var base: Stats = Stats(),
    var hp: Double = 360.0,
    var gold: Int = 0,
    var bank: Int = 0,
    var floor: Int = 0,
    var highest: Int = 0,
    var runDeepestFloor: Int = 0,
    var runKills: Int = 0,
    var runChests: Int = 0,
    var runTraps: Int = 0,
    var runElapsedMs: Long = 0L,
    var runResult: RunResult? = null,
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
    var encounter: Event? = null,
    var quest: Quest? = null,
    var menu: Menu? = null,
    var menuStack: MutableList<Menu> = mutableListOf(),
    var log: MutableList<String> = mutableListOf(),
    var message: String = "지상 / 출발 준비",
    var shop: MutableList<Gear> = mutableListOf(),
    var movementMs: Long = 0L,
    var playMs: Long = 0L,
    var debug: Boolean = false,
    var training: Boolean = false,
    var bootstrapped: Boolean = false,
    var bestiary: MutableMap<String, MonsterRecord> = mutableMapOf(),
    var npcServicesUsed: MutableSet<Int> = mutableSetOf(),
    val achievements: AchievementProgress = AchievementProgress(),
    val challengeProgress: ChallengeProgress = ChallengeProgress(),
    var challenge: ChallengeRun? = null,
    val story: StoryProgress = StoryProgress(),
) {
  val experienceRequired: Int get() = level * 80
  val experienceRemaining: Int get() = (experienceRequired - experience).coerceAtLeast(0)

  fun random(): Double {
    rng = rng * 6364136223846793005L + 1442695040888963407L
    return (rng ushr 11).toDouble() / 9007199254740992.0
  }

  fun nextInt(bound: Int): Int = (random() * max(1, bound)).toInt().coerceAtMost(max(1, bound) - 1)

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
