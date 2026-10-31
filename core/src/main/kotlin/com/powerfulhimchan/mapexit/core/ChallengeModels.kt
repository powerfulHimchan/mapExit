package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.Serializable

@Serializable
enum class ChallengeKind(val title: String, val objective: String, val seconds: Int) {
    LOST("길 잃은 미궁", "미니맵 없이 벽 표식을 기억하며 90초 안에 출구를 찾으세요.", 90),
    MEMORY("기억의 회랑", "7초 동안 지도를 기억한 뒤 60초 안에 출구를 찾으세요.", 60),
    COLLAPSE("무너지는 유적", "지나간 바닥은 3초 뒤 무너집니다. 안정된 문양을 따라 탈출하세요.", 70),
    SEALS("봉인의 방", "입구의 문양 순서대로 세 제단을 작동한 뒤 출구로 가세요. 오작동은 10초 차감됩니다.", 90),
    TORCH("꺼져가는 횃불", "불이 꺼지기 전에 화로에서 보충하며 90초 안에 출구를 찾으세요.", 90),
    HAZARDS("칼날의 회랑", "칼날·낙하물·닫히는 벽의 주기를 읽어 탈출하세요. 실수 3회면 실패합니다.", 90),
    STEPS("한정된 발걸음", "준비 화면에서 경로를 계획하세요. 시간 대신 이동 칸 수가 제한됩니다.", 0),
}

@Serializable enum class ChallengePhase { INTRO, PREVIEW, ACTIVE, HAZARD, PAUSED, RESULT }

@Serializable
data class ChallengeRecord(
    var attempts: Int = 0,
    var clears: Int = 0,
    var bestTimeMs: Long? = null,
    var bestSteps: Int? = null,
)

@Serializable
data class ChallengeProgress(
    var seed: Long? = null,
    val usedFloors: MutableSet<Int> = mutableSetOf(),
    val records: MutableMap<ChallengeKind, ChallengeRecord> = mutableMapOf(),
)

@Serializable
data class ChallengeRun(
    val kind: ChallengeKind,
    val seed: Long,
    val hostDungeon: Dungeon,
    val hostFloor: Int,
    val stepLimit: Int,
    var phase: ChallengePhase = ChallengePhase.INTRO,
    var pausedFrom: ChallengePhase = ChallengePhase.ACTIVE,
    var previewMs: Long = 7000,
    var elapsedMs: Long = 0,
    var steps: Int = 0,
    var torchMs: Long = 22000,
    val sealOrder: List<Int> = emptyList(),
    var seals: Int = 0,
    val collapseAt: MutableMap<Int, Long> = mutableMapOf(),
    var hazardMs: Long = 0,
    var hazardCooldownMs: Long = 0,
    var mistakes: Int = 0,
    var navigationRevision: Int = 0,
    var autoMoving: Boolean = false,
    var movementMs: Long = 0,
    val collapsedCells: MutableSet<Int> = mutableSetOf(),
    var success: Boolean = false,
    var result: String = "",
    var rewardApplied: Boolean = false,
    var rewardGold: Int = 0,
    var rewards: List<Gear> = emptyList(),
    var feedback: String = "",
) {
    val remainingMs: Long get() = (kind.seconds * 1000L - elapsedMs).coerceAtLeast(0)
    val remainingSteps: Int get() = (stepLimit - steps).coerceAtLeast(0)
    val started: Boolean get() = phase != ChallengePhase.INTRO
}
