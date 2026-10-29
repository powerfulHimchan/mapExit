package com.powerfulhimchan.mapexit.core

import kotlinx.serialization.Serializable

@Serializable
enum class TrapPhase {
    PREPARE,
    REVEAL,
    ACTIVE,
    RESULT,
}

@Serializable
data class TrapState(
    val type: Int,
    val pattern: List<Int>,
    val limitMs: Long,
    val revealMs: Long,
    val target: Double,
    val targetWidth: Double,
    val required: Int,
    val signalMs: Long,
    val releaseWindowMs: Long,
    var phase: TrapPhase = TrapPhase.PREPARE,
    var elapsedMs: Long = 0L,
    var progress: Int = 0,
    var held: Int = -1,
    var holdStarted: Long = -1L,
    var rehold: Boolean = false,
    var success: Boolean = false,
    var applied: Boolean = false,
    var result: String = "",
) {
    val title: String
        get() = listOf("순간 입력", "타이밍 바", "연타", "패턴 기억", "누르고 떼기")[type]

    fun position(): Double {
        val v = (elapsedMs % 2000) / 1000.0
        return if (v <= 1) v else 2 - v
    }

    fun signal(): Boolean = holdStarted >= 0 && elapsedMs - holdStarted >= signalMs

    /** Input direction is conveyed by the scene, never by answer text. */
    fun visualSide(): Int? =
        when {
            phase == TrapPhase.PREPARE || phase == TrapPhase.RESULT -> null
            type == 3 && phase == TrapPhase.REVEAL -> {
                val index =
                    ((elapsedMs * pattern.size) / revealMs.coerceAtLeast(1))
                        .toInt()
                        .coerceIn(0, pattern.lastIndex)
                val stepMs = revealMs.toFloat() / pattern.size
                if ((elapsedMs % stepMs.toLong().coerceAtLeast(1)) / stepMs < .8f) pattern[index]
                else null
            }
            type == 3 -> null
            type == 0 -> pattern.getOrNull(progress)
            else -> pattern.firstOrNull()
        }

    fun prompt(): String =
        when {
            phase == TrapPhase.RESULT -> if (success) "회피 성공" else "회피 실패"
            phase == TrapPhase.PREPARE -> cue()
            phase == TrapPhase.REVEAL -> "기억해라"
            else -> listOf("솥을 살피세요", "칼날을 살피세요", "벽을 밀어내세요", "기억한 움직임을 따라 하세요", "나무봉을 살피세요")[type]
        }

    fun cue(): String =
        when (type) {
            0 -> "솥을 피해라"
            1 -> "칼날을 피해라"
            2 -> "벽을 밀어라"
            3 -> "기억해라"
            else -> "봉을 피해라"
        }

    fun instruction(): String = "장면의 움직임과 빛을 살피세요"

    companion object {
        fun button(n: Int) = if (n == 0) "A" else "B"
    }
}

object Traps {
    const val PREPARE_MS = 1000L
    const val RESULT_MS = 900L

    fun create(s: State, type: Int): TrapState {
        require(type in 0..4)
        val charm = if (Equipment.charm(s) == Charm.TRAP) 1.25 else 1.0
        val first = s.nextInt(2)
        val pattern =
            when (type) {
                0 -> listOf(first, 1 - first)
                3 -> List((3 + s.floor / 25).coerceAtMost(5)) { s.nextInt(2) }
                else -> listOf(first)
            }
        return TrapState(
            type,
            pattern,
            (2000 * charm).toLong(),
            1000L,
            .25 + s.random() * .5,
            .20 * charm,
            4 + s.floor / 20,
            450 + s.nextInt(300).toLong(),
            (400 * charm).toLong(),
        )
    }

    private fun finish(t: TrapState, success: Boolean, text: String) {
        if (t.phase == TrapPhase.RESULT) return
        t.success = success
        t.result = text
        t.phase = TrapPhase.RESULT
        t.elapsedMs = 0
    }

