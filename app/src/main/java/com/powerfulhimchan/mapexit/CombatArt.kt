package com.powerfulhimchan.mapexit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.*

private fun DrawScope.arm(a: Offset, b: Offset, unit: Float, opacity: Float = 1f) {
    val elbow = (a+b)*.5f+Offset(-unit*1.5f,unit*3)
    drawLine(Color(0xff273a52).copy(alpha=opacity.coerceIn(0f,1f)), a, elbow, unit * 5)
    drawLine(Color(0xff4b6477).copy(alpha=opacity.coerceIn(0f,1f)), a, elbow, unit * 3)
    drawLine(Color(0xffc4a285).copy(alpha=opacity.coerceIn(0f,1f)), elbow, b, unit * 3)
    pixelRect(Color(0xffead1ae).copy(alpha=opacity.coerceIn(0f,1f)), b.x - unit, b.y - unit, unit * 3, unit * 3)
}

private fun DrawScope.heldWeapon(kind: Kind, hand: Offset, unit: Float, angle: Float, opacity: Float = 1f) {
    val scale = if (kind == Kind.SPEAR) (unit * .8f).toInt().coerceAtLeast(1).toFloat() else unit
    val gripX = if (kind == Kind.WHIP) 7 else 16
    val gripY = if (kind == Kind.SPEAR) 40 else if (kind == Kind.WHIP) 25 else 27
    withTransform({ rotate(angle, hand) }) {
        sprite(gearSprite(kind), hand.x - gripX * scale, hand.y - gripY * scale, 32 * scale, opacity=opacity)
    }
}

internal fun DrawScope.drawAnimatedBattle(s: State, clock: Long, visual: BattleVisual?) {
    val age=visual?.let { clock-it.startedAt-BattleAnimation.HIT_MS } ?: -1L
    val connected=visual?.impact?.outcome !in listOf(ImpactOutcome.MISS,ImpactOutcome.DODGE)
    val strength=if(visual?.action=="strong" || visual?.impact?.critical==true)2.5f else 1.3f
    val shake=if(connected && age in 0..220) sin(age*.18).toFloat()*(1-age/220f)*strength else 0f
    withTransform({ translate(shake,shake*.4f);scale(1+abs(shake)*.003f,1+abs(shake)*.003f,center) }) {
        drawBattleActors(s,clock,visual)
    }
}

