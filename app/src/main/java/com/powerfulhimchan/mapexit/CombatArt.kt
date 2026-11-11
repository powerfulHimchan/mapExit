package com.powerfulhimchan.mapexit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.*

/** Projected rear-view joints: depth shortens the forearm without turning elbows inward. */
internal object RearArmRig {
    data class Arm(val joints: ArmKinematics.Pose,val hand: Offset,val gloveAngle: Float)
    private fun point(p: Offset)=ArmKinematics.Point(p.x,p.y)
    private fun offset(p: ArmKinematics.Point)=Offset(p.x,p.y)
    fun anchored(shoulder: Offset,elbow: Offset,hand: Offset,unit: Float): Arm {
        val delta=hand-elbow
        val distance=delta.getDistance()
        val axis=if(distance>.0001f)delta/distance else Offset(0f,1f)
        val wrist=hand-axis*min(unit*1.25f,distance*.45f)
        // The source glove cuff points down at zero degrees. Rotate its cuff towards
        // the elbow, independently of the blade, so a lowered hand is not upside down.
        val angle=atan2(axis.y,axis.x)*180f/PI.toFloat()+90f
        return Arm(ArmKinematics.Pose(point(shoulder),point(elbow),point(wrist)),hand,angle)
    }
    fun solve(shoulder: Offset,target: Offset,unit: Float,side: Float): Arm {
        val delta=target-shoulder
        val hand=shoulder+delta*(min(delta.getDistance(),unit*12f)/delta.getDistance().coerceAtLeast(.0001f))
        val relative=(hand-shoulder)/unit
        val reach=(abs(relative.x)/10f).coerceIn(0f,1f)
        val lift=((10f-relative.y)/16f).coerceIn(0f,1f)
        val preferred=shoulder+Offset(side*(1.8f+2.6f*reach),6.3f-7f*lift)*unit
        // Both projected bones stay within their native length. Their screen lengths
        // may shorten as the forearm points away from the viewer towards the monster.
        val elbow=ArmKinematics.sharedTarget(point(shoulder),point(hand),point(preferred),
            ArmKinematics.Point(0f,0f),unit*6.5f)
        return anchored(shoulder,offset(elbow),hand,unit)
    }
}

private fun DrawScope.rearArm(arm: RearArmRig.Arm,unit: Float,metal: Boolean,
    forearmOnly: Boolean=false,upperOnly: Boolean=false,mirror: Boolean=false) {
    if(with(MotionArtwork) { paintArm(arm.joints,unit,metal=metal,forearmOnly=forearmOnly,upperOnly=upperOnly,rear=true,mirror=mirror) })return
    val a=Offset(arm.joints.shoulder.x,arm.joints.shoulder.y)
    val e=Offset(arm.joints.elbow.x,arm.joints.elbow.y)
    val w=Offset(arm.joints.wrist.x,arm.joints.wrist.y)
    if(!forearmOnly)drawLine(Color(0xff4b6477),a,e,unit*3.8f)
    if(!upperOnly)drawLine(Color(0xffc4a285),e,w,unit*3.2f)
}

private fun DrawScope.arm(a: Offset, b: Offset, unit: Float, opacity: Float = 1f, metal: Boolean = false, forearmOnly: Boolean = false) {
    if (with(MotionArtwork) { arm(a, b, unit, opacity, metal, forearmOnly) }) return
    val elbow = (a+b)*.5f+Offset(-unit*1.5f,unit*3)
    if (!forearmOnly) {
        drawLine(Color(0xff273a52).copy(alpha=opacity.coerceIn(0f,1f)), a, elbow, unit * 5)
        drawLine(Color(0xff4b6477).copy(alpha=opacity.coerceIn(0f,1f)), a, elbow, unit * 3)
    }
    drawLine(Color(0xffc4a285).copy(alpha=opacity.coerceIn(0f,1f)), elbow, b, unit * 3)
    pixelRect(Color(0xffead1ae).copy(alpha=opacity.coerceIn(0f,1f)), b.x - unit, b.y - unit, unit * 3, unit * 3)
}

