package com.powerfulhimchan.mapexit.core

import kotlin.math.PI
import kotlin.math.sin

data class AttackPose(
    val advance: Float,
    val lift: Float,
    val lean: Float,
    val angle: Float,
    val reach: Float,
    val impact: Float,
)

/** Shared strike timing for the actor, held weapon, victim reaction and damage popup. */
object BattleAnimation {
    const val DURATION_MS = 760L
    const val HIT_MS = 320L

    private fun ease(t: Float) = t.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }

    private fun blend(a: Float, b: Float, t: Float) = a + (b - a) * ease(t)

    fun pose(kind: Kind?, elapsed: Long, strong: Boolean = false): AttackPose {
        if (elapsed !in 0 until DURATION_MS) return AttackPose(0f, 0f, 0f, 25f, 0f, 0f)
        val t = elapsed.toFloat() / DURATION_MS
        val advance =
            when {
                t < .18f -> blend(0f, -.08f, t / .18f)
                t < .43f -> blend(-.08f, 1f, (t - .18f) / .25f)
                t < .60f -> 1f
                else -> blend(1f, 0f, (t - .60f) / .40f)
            }
        val windup =
            when (kind) {
                Kind.HAMMER -> -120f
                Kind.WHIP -> -110f
                Kind.SPEAR -> 5f
                Kind.CLAW -> -45f
                else -> -65f
            }
        val contact =
            when (kind) {
                Kind.SPEAR -> 60f
                Kind.HAMMER -> 135f
                Kind.CLAW -> 110f
                Kind.WHIP -> 100f
                else -> 120f
            }
        val angle =
            when {
                t < .20f -> blend(25f, windup, t / .20f)
                t < .46f -> blend(windup, contact, (t - .20f) / .26f)
                t < .62f && kind == Kind.CLAW ->
                    contact - sin((t - .46f) / .16f * PI).toFloat() * 100f
                t < .62f -> contact
                else -> blend(contact, 25f, (t - .62f) / .38f)
            }
        val impact = (1f - kotlin.math.abs(elapsed - HIT_MS).toFloat() / 170f).coerceIn(0f, 1f)
        return AttackPose(
            advance * if (strong) 1.15f else 1f,
            if (kind == Kind.HAMMER) sin(t * PI).toFloat() * .12f else 0f,
            advance * if (kind == Kind.HAMMER) 12f else 7f,
            angle,
            advance * if (kind == Kind.SPEAR) 1.4f else 1f,
            impact,
        )
    }
}
