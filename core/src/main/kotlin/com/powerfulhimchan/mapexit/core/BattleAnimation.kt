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
    val armLift: Float = 0f,
    val stride: Float = 0f,
)

/** Shared strike timing for the actor, held weapon, victim reaction and damage popup. */
object BattleAnimation {
    const val DURATION_MS = 760L
    const val HIT_MS = 320L

    fun smooth(t: Float): Float = t.coerceIn(0f,1f).let { (it*it*it*(it*(it*6-15)+10)).coerceIn(0f,1f) }
    private fun ease(t: Float) = smooth(t)
    fun motionTime(elapsed: Long, connected: Boolean): Long {
        if(!connected || elapsed < HIT_MS) return elapsed
        if(elapsed < HIT_MS+32) return HIT_MS
        return HIT_MS+((elapsed-HIT_MS-32)*(DURATION_MS-HIT_MS)/(DURATION_MS-HIT_MS-32))
    }
    fun reaction(elapsed: Long): Float {
        val age=elapsed-HIT_MS
        if(age<0 || age>=300) return 0f
        return sin(age/300f*PI).toFloat()*(1-age/300f)
    }

    data class DefensePose(val retreat: Float, val crouch: Float, val lean: Float, val guard: Float)

    data class TellPose(
        val lean: Float,
        val bob: Float,
        val squash: Float,
        val weaponAngle: Float,
        val guard: Float,
        val armLift: Float = 0f,
    )

    fun tell(intent: EnemyIntent, clock: Long): TellPose {
        val breath = sin((clock % 2400) * PI / 1200).toFloat()
        return when (intent) {
            EnemyIntent.HEAVY -> TellPose(-14f, breath * .015f, .16f, -35f, 0f, 16f)
            EnemyIntent.FLURRY ->
                TellPose(-8f, sin((clock % 600) * PI / 300).toFloat() * .025f, .03f, -55f, 0f)
            EnemyIntent.GUARD -> TellPose(5f, breath * .005f, 0f, -10f, 1f)
            EnemyIntent.RECOVER -> TellPose(18f, breath * .01f, .10f, 35f, 0f)
            EnemyIntent.STRIKE -> TellPose(0f, breath * .008f, 0f, -25f, 0f)
        }
    }

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
        val rest=AttackPose(0f,0f,0f,25f,0f,0f)
        if(elapsed !in 0 until DURATION_MS) return rest
        val power=if(strong)1.15f else 1f
        val windEnd=if(strong)210L else 165L
        val contactAngle=when(kind) { Kind.SPEAR->60f;Kind.HAMMER->135f;Kind.CLAW->110f;Kind.WHIP->100f;else->120f }
        val windAngle=when(kind) { Kind.HAMMER->-55f;Kind.WHIP->-110f;Kind.SPEAR->5f;Kind.CLAW->-40f;else->-70f }-(if(strong && kind!=Kind.SPEAR)20f else 0f)
        val wind=AttackPose(if(strong)-.22f else -.08f,if(kind==Kind.HAMMER).10f else .012f,if(strong)-18f else -7f,windAngle,-.10f,0f,if(kind==Kind.HAMMER)17f else if(kind==Kind.WHIP)9f else 4f,-.25f)
        val contact=AttackPose(power,if(kind==Kind.HAMMER).025f else .008f,(if(kind==Kind.HAMMER)14f else 10f)*power,contactAngle,(if(kind==Kind.SPEAR)1.4f else 1f)*power,0f,if(kind==Kind.HAMMER)-3f else 0f,.7f)
        val follow=contact.copy(advance=power*.92f,lift=0f,lean=contact.lean*.75f,angle=contactAngle+if(kind==Kind.SPEAR)0f else 12f,armLift=-2f,stride=.45f)
        val followAt=if(kind==Kind.HAMMER)430L else 405L
        val (from,to,t)=when {
            elapsed<windEnd -> Triple(rest,wind,elapsed.toFloat()/windEnd)
            elapsed<HIT_MS -> Triple(wind,contact,(elapsed-windEnd).toFloat()/(HIT_MS-windEnd))
            elapsed<followAt -> Triple(contact,follow,(elapsed-HIT_MS).toFloat()/(followAt-HIT_MS))
            else -> Triple(follow,rest,(elapsed-followAt).toFloat()/(DURATION_MS-followAt))
        }
        val f=smooth(t)
        fun mix(a:Float,b:Float)=a+(b-a)*f
        val impact=if(elapsed<HIT_MS)0f else (1f-(elapsed-HIT_MS)/190f).coerceIn(0f,1f)
        return AttackPose(mix(from.advance,to.advance),mix(from.lift,to.lift),mix(from.lean,to.lean),mix(from.angle,to.angle),mix(from.reach,to.reach),impact,mix(from.armLift,to.armLift),mix(from.stride,to.stride))
    }
}
