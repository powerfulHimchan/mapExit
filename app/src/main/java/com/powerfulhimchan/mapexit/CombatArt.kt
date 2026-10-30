package com.powerfulhimchan.mapexit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.*

private fun DrawScope.arm(a: Offset, b: Offset, unit: Float) {
    val elbow = Offset((a.x + b.x) * .5f, max(a.y, b.y) + unit * 2)
    drawLine(Color(0xff273a52), a, elbow, unit * 5)
    drawLine(Color(0xff4b6477), a, elbow, unit * 3)
    drawLine(Color(0xffc4a285), elbow, b, unit * 3)
    pixelRect(Color(0xffead1ae), b.x - unit, b.y - unit, unit * 3, unit * 3)
}

private fun DrawScope.heldWeapon(kind: Kind, hand: Offset, unit: Float, angle: Float) {
    val scale = if (kind == Kind.SPEAR) (unit * .8f).toInt().coerceAtLeast(1).toFloat() else unit
    val gripX = if (kind == Kind.WHIP) 7 else 16
    val gripY = if (kind == Kind.SPEAR) 40 else if (kind == Kind.WHIP) 25 else 27
    withTransform({ rotate(angle, hand) }) {
        sprite(gearSprite(kind), hand.x - gripX * scale, hand.y - gripY * scale, 32 * scale)
    }
}

private fun DrawScope.burst(
    at: Offset,
    strength: Float,
    critical: Boolean,
    player: Boolean = false,
) {
    if (strength <= .05f) return
    val color = if (player) red else if (critical) amber else pale
    val rays = if (critical) 12 else 6
    for (i in 0 until rays) {
        val angle = i * PI * 2 / rays
        val inner = 5f
        val outer = (if (critical) 36f else 20f) * strength
        drawLine(
            color,
            at + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * inner,
            at + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * outer,
            if (critical) 4f else 2f,
        )
    }
    pixelRect(Color.White, at.x - 3, at.y - 3, 6f, 6f)
}

internal fun DrawScope.drawAnimatedBattle(s: State, clock: Long, visual: BattleVisual?) {
    val motion = visual?.takeIf { it.active(clock) }
    val scene = motion?.scene ?: s
    val enemy = scene.enemy ?: s.enemy ?: s.battleResult?.enemy ?: return
    val elapsed =
        motion?.let { (clock - it.startedAt).coerceAtLeast(0) } ?: BattleAnimation.DURATION_MS
    val attacking = motion?.impact?.target == ImpactTarget.ENEMY
    val defending = motion != null && !attacking
    val kind = Equipment.equipped(scene).firstOrNull { it.weapon }?.kind
    val pose = BattleAnimation.pose(kind, elapsed, motion?.action == "strong")
    val monsterPose =
        BattleAnimation.pose(
            if (enemy.boss) Kind.HAMMER else Kind.SWORD,
            elapsed,
            enemy.intent == EnemyIntent.HEAVY,
        )
    val tell = BattleAnimation.tell(enemy.intent, clock)
    val showingTell = motion == null && s.mode in listOf(Mode.COMBAT_ATTACK, Mode.COMBAT_DEFEND)
    val playerDamage = defending && motion!!.impact.amount > 0
    val enemyDamage = attacking && motion!!.impact.outcome != ImpactOutcome.MISS
    val w = size.width
    val h = size.height
    arena(clock)
    val heroRecoil = if (playerDamage) monsterPose.impact else 0f
    val defense = BattleAnimation.defense(if (defending) motion!!.action else "", elapsed)
    val dodge = defense.retreat
    val heroX =
        w * .18f + (if (attacking) pose.advance * w * .17f else 0f) -
            heroRecoil * 15 -
            dodge * w * .11f
    val heroBase =
        h * .90f - (if (attacking) pose.advance * h * .11f + pose.lift * h else 0f) +
            defense.crouch * h * .045f
    val heroWidth = min(w * .34f, h * .32f)
    var hero = HeroPose(heroX, heroBase, spriteUnit(heroWidth))
    val flash =
        if (playerDamage && elapsed in BattleAnimation.HIT_MS..BattleAnimation.HIT_MS + 140) red
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
            )
        val shoulder = Offset(hero.x + hero.unit * 23, hero.y + hero.unit * 23)
        val overhead =
            if (attacking && kind == Kind.HAMMER && elapsed <= BattleAnimation.HIT_MS)
                sin(elapsed.toFloat() / BattleAnimation.HIT_MS * PI).toFloat() * 14f
            else 0f
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
        arm(shoulder, hand, hero.unit)
        if (kind != null) {
            if (attacking && kind == Kind.WHIP && elapsed in 150..570) {
                val target = Offset(w * .71f, h * .60f)
                var previous = hand
                for (i in 1..20) {
                    val t = i / 20f
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
    val recoil = if (enemyDamage) pose.impact else 0f
    val ex = w * .64f - advance * w * .30f + recoil * 16
    val jump =
        if (defending && type in listOf("slime", "boss") && elapsed <= BattleAnimation.HIT_MS)
            sin(elapsed.toFloat() / BattleAnimation.HIT_MS * PI).toFloat() * h * .10f
        else 0f
    val baseline =
        h * (if (type == "bat") .57f else .73f) + advance * h * .14f - jump +
            if (showingTell) tell.bob * h else 0f
    val ey = baseline - spriteHeight(type, enemyWidth)
    val dead = s.mode == Mode.VICTORY && (motion == null || elapsed >= 590)
    contactShadow(ex, h * .74f + advance * h * .14f, unit * 32)
    if (!dead) {
        val tint =
            if (enemyDamage && elapsed in BattleAnimation.HIT_MS..BattleAnimation.HIT_MS + 140)
                if (motion!!.impact.critical) Color.White else amber
            else null
        withTransform({
            val pivot = Offset(ex + unit * 16, baseline)
            rotate(
                if (defending) -monsterPose.lean else if (showingTell) tell.lean else recoil * 12,
                pivot,
            )
            if (showingTell && type == "slime") scale(1f + tell.squash, 1f - tell.squash, pivot)
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
            sprite(
                type,
                ex,
                ey,
                enemyWidth,
                flash = tint,
                articulated = type in listOf("knight", "boss"),
            )
            if (showingTell && tell.guard > 0f && type in listOf("knight", "boss"))
                sprite("shield", ex - unit * 2, ey + unit * 17, unit * 25)
            if (type in listOf("knight", "boss")) {
                val hand =
                    Offset(
                        ex + unit * 8 - advance * unit * 4,
                        ey + unit * (28 - if (showingTell) tell.armLift else 0f),
                    )
                arm(Offset(ex + unit * 12, ey + unit * 22), hand, unit)
                heldWeapon(
                    if (type == "boss") Kind.HAMMER else Kind.SWORD,
                    hand,
                    unit,
                    if (defending) -monsterPose.angle
                    else if (showingTell) tell.weaponAngle else -25f,
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
    if (enemyDamage)
        burst(
            Offset(ex + unit * 16, ey + spriteHeight(type, enemyWidth) * .55f),
            pose.impact,
            motion!!.impact.critical,
        )
    if (playerDamage)
        burst(
            Offset(hero.x + hero.unit * 23, hero.y + hero.unit * 25),
            monsterPose.impact,
            motion!!.impact.critical,
            player = true,
        )
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
            pale,
            Offset(ex + i * 12, ey + unit * 8),
            Offset(ex + i * 12 + 26, ey + unit * 21),
            3f,
        )
    }
}