private fun DrawScope.heldWeapon(kind: Kind, hand: Offset, unit: Float, angle: Float, opacity: Float = 1f, gear: Gear? = null) {
    val scale = unit * GearAppearance.heldScale(kind)
    val grip = MotionArtwork.weaponGrip(gear?.let { gearSprite(it) } ?: gearSprite(kind))
    val gripX = grip?.x ?: if (kind == Kind.WHIP) 7f else 16f
    val gripY = grip?.y ?: if (kind == Kind.SPEAR) 40f else if (kind == Kind.WHIP) 25f else 27f
    withTransform({ rotate(angle, hand) }) {
        sprite(gear?.let { gearSprite(it) } ?: gearSprite(kind), hand.x - gripX * scale, hand.y - gripY * scale, 32 * scale, opacity=opacity, detailPose="idle_0")
    }
}

internal fun DrawScope.drawAnimatedBattle(s: State, clock: Long, visual: BattleVisual?, battleTop: Float = 0f) {
    val age=visual?.let { clock-it.startedAt-BattleAnimation.HIT_MS } ?: -1L
    val connected=visual?.impact?.outcome !in listOf(ImpactOutcome.MISS,ImpactOutcome.DODGE)
    val strength=if(visual?.action=="strong" || visual?.impact?.critical==true)2.5f else 1.3f
    val shake=if(connected && age in 0..220) sin(age*.18).toFloat()*(1-age/220f)*strength else 0f
    withTransform({ translate(shake,shake*.4f);scale(1+abs(shake)*.003f,1+abs(shake)*.003f,center) }) {
        drawBattleActors(s,clock,visual,battleTop)
    }
}

internal fun DrawScope.drawChestEncounter(s: State, clock: Long, battleTop: Float) {
    drawBattleActors(s, clock, null, battleTop, chestEncounter = true)
}

internal fun DrawScope.drawPreparationCamp(s: State, clock: Long) {
    if(!PresentationArtwork.loaded) { drawBattleActors(s,clock,null,0f,campEncounter=true);return }
    with(PreparationArtwork) { paintCamp(clock) }
    val items=Equipment.equipped(s)
    val weapon=items.firstOrNull { it.weapon && it.id==s.equipment[Slot.RIGHT] } ?: items.firstOrNull { it.weapon }
    val off=items.firstOrNull { it.weapon && it.id!=weapon?.id && it.id==s.equipment[Slot.LEFT] }
    val shield=items.firstOrNull { it.kind==Kind.SHIELD }
    val metal=items.any { it.kind==Kind.BODY && it.variantId in listOf("chainmail","platearmor") }
    val motion=CampMotion056.sample(clock,weapon?.kind)
    val direction=CampMotion056.direction(clock)
    val width=min(size.width*.22f,size.height*.94f*.48f)
    val unit=width/32
    val baseline=size.height*.865f-(if(direction==0)motion.bob else 0f)*unit
    val x=size.width*(.49f+motion.travel)-width/2
    val y=baseline-unit*48
    withTransform({rotate(if(direction==0)motion.lean else 0f,Offset(x+width/2,baseline))}) {
        val rightShoulder=Offset(x,y)+PresentationArtwork.campShoulder(clock,direction,true)*unit
        val leftShoulder=Offset(x,y)+PresentationArtwork.campShoulder(clock,direction,false)*unit
        val angle=if(direction==0)motion.angle else direction*23f
        val radians=angle*PI.toFloat()/180
        val supportShift=Offset(sin(radians),-cos(radians))*unit*4f
        fun point(p: Offset)=ArmKinematics.Point(p.x,p.y)
        val wanted=rightShoulder+if(direction==0)Offset(-unit*(2.5f+motion.reach*4f),unit*(10-motion.armLift-motion.reach*2))
            else Offset(direction*unit*4,unit*9)
        val shared=if(weapon?.twoHanded==true)ArmKinematics.sharedTarget(point(rightShoulder),point(leftShoulder),point(wanted),point(supportShift),unit*12) else point(wanted)
        val near=RearArmRig.solve(rightShoulder,Offset(shared.x,shared.y),unit,if(direction==0)-1f else direction.toFloat())
        val farTarget=if(weapon?.twoHanded==true)near.hand+supportShift else leftShoulder+Offset(unit*(if(direction==0)2f else direction*2f),unit*10)
        val far=RearArmRig.solve(leftShoulder,farTarget,unit,if(direction==0)1f else -direction.toFloat())
        fun paintRight() {
            weapon?.let { heldWeapon(it.kind,near.hand,unit,angle,gear=it) }
            rearArm(near,unit,metal,forearmOnly=true,mirror=true)
            with(MotionArtwork) { grip(near.hand,unit,near.gloveAngle,1f,metal,mirror=true) }
        }
        fun paintLeft() {
            off?.let { heldWeapon(it.kind,far.hand,unit,-angle,gear=it) }
            rearArm(far,unit,metal,forearmOnly=true)
            with(MotionArtwork) { grip(far.hand,unit,far.gloveAngle,1f,metal) }
            shield?.let {
                val w=unit*(if(direction==0)20 else 14)
                sprite(gearSprite(it),far.hand.x-w/2,far.hand.y-w*.75f,w,detailPose="idle_0")
            }
        }
        rearArm(near,unit,metal,upperOnly=true,mirror=true)
        rearArm(far,unit,metal,upperOnly=true)
        if(direction<0)paintRight()
        if(direction>0)paintLeft()
        with(PresentationArtwork) { frontBody(s,x,baseline,width,motion.stride,clock,direction) }
        if(direction>=0)paintRight()
        if(direction<=0)paintLeft()
        with(PresentationArtwork) { frontBoots(s,x,baseline,width,clock,direction) }
    }
}

