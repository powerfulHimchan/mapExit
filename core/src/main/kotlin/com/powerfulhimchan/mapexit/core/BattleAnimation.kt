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

    data class DefensePose(val retreat: Float, val crouch: Float, val lean: Float, val guard: Float)

    fun defense(action: String, elapsed: Long): DefensePose {
        if (elapsed !in 0 until DURATION_MS) return DefensePose(0f, 0f, 0f, 0f)
        val envelope =
            if (elapsed < 180) ease(elapsed / 180f)
            else if (elapsed < 420) 1f else 1f - ease((elapsed - 420) / 340f)
        return if (action == "dodge") DefensePose(envelope, envelope, -20f * envelope, 0f)
        else if (action == "guard")
            DefensePose(.05f * envelope, .15f * envelope, -5f * envelope, envelope)
        else DefensePose(0f, 0f, 0f, 0f)
    }

    fun pose(kind: Kind?, elapsed: Long, strong: Boolean = false): AttackPose {
        if (elapsed !in 0 until DURATION_MS) return AttackPose(0f, 0f, 0f, 25f, 0f, 0f)
        val t = elapsed.toFloat() / DURATION_MS
        if (strong) {
            val drawBack =
                if (elapsed < 210) ease(elapsed / 210f) else 1f - ease((elapsed - 210) / 110f)
            val strike =
                when {
                    elapsed < 210 -> 0f
                    elapsed < HIT_MS -> ease((elapsed - 210) / 110f)
                    elapsed < 450 -> 1f
                    else -> 1f - ease((elapsed - 450) / 310f)
                }
            val normal = pose(kind, elapsed, false)
            val contact = pose(kind, HIT_MS, false)
            return normal.copy(
                advance = strike * 1.15f - drawBack * .22f,
                lift = if (kind == Kind.HAMMER) drawBack * .16f else strike * .035f,
                lean = -drawBack * 16f + strike * 22f,
                angle =
                    25f - drawBack * (if (kind == Kind.SPEAR) 18f else 125f) +
                        strike * (contact.angle - 25f),
                reach = strike * (if (kind == Kind.SPEAR) 1.65f else 1.2f) - drawBack * .15f,
            )
        }
        val advance =
            when {
                t < .18f -> blend(0f, -.08f, t / .18f)
                t < .43f -> blend(-.08f, 1f, (t - .18f) / .25f)
                t < .60f -> 1f
                else -> blend(1f, 0f, (t - .60f) / .40f)
            }
        val windup =
            when (kind) {
                Kind.HAMMER -> -45f
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