private fun DrawScope.drawBattleActors(s: State, clock: Long, visual: BattleVisual?) {
    val motion = visual?.takeIf { it.active(clock) }
    val scene = motion?.scene ?: s
    val enemy = scene.enemy ?: s.enemy ?: s.battleResult?.enemy ?: return
    val elapsed =
        motion?.let { (clock - it.startedAt).coerceAtLeast(0) } ?: BattleAnimation.DURATION_MS
    val attacking = motion?.impact?.target == ImpactTarget.ENEMY
    val defending = motion != null && !attacking
    val kind = Equipment.equipped(scene).firstOrNull { it.weapon }?.kind
    val connected=motion?.impact?.outcome !in listOf(ImpactOutcome.MISS,ImpactOutcome.DODGE)
    val poseTime=BattleAnimation.motionTime(elapsed,connected)
    val pose = BattleAnimation.pose(kind, poseTime, motion?.action == "strong")
    val monsterPose =
        BattleAnimation.pose(
            if (enemy.boss) Kind.HAMMER else Kind.SWORD,
            poseTime,
            enemy.intent == EnemyIntent.HEAVY,
        )
    val tellWeight=if(!defending)1f else 1-BattleAnimation.smooth(elapsed/120f)+BattleAnimation.smooth((elapsed-600)/160f)
    val tellEnemy=if(defending && elapsed>500)s.enemy ?: enemy else enemy
    val tell = BattleAnimation.tell(tellEnemy.intent, clock)
    val showingTell = s.mode in listOf(Mode.COMBAT_ATTACK, Mode.COMBAT_DEFEND) || motion!=null
    val playerDamage = defending && motion!!.impact.amount > 0
    val enemyDamage = attacking && motion!!.impact.outcome != ImpactOutcome.MISS
    val w = size.width
    val h = size.height
    arena(clock, floor = s.floor)
    val heroRecoil = if (playerDamage) BattleAnimation.reaction(elapsed) else 0f
    val defense = BattleAnimation.defense(if (defending) motion!!.action else "", elapsed)
    val dodge = defense.retreat
    val heroWidth = min(w * .34f, h * .32f)
    val advanceDistance=w*.17f+(5-spriteUnit(heroWidth)).coerceAtLeast(0f)*10
    val heroX =
        w * .18f + (if (attacking) pose.advance * advanceDistance else 0f) -
            heroRecoil * 15 -
            dodge * w * .11f
    val heroBase =
        h * .90f - (if (attacking) pose.advance * h * .11f + pose.lift * h else 0f) +
            defense.crouch * h * .045f + if(motion==null)sin(clock*.0026f)*spriteUnit(min(w*.34f,h*.32f))*.4f else 0f
    contactShadow(heroX,h*.90f+3,32*spriteUnit(heroWidth))
    var hero = HeroPose(heroX, heroBase, spriteUnit(heroWidth))
    val flash =
        if (playerDamage && elapsed in BattleAnimation.HIT_MS..BattleAnimation.HIT_MS + 100) red.copy(alpha=.9f*(1-(elapsed-BattleAnimation.HIT_MS)/100f))
        else null
    withTransform({
        rotate(
            if (attacking) pose.lean else defense.lean - heroRecoil * 10,
            Offset(heroX + heroWidth / 2, heroBase),
        )
    }) {
        hero =
            adventurer(
                scene,
                heroX,
                heroBase,
                heroWidth,
                armed = false,
                hitTint = flash,
                articulated = true,
                shadow = false,
                stride = if(attacking)pose.stride else -defense.retreat*.5f,
            )
        val shoulder = Offset(hero.x + hero.unit * 23, hero.y + hero.unit * 23)
        val overhead = if(attacking)pose.armLift else sin(clock*.0026f)*.4f
        val hand =
            shoulder +
                Offset(
                    hero.unit *
                        (4 +
                            if (attacking) pose.reach * (if (kind == Kind.SPEAR) 3 else 7)
                            else defense.guard * 8),
                    hero.unit *
                        (10 - (if (attacking) pose.advance * 5 else defense.guard * 17) - overhead),
                )
        if(attacking && kind!=null) weaponTrail(kind,hand,hero.unit,elapsed,motion?.action=="strong")
        arm(shoulder, hand, hero.unit)
        if (kind != null) {
            if (attacking && kind == Kind.WHIP && elapsed in 150..570) {
                val target = Offset(w * .71f, h * .60f)
                var previous = hand
                for (i in 1..28) {
                    val t = i / 28f
                    val end =
                        hand +
                            (target - hand) * (t * pose.reach.coerceIn(.15f, 1f)) +
                            Offset(
                                0f,
                                sin(t * PI * 2 + elapsed * .018).toFloat() *
                                    30 *
                                    sin(t * PI).toFloat(),
                            )
                    drawLine(amber, previous, end, 3f)
                    previous = end
                }
                pixelRect(Color(0xff8b6041), hand.x - 3, hand.y - 2, 8f, 14f)
            } else
                heldWeapon(
                    kind,
                    hand,
                    hero.unit,
                    if (attacking) pose.angle else 25f + defense.guard * 65f,
                )
        }
        if (Equipment.equipped(scene).any { it.kind == Kind.SHIELD }) {
            val guard = defense.guard
            sprite(
                "shield",
                hero.x - hero.unit * 4 + guard * hero.unit * 17,
                hero.y + hero.unit * 24 - guard * hero.unit * 4,
                hero.unit * 24,
            )
        }
    }
    if (defending && motion!!.action == "dodge") {
        for (i in 1..3) drawLine(
            mint.copy(alpha = .22f * defense.retreat),
            Offset(heroX + heroWidth + i * 8, heroBase - heroWidth * .5f + i * 9),
            Offset(heroX + heroWidth + 25 + i * 8, heroBase - heroWidth * .5f + i * 9),
            2f,
        )
    }
    if (attacking && motion!!.action == "strong") {
        drawCircle(
            amber.copy(alpha = .35f * pose.impact),
            heroWidth * .7f,
            Offset(heroX + heroWidth * .7f, heroBase - heroWidth * .6f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(4f),
        )
    }
    val type =
        when {
            enemy.boss -> "boss"
            enemy.name.contains("기사") -> "knight"
            enemy.name.contains("박쥐") -> "bat"
            else -> "slime"
        }
    val enemyWidth = min(w * (if (enemy.boss) .34f else .28f), h * (if (enemy.boss) .26f else .20f))
    val unit = spriteUnit(enemyWidth)
    val advance = if (defending) monsterPose.advance else 0f
    val recoil = if (enemyDamage) BattleAnimation.reaction(elapsed) else 0f
    val enemyHome=min(w*.64f,w-32*unit-50)
    val ex=(enemyHome-advance*w*.30f).coerceAtMost(w-32*unit-(if(enemy.boss)50 else 28))+recoil*16
    val jump =
        if (defending && type in listOf("slime", "boss") && elapsed <= BattleAnimation.HIT_MS)
            sin(elapsed.toFloat() / BattleAnimation.HIT_MS * PI).toFloat() * h * .10f
        else 0f
    val baseline =
        h * (if (type == "bat") .57f else .73f) + advance * h * .14f - jump +
            if (showingTell) tell.bob * h * tellWeight else 0f
    val ey = baseline - spriteHeight(type, enemyWidth)
    val death=if(s.mode==Mode.VICTORY) if(motion==null)1f else BattleAnimation.smooth((elapsed-BattleAnimation.HIT_MS-80)/360f) else 0f
    val opacity=1-death
    val dead=death>=1f
    contactShadow(ex, h * .74f + advance * h * .14f, unit * 32)
    if (!dead) {
        val tint =
            if (enemyDamage && elapsed in BattleAnimation.HIT_MS..BattleAnimation.HIT_MS + 100)
                (if (motion!!.impact.critical) Color.White else amber).copy(alpha=.9f*(1-(elapsed-BattleAnimation.HIT_MS)/100f))
            else null
        withTransform({
            val pivot = Offset(ex + unit * 16, baseline)
            rotate(
                if (defending) (-monsterPose.lean).coerceAtMost(if(enemy.boss)8f else 18f)+tell.lean*tellWeight else if (showingTell) tell.lean+recoil*12 else recoil * 12,
                pivot,
            )
            scale(1f,1-death*.65f,pivot)
            if (showingTell && type == "slime") scale(1f + tell.squash*tellWeight, 1f - tell.squash*tellWeight, pivot)
            if (showingTell && type == "bat")
                scale(
                    if (enemy.intent == EnemyIntent.HEAVY) .78f else 1f + abs(tell.bob) * 5,
                    1f,
                    pivot,
                )
            if (type == "slime" && defending)
                scale(1f + monsterPose.impact * .2f, 1f - monsterPose.impact * .18f, pivot)
            if (type == "bat" && defending)
                scale(1f + sin(elapsed * .03).toFloat() * .16f, 1f, pivot)
        }) {
            if(type=="bat") batBody(ex,ey,enemyWidth,clock,tint,opacity)
            else combatBody(type,ex,ey,enemyWidth,tint,type in listOf("knight","boss"),if(defending)monsterPose.stride else sin(clock*.0026f)*.06f,opacity)

            if (showingTell && tell.guard > 0f && type in listOf("knight", "boss"))
                sprite("shield", ex - unit * 2, ey + unit * 17, unit * 25, opacity=opacity*tellWeight)
            if (type in listOf("knight", "boss")) {
                val hand =
                    Offset(
                        ex + unit * 8 - advance * unit * 4,
                        ey + unit * (28 - if (showingTell) tell.armLift*tellWeight else 0f),
                    )
                if(defending) weaponTrail(if(type=="boss")Kind.HAMMER else Kind.SWORD,hand,unit,elapsed,enemy.intent==EnemyIntent.HEAVY,-1f)
                arm(Offset(ex + unit * 12, ey + unit * 22), hand, unit,opacity)
                heldWeapon(
                    if (type == "boss") Kind.HAMMER else Kind.SWORD,
                    hand,
                    unit,
                    if (defending) -monsterPose.angle
                    else if (showingTell) tell.weaponAngle else -25f,
                    opacity=opacity,
                )
            }
        }
    } else {
        pixelRect(Color(0xff3c524a), ex, baseline - 5, unit * 32, 5f)
        sprite("coin", ex + unit * 12, baseline - 35, 32f)
    }
    if (defending && enemy.intent == EnemyIntent.FLURRY) {
        for (i in 0..2) drawLine(
            amber.copy(alpha = monsterPose.impact * .8f),
            Offset(heroX + heroWidth * .8f, heroBase - heroWidth * .65f + i * 12),
            Offset(heroX + heroWidth * 1.2f, heroBase - heroWidth * .45f + i * 12),
            3f,
        )
    }
    if (showingTell && enemy.opening) {
        for (i in 0..2) pixelRect(mint, ex + unit * (10 + i * 6), ey - unit * 5, unit * 2, unit * 3)
    }
    if(enemy.elite && !dead) {
        for(i in 0..2) pixelRect(amber.copy(alpha=opacity),ex+unit*(9+i*6),ey-unit*(if(i==1)8 else 6),unit*3,unit*4)
        pixelRect(amber.copy(alpha=opacity),ex+unit*8,ey-unit*3,unit*18,unit*2)
    }
    if(death>.45f) sprite("coin",ex+unit*12,baseline-35,32f,opacity=BattleAnimation.smooth((death-.45f)/.55f))
    if(enemyDamage) impactParticles(Offset(ex+unit*16,ey+spriteHeight(type,enemyWidth)*.55f),elapsed,motion!!.impact,motion.action=="strong",baseline)
    if(playerDamage) impactParticles(Offset(hero.x+hero.unit*23,hero.y+hero.unit*25),elapsed,motion!!.impact,enemy.intent==EnemyIntent.HEAVY,heroBase)
    if (defending && motion!!.action == "guard") {
        for (i in 0..4) pixelRect(
            mint,
            hero.x + hero.unit * 29,
            hero.y + hero.unit * 20 + i * 9,
            3f,
            5f,
        )
    }
    if (attacking && kind == Kind.CLAW && elapsed in 250..510) {
        for (i in 0..2) drawLine(
            pale.copy(alpha=((elapsed-250)/70f).coerceIn(0f,1f)*(1-(elapsed-320)/190f).coerceIn(0f,1f)),
            Offset(ex + i * 12, ey + unit * 8),
            Offset(ex + i * 12 + 26, ey + unit * 21),
            3f,
        )
    }
}
