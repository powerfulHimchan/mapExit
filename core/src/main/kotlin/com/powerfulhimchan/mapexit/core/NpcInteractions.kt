package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.math.roundToInt

@Serializable
enum class NpcRole(val title: String, val sprite: String, val action: String) {
    QUEST("여행자의 의뢰", "npc", ""),
    BEGGAR("구걸하는 방랑자", "npc_beggar", "npcDonate"),
    HEALER("떠돌이 치료사", "npc_healer", "npcHeal"),
    HERBALIST("약초 수집가", "npc_herbalist", "npcPotion"),
}

data class NpcOutcome(val success: Boolean, val message: String)

object NpcInteractions {
    const val DONATION_GOLD = 10
    const val HEAL_RATIO = .20
    const val GIFT_CHANCE = .30

    /** A per-floor stream keeps NPC identity and the gift stable on map regeneration. */
    private fun random(seed: Long, floor: Int, salt: Long) =
        State(bootstrapped = true, rng = seed xor (floor.toLong() * 104729L) xor salt)

    fun roleFor(seed: Long, floor: Int): NpcRole =
        NpcRole.entries[random(seed, floor, 0x4E50435F524F4C45L).nextInt(NpcRole.entries.size)]

    fun giftFor(seed: Long, floor: Int): Boolean =
        random(seed, floor, 0x4E50435F47494654L).random() < GIFT_CHANCE

    fun role(state: State): NpcRole = state.dungeon?.here()?.npcRole ?: NpcRole.QUEST

    fun used(state: State): Boolean = state.dungeon?.here()?.resolved == true ||
        (role(state) != NpcRole.QUEST && state.floor in state.npcServicesUsed)

    fun blockedReason(state: State): String? {
        if (used(state)) return "이 회차에서 이미 도움을 받은 NPC입니다. 다시 이용할 수 없습니다."
        return when (role(state)) {
            NpcRole.BEGGAR -> if (state.gold < DONATION_GOLD) "골드가 부족합니다. 기부하려면 10G가 필요합니다." else null
            NpcRole.HEALER -> when {
                state.hp <= 0 -> "지금은 치료를 받을 수 없습니다."
                state.hp >= Equipment.stats(state).hp -> "체력이 가득 차 있습니다. 치료는 체력이 줄었을 때 받을 수 있습니다."
                else -> null
            }
            else -> null
        }
    }

    fun actionLabel(role: NpcRole): String = when (role) {
        NpcRole.BEGGAR -> "기부하기 · 10G"
        NpcRole.HEALER -> "치료받기 · 20% 회복"
        NpcRole.HERBALIST -> "회복약 1개 받기"
        else -> ""
    }

    fun description(state: State): String {
        val body = when (role(state)) {
            NpcRole.BEGGAR -> "“길을 잃고 가진 것을 모두 잃었어요. 10G만 도와주시겠어요?”\n기부하면 30% 확률로 회복약 1개를 답례합니다. 소지 ${state.gold}G"
            NpcRole.HEALER -> "“잠시 쉬세요. 가벼운 상처는 제가 돌봐드릴게요.”\n무료로 최대 체력의 20%를 회복합니다. 현재 ${state.hp.roundToInt()} / ${Equipment.stats(state).hp.roundToInt()}"
            NpcRole.HERBALIST -> "“직접 만든 회복약이에요. 필요할 때 쓰세요.”\n회복약 1개를 받습니다. 가방에서 사용하면 최대 체력의 35%를 회복합니다."
            else -> ""
        }
        return blockedReason(state)?.let { "$it\n\n$body" } ?: body
    }

    /** Validate again at mutation time; repeated/stale taps cannot consume or reward twice. */
    fun use(state: State, action: String): NpcOutcome {
        val cell = state.dungeon?.here()
        val role = role(state)
        if (state.mode != Mode.ENCOUNTER || state.encounter != Event.NPC || cell?.event != Event.NPC ||
            role == NpcRole.QUEST || action != role.action) return NpcOutcome(false, "지금은 이 NPC를 이용할 수 없습니다.")
        blockedReason(state)?.let { return NpcOutcome(false, it) }
        val message = when (role) {
            NpcRole.BEGGAR -> {
                val gift = cell.npcGift ?: giftFor(state.visitorSeed ?: state.rng, state.floor).also { cell.npcGift = it }
                state.gold -= DONATION_GOLD
                if (gift) {
                    state.inventory.add(ItemFactory.create(state, Kind.POTION))
                    "방랑자에게 10G를 기부했습니다 · 답례로 회복약 1개를 받았습니다"
                } else "방랑자에게 10G를 기부했습니다 · 방랑자가 고마움을 전합니다"
            }
            NpcRole.HEALER -> {
                val before = state.hp
                val maximum = Equipment.stats(state).hp
                state.hp = min(maximum, state.hp + maximum * HEAL_RATIO)
                "치료사의 도움 · 체력 ${(state.hp - before).roundToInt()} 회복"
            }
            NpcRole.HERBALIST -> {
                state.inventory.add(ItemFactory.create(state, Kind.POTION))
                "약초 수집가에게 회복약 1개를 받았습니다 · 가방에서 사용할 수 있습니다"
            }
            else -> return NpcOutcome(false, "지금은 이 NPC를 이용할 수 없습니다.")
        }
        cell.resolved = true
        state.npcServicesUsed.add(state.floor)
        return NpcOutcome(true, message)
    }
}