internal fun DrawScope.drawDialogueEncounter(s: State,clock: Long,battleTop: Float) {
    drawBattleActors(s,clock,null,battleTop,npcEncounter=true)
}

private fun DrawScope.drawBattleActors(s: State, clock: Long, visual: BattleVisual?, battleTop: Float, chestEncounter: Boolean = false, campEncounter: Boolean = false, npcEncounter: Boolean = false) {
    val motion = visual?.takeIf { it.active(clock) }
    val scene = motion?.scene ?: s
    // The inert placeholder supplies no game state or combat: prop scenes return after the hero.
    val enemy = scene.enemy ?: s.enemy ?: s.battleResult?.enemy
        ?: if (chestEncounter || campEncounter || npcEncounter) Enemy("장면", 1.0, 1.0, 0.0, 0.0, 0.0) else return
    val elapsed =
        motion?.let { (clock - it.startedAt).coerceAtLeast(0) } ?: BattleAnimation.DURATION_MS
    val attacking = motion?.impact?.target == ImpactTarget.ENEMY
    val defending = motion != null && !attacking
    val monsterAttacking = defending && clock >= motion!!.startedAt
    val defenseTime = if (!defending) elapsed else if (clock < motion!!.startedAt)
        (clock - motion.selectedAt).coerceIn(0L, 180L) else elapsed.coerceAtLeast(180L)
    val equipped = Equipment.equipped(scene)
    val weapon = equipped.firstOrNull { it.weapon && it.id == scene.equipment[Slot.RIGHT] }
        ?: equipped.firstOrNull { it.weapon }
    val shield = equipped.firstOrNull { it.kind == Kind.SHIELD }
    val offWeapon = equipped.firstOrNull { it.weapon && it.id != weapon?.id && it.id == scene.equipment[Slot.LEFT] }
    val kind = weapon?.kind
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
    // Fit painted actors into the arena below the measured information panel.
    val stageTop = if (campEncounter) 0f else h * (battleTop + .04f).coerceAtMost(.79f)
    val stageHeight = (h * .94f - stageTop).coerceAtLeast(h * .15f)
    val heroFloor = stageTop + stageHeight * .92f
    if (campEncounter) with(PreparationArtwork) { paintCamp(clock) } else arena(clock, floor = s.floor)
    val heroRecoil = if (playerDamage) BattleAnimation.reaction(elapsed) else 0f
    val heroFrame = BattlePose056.frame(motion?.action, kind, if (defending && elapsed < BattleAnimation.HIT_MS) defenseTime else poseTime, clock,
        damaged = playerDamage, dead = s.hp <= 0 && elapsed >= BattleAnimation.HIT_MS)
    val metalSleeves = Equipment.equipped(scene).any { it.kind == Kind.BODY && it.variantId in listOf("chainmail", "platearmor") }
    val defense = BattleAnimation.defense(if (defending) motion!!.action else "", defenseTime)
    val dodge = defense.retreat
    val heroWidth = min(w * (if(campEncounter).22f else .34f), stageHeight * .48f)
    val advanceDistance=w*.17f+(5-spriteUnit(heroWidth)).coerceAtLeast(0f)*10
    val heroX =
        (if(campEncounter) w*.49f-heroWidth/2 else w*.18f) + (if (attacking) pose.advance * advanceDistance else 0f) -
            heroRecoil * 15 -
            dodge * w * .11f
    val heroBase =
        heroFloor - (if (attacking) pose.advance * stageHeight * .11f + pose.lift * stageHeight else 0f) +
            defense.crouch * h * .045f + if(motion==null)sin(clock*.0026f)*spriteUnit(min(w*.34f,h*.32f))*.4f else 0f
    contactShadow(heroX,heroFloor+3,heroWidth)
    var hero = HeroPose(heroX, heroBase, spriteUnit(heroWidth))
    val flash =
        if (playerDamage && elapsed in BattleAnimation.HIT_MS..BattleAnimation.HIT_MS + 100) red.copy(alpha=.9f*(1-(elapsed-BattleAnimation.HIT_MS)/100f))
        else null
    // The player stands on the nearer floor plane. Draw the complete rig after the far actor.
    fun paintHero() {
        withTransform({
            rotate(
                if (attacking) pose.lean else defense.lean - heroRecoil * 10,
                Offset(heroX + heroWidth / 2, heroBase),
            )
        }) {
            val bodyUnit = if (MotionArtwork.loaded) (heroWidth / 32).coerceAtLeast(.25f) else spriteUnit(heroWidth)
            hero = HeroPose(heroX, heroBase - bodyUnit * 48, bodyUnit)
            fun paintBody() {
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
                        detailPose = heroFrame,
                        deferBoots = true,
                    )
            }
            val bone = if (MotionArtwork.loaded) MotionArtwork.shoulder(heroFrame) else Offset(23f, 23f)
            val shoulder = Offset(hero.x + hero.unit * bone.x, hero.y + hero.unit * bone.y)
            val limbScale = MotionArtwork.bodyScale(heroFrame)
            val overhead = if(attacking)pose.armLift else sin(clock*.0026f)*.4f
            val wantedHand =
                shoulder +
                    Offset(
                        hero.unit *
                            (if(weapon?.twoHanded==true) -2f+(if(attacking)pose.reach*2f else defense.guard*2f)
                             else 3.5f+(if(attacking)pose.reach*6f else defense.guard*1.5f)),
                        hero.unit *
                            (10 - (if (attacking) pose.advance * 3 else defense.guard * 8) - overhead*.65f) * limbScale,
                    )
            val gripAngle = if (attacking) pose.angle else 25f + defense.guard * 65f
            val leftBone = MotionArtwork.leftShoulder(heroFrame)
            val leftShoulder = Offset(hero.x + hero.unit * leftBone.x, hero.y + hero.unit * leftBone.y)
            val fallen = heroFrame.startsWith("death_")
            fun point(p: Offset)=ArmKinematics.Point(p.x,p.y)
            fun offset(p: ArmKinematics.Point)=Offset(p.x,p.y)
            fun wristShift(angle: Float): Offset {
                val rad=angle*PI.toFloat()/180
                return Offset(-sin(rad),cos(rad))*hero.unit*1.25f
            }
            val supportShift=Offset(sin(gripAngle*PI.toFloat()/180),-cos(gripAngle*PI.toFloat()/180))*hero.unit*4f
            val requested=if(weapon?.twoHanded==true) offset(ArmKinematics.sharedTarget(point(shoulder),point(leftShoulder),
                point(wantedHand),point(supportShift),hero.unit*12f)) else wantedHand
            val rightArm=RearArmRig.solve(shoulder,requested,hero.unit,side=1f)
            val hand=rightArm.hand
            val offAngle=if(shield!=null) (if(fallen)65f else -6f + defense.guard*18f - if(attacking)pose.reach*8f else 0f) else if(weapon?.twoHanded==true)gripAngle else if(offWeapon!=null)-gripAngle*.65f else 0f
            val requestedSupport = when {
                // Hang beside the left hip, not across the back. Guard lifts the same hand
                // towards the left chest; the upper rim stays below the face.
                shield != null -> leftShoulder + Offset(hero.unit * (if(fallen)5f else -4f-defense.guard), hero.unit * (if(fallen)5f else 11.5f-defense.guard*7f))
                weapon?.twoHanded == true -> hand+supportShift
                offWeapon != null -> leftShoulder+Offset(hero.unit*(2+if(attacking)pose.reach*5 else 0f),hero.unit*(10-overhead*.5f))
                else -> Offset(hero.x+hero.unit*7,hero.y+hero.unit*31)
            }
            // The same measured grip and brace constrain the raised guard as well as
            // the relaxed hand, so the shield never rotates away from its forearm.
            val mountArm=if(shield!=null)ArmKinematics.solve(point(leftShoulder),point(requestedSupport+wristShift(offAngle)),hero.unit*6.5f,hero.unit*6.5f) else null
            val leftArm=if(mountArm!=null)RearArmRig.anchored(leftShoulder,offset(mountArm.elbow),offset(mountArm.wrist)-wristShift(offAngle),hero.unit)
                else RearArmRig.solve(leftShoulder,requestedSupport,hero.unit,side=-1f)
            val supportHand=leftArm.hand
            val shieldMount=shield?.let { PreparationArtwork.mountShield(it,supportHand,offset(leftArm.joints.elbow)) }
            if(attacking && kind!=null) weaponTrail(kind,hand,hero.unit,elapsed,motion?.action=="strong")
            // Far shield/arm, upper sleeves, native shoulder caps/body, then the near
            // forearm and hands. Neither sleeve is pasted across a rear shoulder cap.
            shield?.let { shield ->
                if(shieldMount!=null) with(PreparationArtwork) { paintShield(shield,shieldMount,flash) }
                else sprite(gearSprite(shield),supportHand.x-hero.unit*8,supportHand.y-hero.unit*13,hero.unit*16,detailPose="idle_0")
            }
            rearArm(leftArm,hero.unit,metalSleeves,upperOnly=shield==null,mirror=true)
            if(shield!=null) {
                if(shieldMount!=null) with(PreparationArtwork) { paintShield(shield,shieldMount,flash,braceOnly=true) }
                with(MotionArtwork) { grip(supportHand,hero.unit,leftArm.gloveAngle,1f,metalSleeves,mirror=true) }
            }
            rearArm(rightArm,hero.unit,metalSleeves,upperOnly=true)
            fun paintHeldArms() {
                if (kind != null) {
                    if (attacking && kind == Kind.WHIP && elapsed in 150..570) {
                        val chain = weapon?.variantId == "chainwhip"
                        val thorn = weapon?.variantId == "thornwhip"
                        val whipColor = when { chain -> pale; thorn -> mint; weapon?.variantId == "flexiblewhip" -> muted; else -> amber }
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
                            drawLine(whipColor, previous, end, if (chain) 4f else 3f)
                            if (chain && i % 2 == 0) drawCircle(ink, 1.5f, end)
                            if (thorn && i % 4 == 0) drawLine(mint, end, end + Offset(4f, -6f), 2f)
                            previous = end
                        }
                        heldWeapon(kind, hand, hero.unit, pose.angle, gear = weapon)
                    } else
                        heldWeapon(
                            kind,
                            hand,
                            hero.unit,
                            if (attacking) pose.angle else 25f + defense.guard * 65f,
                            gear = weapon,
                        )
                }
                offWeapon?.let { heldWeapon(it.kind, supportHand, hero.unit, -gripAngle * .65f, gear = it) }
                // Viewer depth: all held weapons are behind both forearms and gripping
                // hands. In particular a crossguard or shaft cannot paint over a wrist.
                if(shield==null)rearArm(leftArm,hero.unit,metalSleeves,forearmOnly=true,mirror=true)
                rearArm(rightArm,hero.unit,metalSleeves,forearmOnly=true)
                with(MotionArtwork) { grip(hand, hero.unit, rightArm.gloveAngle, 1f, metalSleeves) }
                if (shield == null) with(MotionArtwork) { grip(supportHand, hero.unit, leftArm.gloveAngle, 1f, metalSleeves, mirror=true) }
            }
            // A two-handed grip is on the far chest plane in this rear view. The
            // body occludes both hands and forearms wherever their silhouettes meet.
            if(weapon?.twoHanded==true)paintHeldArms()
            paintBody()
            if(weapon?.twoHanded!=true)paintHeldArms()
            paintForegroundBoots(scene,hero,heroFrame,flash)

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
    }
    if (campEncounter) { paintHero(); return }
    if (npcEncounter) {
        val width=min(w*.30f,stageHeight*.44f)
        val x=w*.73f-width/2
        val baseline=stageTop+stageHeight*.79f
        val type=if(s.encounter==Event.MERCHANT) "merchant" else NpcInteractions.role(s).sprite
        contactShadow(x,baseline+3,width)
        with(PresentationArtwork) { person(type,x,baseline,width) }
        paintHero()
        return
    }
    if (chestEncounter) {
        val chestWidth = min(w * .30f, stageHeight * .48f)
        val baseline = stageTop + stageHeight * .72f
        contactShadow(w * .64f, baseline + 2, chestWidth)
        with(NarrativeArtwork) { paintChest(s.chestLoot != null, w * .64f, baseline, chestWidth) }
        paintHero()
        return
    }
    val species = MonsterCatalog.describe(enemy, s.floor)
    val type = species.sprite
    val family = species.family
    // Grow both dimensions from 0.47 by 1.5; exceptionally tall bosses still fit the clear arena.
    val desiredWidth = min(w * (if (enemy.boss) .38f else .28f), stageHeight * (if (enemy.boss) .44f else .25f)) * 1.5f
    val heightPerWidth = spriteHeight(type,32f,detailed=MotionArtwork.loaded)/32f
    val enemyWidth = min(desiredWidth,min(stageHeight*.90f/heightPerWidth,(w-32f)/1.5f))
    val unit = if (MotionArtwork.loaded) (enemyWidth / 32).coerceAtLeast(.25f) else spriteUnit(enemyWidth)
    val advance = if (monsterAttacking) monsterPose.advance else 0f
    val recoil = if (enemyDamage) BattleAnimation.reaction(elapsed) else 0f
    // Painted cells extend 8 logical pixels left and 40 right of their nominal origin.
    val leftLimit = unit*8+12
    val rightLimit = (w-unit*40-12).coerceAtLeast(leftLimit)
    val enemyHome=(w*.64f).coerceIn(leftLimit,rightLimit)
    val ex=(enemyHome-advance*w*.30f+recoil*16).coerceIn(leftLimit,rightLimit)
    val jump =
        if (monsterAttacking && family in listOf("slime", "boss") && elapsed <= BattleAnimation.HIT_MS)
            sin(elapsed.toFloat() / BattleAnimation.HIT_MS * PI).toFloat() * stageHeight * .10f
        else 0f
    val homeBaseline =
        stageTop + stageHeight * (if (family == "bat") .43f else .65f) + advance * stageHeight * .14f - jump +
            if (showingTell) tell.bob * h * tellWeight else 0f
    val baseline = max(homeBaseline,stageTop+spriteHeight(type,enemyWidth,detailed=MotionArtwork.loaded)+stageHeight*.04f)
    val ey = baseline - spriteHeight(type, enemyWidth, detailed = MotionArtwork.loaded)
    val death=if(s.mode==Mode.VICTORY) if(motion==null)1f else BattleAnimation.smooth((elapsed-BattleAnimation.HIT_MS-80)/360f) else 0f
    val opacity=1-death
    val dead=death>=1f
    contactShadow(ex, if(family=="bat")stageTop+stageHeight*.72f else baseline+3, unit * 32)
    if (enemy.boss && s.floor == 50 && s.cycle > 1 && !dead) {
        drawOval(Color(0xFFAC80DA).copy(alpha = .16f * opacity),Offset(ex-unit*3,ey-unit*3),Size(unit*38,baseline-ey+unit*7))
        for(i in 0..4) pixelRect(Color(0xFFB38AE0).copy(alpha=.55f*opacity),ex+unit*(i*8-1),ey+unit*(8+i*4),unit*2,unit*6)
    }
    if (!dead) {
        val tint =
            if (enemyDamage && elapsed in BattleAnimation.HIT_MS..BattleAnimation.HIT_MS + 100)
                (if (motion!!.impact.critical) Color.White else amber).copy(alpha=.9f*(1-(elapsed-BattleAnimation.HIT_MS)/100f))
            else null
        withTransform({
            val pivot = Offset(ex + unit * 16, baseline)
            rotate(
                if (monsterAttacking) (-monsterPose.lean).coerceAtMost(if(enemy.boss)8f else 18f)+tell.lean*tellWeight else if (showingTell) tell.lean+recoil*12 else recoil * 12,
                pivot,
            )
            scale(1f,1-death*.65f,pivot)
            if (showingTell && family == "slime") scale(1f + tell.squash*tellWeight, 1f - tell.squash*tellWeight, pivot)
            if (showingTell && family == "bat")
                scale(
                    if (enemy.intent == EnemyIntent.HEAVY) .78f else 1f + abs(tell.bob) * 5,
                    1f,
                    pivot,
                )
            if (family == "slime" && monsterAttacking)
                scale(1f + monsterPose.impact * .2f, 1f - monsterPose.impact * .18f, pivot)
            if (family == "bat" && monsterAttacking)
                scale(1f + sin(elapsed * .03).toFloat() * .16f, 1f, pivot)
        }) {
            val monsterFrame = SpriteFrames.monster(monsterAttacking, poseTime, clock, hit = enemyDamage,
                dying = death > 0f, guarding = enemy.intent == EnemyIntent.GUARD && showingTell)
            if(family=="bat") batBody(ex,ey,enemyWidth,clock,tint,opacity,type,monsterFrame)
            else combatBody(type,ex,ey,enemyWidth,tint,family in listOf("knight","boss"),if(monsterAttacking)monsterPose.stride else sin(clock*.0026f)*.06f,opacity,monsterFrame)

            if (showingTell && tell.guard > 0f && family in listOf("knight", "boss"))
                sprite("shield", ex - unit * 2, ey + unit * 17, unit * 25, opacity=opacity*tellWeight, detailPose="idle_0")
            if (family in listOf("knight", "boss")) {
                val paintedHand = MotionArtwork.monsterHand(type, monsterFrame)
                val hand = paintedHand?.let { Offset(ex + unit * it.x, ey + unit * it.y) } ?:
                    Offset(
                        ex + unit * 8 - advance * unit * 4,
                        ey + unit * (28 - if (showingTell) tell.armLift*tellWeight else 0f),
                    )
                if(monsterAttacking) weaponTrail(if(family=="boss")Kind.HAMMER else Kind.SWORD,hand,unit,elapsed,enemy.intent==EnemyIntent.HEAVY,-1f)
                if (!MotionArtwork.loaded) arm(Offset(ex + unit * 12, ey + unit * 22), hand, unit,opacity,metal=true)
                heldWeapon(
                    if (family == "boss") Kind.HAMMER else Kind.SWORD,
                    hand,
                    unit,
                    if (monsterAttacking) -monsterPose.angle
                    else if (showingTell) tell.weaponAngle else -25f,
                    opacity=opacity,
                )
                with(MotionArtwork) { grip(hand, unit, if (monsterAttacking) -monsterPose.angle else if (showingTell) tell.weaponAngle else -25f, opacity, metal=true) }
            }
        }
    } else {
        pixelRect(Color(0xff3c524a), ex, baseline - 5, unit * 32, 5f)
        sprite("coin", ex + unit * 12, baseline - 35, 32f)
    }
    paintHero()
    if (monsterAttacking && enemy.intent == EnemyIntent.FLURRY) {
        for (i in 0..2) drawLine(
            amber.copy(alpha = monsterPose.impact * .8f),
            Offset(heroX + heroWidth * .8f, heroBase - heroWidth * .65f + i * 12),
            Offset(heroX + heroWidth * 1.2f, heroBase - heroWidth * .45f + i * 12),
            3f,
        )
    }
    // Openings are described in the panel, without a floating three-dot marker.
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