    fun tick(t: TrapState, delta: Long) {
        if (t.rehold) return
        t.elapsedMs += delta.coerceAtLeast(0)
        when (t.phase) {
            TrapPhase.PREPARE ->
                if (t.elapsedMs >= PREPARE_MS) {
                    t.elapsedMs = 0
                    t.phase = if (t.type == 3) TrapPhase.REVEAL else TrapPhase.ACTIVE
                    t.held = -1
                }
            TrapPhase.REVEAL ->
                if (t.elapsedMs >= t.revealMs) {
                    t.elapsedMs = 0
                    t.phase = TrapPhase.ACTIVE
                    t.held = -1
                }
            TrapPhase.ACTIVE -> {
                if (
                    t.type == 4 &&
                        t.holdStarted >= 0 &&
                        t.elapsedMs - t.holdStarted > t.signalMs + t.releaseWindowMs
                )
                    finish(t, false, "놓기 신호를 놓쳤습니다")
                else if (t.elapsedMs >= t.limitMs) finish(t, false, "시간 초과")
            }
            else -> Unit
        }
    }

    /** The beam challenge uses two separate taps: engage, then release on its visual signal. */
    fun tap(t: TrapState, button: Int) {
        if (t.phase != TrapPhase.ACTIVE) return
        if (t.type != 4) {
            down(t, button)
            up(t, button)
            return
        }
        if (t.rehold) {
            t.rehold = false
            t.held = -1
            return
        }
        if (button != t.pattern[0]) {
            finish(t, false, "다른 동작을 선택했습니다")
            return
        }
        if (t.holdStarted < 0) {
            t.holdStarted = t.elapsedMs
            t.held = -1
            return
        }
        val elapsed = t.elapsedMs - t.holdStarted
        finish(
            t,
            elapsed in t.signalMs..t.signalMs + t.releaseWindowMs,
            if (elapsed in t.signalMs..t.signalMs + t.releaseWindowMs) "신호에 맞춰 회피!"
            else "너무 일찍 놓았습니다",
        )
    }

    fun down(t: TrapState, button: Int) {
        if (t.phase != TrapPhase.ACTIVE || t.held != -1) return
        if (t.rehold) {
            if (button == t.pattern[0]) {
                t.held = button
                t.rehold = false
            }
            return
        }
        t.held = button
        when (t.type) {
            0,
            3 -> {
                if (button != t.pattern[t.progress]) finish(t, false, "잘못된 순서! 회피 실패")
                else {
                    t.progress++
                    if (t.progress == t.pattern.size) finish(t, true, "회피 성공!")
                }
            }
            1 -> {
                val ok =
                    button == t.pattern[0] &&
                        kotlin.math.abs(t.position() - t.target) <= t.targetWidth / 2
                finish(t, ok, if (ok) "정확한 타이밍!" else "목표 구간을 놓쳤습니다")
            }
            2 -> Unit
            4 -> {
                if (button != t.pattern[0]) finish(t, false, "다른 버튼을 눌렀습니다")
                else t.holdStarted = t.elapsedMs
            }
        }
    }

    fun up(t: TrapState, button: Int) {
        if (t.phase != TrapPhase.ACTIVE || t.held != button) return
        t.held = -1
        when (t.type) {
            2 ->
                if (button == t.pattern[0]) {
                    t.progress++
                    if (t.progress >= t.required) finish(t, true, "연타 성공!")
                }
            4 -> {
                val time = t.elapsedMs - t.holdStarted
                val ok = time in t.signalMs..t.signalMs + t.releaseWindowMs
                finish(t, ok, if (ok) "신호에 맞춰 회피!" else "너무 일찍 놓았습니다")
            }
        }
    }

    fun pause(t: TrapState) {
        if (
            t.type == 4 &&
                t.phase == TrapPhase.ACTIVE &&
                t.holdStarted >= 0 &&
                t.held == t.pattern[0]
        )
            t.rehold = true
        t.held = -1
    }
}
