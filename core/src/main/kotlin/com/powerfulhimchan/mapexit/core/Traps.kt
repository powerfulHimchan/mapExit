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
    var action: Int = -1,
    var actionAtMs: Long = 0L,
) {
    val title: String
        get() = listOf("순간 입력", "바닥 칼날", "연타", "패턴 기억", "천장 나무봉")[type]

    val physicalAction: Boolean
        get() = type == 1 || type == 4

    /** The rendered blade height is also the collision rule. A fully retracted blade is safe. */
    fun bladeExtension(): Float {
        val time = if (phase == TrapPhase.RESULT) actionAtMs else elapsedMs
        val p = (time.toFloat() / limitMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        return when {
            p < .15f -> 1f
            p < .35f -> (.35f - p) / .20f
            p <= .65f -> 0f
            p < .85f -> (p - .65f) / .20f
            else -> 1f
        }.coerceIn(0f, 1f)
    }

    fun bladesRetracted(): Boolean = bladeExtension() <= .001f

    /** The beam falls by itself; crouching early remains safe until it has passed. */
    fun beamDescent(): Float {
        val time =
            if (phase == TrapPhase.RESULT)
                actionAtMs + (limitMs - actionAtMs) * (elapsedMs / 650f).coerceIn(0f, 1f)
            else elapsedMs.toFloat()
        val p = (time / limitMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        return p * p
    }

    /** Input direction is conveyed by the scene, never by answer text. */
    fun visualSide(): Int? =
        when {
            phase == TrapPhase.PREPARE || phase == TrapPhase.RESULT -> null
            physicalAction -> null
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

}

object Traps {
    const val PREPARE_MS = 1000L
    const val RESULT_MS = 900L

    fun create(s: State, type: Int): TrapState {
        require(type in 0..4)
        val charm = if (Equipment.charm(s) == Charm.TRAP) 1.25 else 1.0
        val depth = s.floor.coerceIn(1, 50) - 1
        val first = s.nextInt(2)
        val pattern =
            when (type) {
                0 -> List(2 + depth / 20) { if (it % 2 == 0) first else 1 - first }
                3 -> List(3 + depth / 20) { s.nextInt(2) }
                else -> listOf(first)
            }
        return TrapState(
            type,
            pattern,
            ((2000 - depth * 10) * charm).toLong(),
            if (type == 3)
                kotlin.math.round(pattern.size * (1000.0 / 3 - depth * .6) * charm).toLong()
            else 1000L,
            .25 + s.random() * .5,
            .20 * charm,
            4 + depth / 20,
            450 + s.nextInt(300).toLong(),
            (400 * charm).toLong(),
        )
    }

    private fun finish(t: TrapState, success: Boolean, text: String) {
        if (t.phase == TrapPhase.RESULT) return
        t.actionAtMs = t.elapsedMs
        t.success = success
        t.result = text
        t.phase = TrapPhase.RESULT
        t.elapsedMs = 0
    }

    fun tick(t: TrapState, delta: Long) {
        if (t.rehold && !t.physicalAction) return
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
                if (t.elapsedMs >= t.limitMs) finish(t, false, "시간 초과")
            }
            else -> Unit
        }
    }

    /** Jump/crouch have fixed meanings. Neither challenge asks for a random direction or hold. */
    fun tap(t: TrapState, button: Int) {
        if (t.phase != TrapPhase.ACTIVE) return
        if (!t.physicalAction) {
            down(t, button)
            up(t, button)
            return
        }
        t.action = button
        val ok = if (t.type == 1) button == 0 && t.bladesRetracted() else button == 1
        finish(t, ok, if (ok) "회피 성공!" else "회피 실패")
    }

    fun down(t: TrapState, button: Int) {
        if (t.physicalAction) {
            tap(t, button)
            return
        }
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
                if (button != t.pattern[t.progress]) {
                    t.action = button
                    finish(t, false, "잘못된 순서! 회피 실패")
                }
                else {
                    t.progress++
                    if (t.progress == t.pattern.size) {
                        t.action = button
                        finish(t, true, "회피 성공!")
                    }
                }
            }
            2 -> Unit
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
        }
    }

    fun pause(t: TrapState) {
        t.rehold = false
        t.held = -1
    }
}
