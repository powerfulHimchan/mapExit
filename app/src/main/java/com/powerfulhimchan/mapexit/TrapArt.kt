package com.powerfulhimchan.mapexit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.*

private val iron = Color(0xff647983)
private val edge = Color(0xffc7d8d9)
private val wood = Color(0xff805537)
private val water = Color(0xff7ac8d4)

/** Every challenge is a physical mechanism. No solution letters are painted into the scene. */
internal fun DrawScope.drawObstacle(s: State, clock: Long) {
    val t = s.trap ?: return
    val w = size.width
    val h = size.height
    arena(clock, floor = s.floor)
    if (t.physicalAction) {
        drawPhysicalChallenge(s, t)
        return
    }
    val time = t.elapsedMs.toFloat()
    val danger =
        when (t.phase) {
            TrapPhase.PREPARE -> .10f + .03f * sin(time * .015f)
            TrapPhase.REVEAL -> .10f
            TrapPhase.ACTIVE -> (time / t.limitMs).coerceIn(0f, 1f)
            TrapPhase.RESULT -> if (t.success) .05f else 1f
        }
    val side = t.visualSide()
    when (t.type) {
        0 -> cauldron(t, danger, side)
        1 -> floorBlades(t)
        2 -> closingWalls(t, danger)
        3 -> memoryStones(t)
        4 -> fallingBeam(t)
    }
    // The two stones occupy the same left/right positions as the actual touch buttons.
    for (i in 0..1) {
        val x = w * (if (i == 0) .12f else .65f)
        val lit = side == i
        val color =
            when {
                lit -> amber
                else -> line
            }
        pixelRect(Color(0xff0b131b), x, h * .84f, w * .23f, h * .07f)
        pixelRect(iron, x + 2, h * .84f + 2, w * .23f - 4, h * .05f)
        pixelRect(color, x + 5, h * .85f, w * .23f - 10, 4f)
        if (lit) {
            drawCircle(color.copy(alpha = .14f), w * .15f, Offset(x + w * .115f, h * .85f))
            for (j in 0..2) pixelRect(color, x + w * (.04f + j * .07f), h * .87f, w * .02f, 3f)
        }
    }
    val heroWidth = min(w * .22f, h * .20f)
    val heroX = w * ObstacleLayout.sequenceHeroCenter(t) - spriteUnit(heroWidth) * 16f
    adventurer(
        s,
        heroX,
        h * .94f,
        heroWidth,
        armed = false,
        hitTint = if (t.phase == TrapPhase.RESULT && !t.success) red else null,
    )
    if (t.phase == TrapPhase.ACTIVE) {
        pixelRect(line, w * .12f, h * .97f, w * .76f, 4f)
        pixelRect(amber, w * .12f, h * .97f, w * .76f * (1 - time / t.limitMs).coerceIn(0f, 1f), 3f)
    }
}

private fun DrawScope.chain(x: Float, bottom: Float) {
    val h = size.height
    pixelRect(Color(0xff1c2b35), x - 4, 0f, 8f, bottom)
    for (i in 0 until (bottom / 12).toInt()) {
        pixelRect(iron, x - 3, i * 12f, 6f, 8f)
        pixelRect(edge, x - 2, i * 12f + 1, 1f, 5f)
    }
}

private fun DrawScope.cauldron(t: TrapState, danger: Float, side: Int?) {
    val w = size.width
    val h = size.height
    val lane=ObstacleLayout.cauldronWaterLane(t)
    val pourRight=lane?.let { it>.5f } ?: true
    val tilt=(if(pourRight) 1 else -1)*(if(lane==null) 2f else 24f)
    val pivot=Offset(w*.5f,h*.22f)
    chain(w*.32f,h*.17f); chain(w*.68f,h*.17f)
    withTransform({ rotate(tilt,pivot) }) {
        // A deep cast-iron bowl, rolled lip and two raised carrying handles.
        drawOval(iron,Offset(w*.20f,h*.13f),Size(w*.12f,h*.15f),style=Stroke(4f))
        drawOval(iron,Offset(w*.68f,h*.13f),Size(w*.12f,h*.15f),style=Stroke(4f))
        val bowl=Path().apply {
            moveTo(w*.25f,h*.20f);lineTo(w*.75f,h*.20f)
            cubicTo(w*.74f,h*.43f,w*.26f,h*.43f,w*.25f,h*.20f);close()
        }
        drawPath(bowl,Color(0xff17252e));drawPath(bowl,iron,style=Stroke(4f))
        drawOval(Color(0xff0b1821),Offset(w*.24f,h*.145f),Size(w*.52f,h*.10f))
        drawOval(water,Offset(w*.28f,h*.17f),Size(w*.44f,h*.055f))
        drawOval(edge,Offset(w*.24f,h*.145f),Size(w*.52f,h*.10f),style=Stroke(3f))
        drawArc(edge.copy(alpha=.5f),20f,140f,false,Offset(w*.29f,h*.22f),Size(w*.42f,h*.13f),style=Stroke(2f))
        for(i in 0..2) pixelRect(iron,w*(.39f+i*.09f),h*.29f,2f,h*.045f)
    }
    for(i in 0..4) {
        val drift=sin(t.elapsedMs*.006f+i)*w*.02f
        drawOval(pale.copy(alpha=.25f),Offset(w*(.36f+i*.055f)+drift,h*(.07f-(i%2)*.025f)),Size(w*.026f,h*.06f))
    }
    if(lane!=null) {
        val rad=tilt*PI.toFloat()/180
        val local=Offset(w*(if(pourRight) .74f else .26f),h*.19f)-pivot
        val lip=pivot+Offset(local.x*cos(rad)-local.y*sin(rad),local.x*sin(rad)+local.y*cos(rad))
        val landing=Offset(w*lane,h*.94f)
        val age=if(t.phase==TrapPhase.RESULT) 300L else t.elapsedMs-t.stepStartedMs
        val reach=(age/260f).coerceIn(.12f,1f)
        val fade=if(t.phase==TrapPhase.RESULT && t.success) (1-t.elapsedMs/700f).coerceIn(0f,1f) else 1f
        for(i in 0..23) {
            val f=i/23f
            if(f>reach) continue
            val point=Offset(lip.x+(landing.x-lip.x)*f,lip.y+(landing.y-lip.y)*f*f)
            val sway=sin(i*1.3f+t.elapsedMs*.025f)*w*.006f*f
            pixelRect(water.copy(alpha=.8f*fade),point.x+sway-w*.012f,point.y,w*.025f,h*.035f)
            if(i%3==0) pixelRect(edge.copy(alpha=fade),point.x+sway,point.y,w*.008f,h*.02f)
        }
        if(reach>=.95f) {
            drawOval(water.copy(alpha=.40f*fade),Offset(landing.x-w*.12f,landing.y-h*.018f),Size(w*.24f,h*.035f))
            for(i in 0..4) {
                val f=((t.elapsedMs+i*67)%300)/300f
                val direction=if(i%2==0) -1 else 1
                pixelRect(water.copy(alpha=(1-f)*fade),landing.x+direction*w*.11f*f,landing.y-h*.06f*sin(f*PI.toFloat()),w*.014f,h*.012f)
            }
        }
    }

}

private fun DrawScope.drawPhysicalChallenge(s: State, t: TrapState) {
    val w = size.width
    val h = size.height
    val floor = h * .85f
    // A single continuous lane replaces the unrelated left/right answer stones.
    pixelRect(Color(0xff151f27), w * .06f, floor, w * .88f, h * .08f)
    pixelRect(iron, w * .06f, floor, w * .88f, 4f)
    for (i in 0..5) {
        val x = w * (.78f + i * .025f)
        pixelRect(mint.copy(alpha = .65f), x, floor + h * .025f, w * .012f, 3f)
    }
    if (t.type == 1) floorBlades(t) else fallingBeam(t)
    val result = t.phase == TrapPhase.RESULT
    val stage = ObstacleLayout.blade(w,h,t)
    val motion = BattleAnimation.smooth(t.elapsedMs / 650f)
    val jumping = result && t.action == 0
    val ducking = result && t.action == 1
    val heroWidth = min(w * .23f, h * .28f)
    val heroX = if(t.type==1) stage.heroX else (w-spriteUnit(heroWidth)*32)/2f
    val jumpY = if(t.type==1) stage.jump else if(jumping) sin(motion*PI.toFloat()).coerceAtLeast(0f)*h*.27f else 0f
    val tint = if (result && !t.success) red else null
    if (ducking) crouchingAdventurer(heroX, floor, heroWidth, tint)
    else adventurer(s, heroX, floor - jumpY, heroWidth, armed = false, hitTint = tint)
    if (jumping) {
        contactShadow(heroX, floor + 4, spriteUnit(heroWidth)*32)
        for (i in 0..2) pixelRect(
            edge.copy(alpha = .5f),
            heroX - 6 - i * 6,
            floor - jumpY - h * .08f + i * 6,
            4f,
            3f,
        )
    }
    if (result && t.success && t.type == 4) {
        for (i in 0..2) pixelRect(mint, heroX - 7, floor - h * .13f + i * 5, 4f, 2f)
    }
    if (t.phase == TrapPhase.ACTIVE) {
        pixelRect(line, w * .12f, h * .97f, w * .76f, 4f)
        pixelRect(
            amber,
            w * .12f,
            h * .97f,
            w * .76f * (1 - t.elapsedMs.toFloat() / t.limitMs).coerceIn(0f, 1f),
            3f,
        )
    }
}

/** Bent knees, lowered head and a horizontal torso leave visible clearance under the beam. */
private fun DrawScope.crouchingAdventurer(x: Float, floor: Float, width: Float, tint: Color?) {
    val u = spriteUnit(width)
    val coat = tint ?: Color(0xff365d47)
    val hair = tint ?: Color(0xffeac685)
    contactShadow(x, floor + 3, width)
    pixelRect(Color(0xff3a302c), x + 4 * u, floor - 6 * u, 8 * u, 6 * u)
    pixelRect(Color(0xff3a302c), x + 20 * u, floor - 5 * u, 10 * u, 5 * u)
    pixelRect(coat, x + 8 * u, floor - 13 * u, 19 * u, 10 * u)
    pixelRect(coat, x + 4 * u, floor - 18 * u, 20 * u, 9 * u)
    pixelRect(Color(0xff91a381), x + 6 * u, floor - 17 * u, 14 * u, 2 * u)
    pixelRect(hair, x + 19 * u, floor - 25 * u, 11 * u, 12 * u)
    pixelRect(Color(0xfff4db9a), x + 22 * u, floor - 25 * u, 7 * u, 3 * u)
    pixelRect(Color(0xffd0aa7c), x + 25 * u, floor - 15 * u, 6 * u, 6 * u)
    pixelRect(coat, x + 27 * u, floor - 10 * u, 4 * u, 6 * u)
}

private fun DrawScope.floorBlades(t: TrapState) {
    val w = size.width
    val h = size.height
    val floor = h * .85f
    val extension = if (t.phase == TrapPhase.PREPARE) 1f else t.bladeExtension()
    val safe = t.phase != TrapPhase.PREPARE && t.bladesRetracted()
    val stage=ObstacleLayout.blade(w,h,t)
    val left=stage.bladeLeft; val lane=stage.bladeRight-left
    pixelRect(Color(0xff080f16),left-3,floor,lane+6,h*.055f)
    for(i in 0..4) {
        val x=left+i*lane/5
        val top=floor-h*.30f*extension
        pixelRect(iron,x-1,floor,lane*.18f,h*.035f)
        if(extension>0) {
            val blade=Path().apply { moveTo(x,floor);lineTo(x+lane*.09f,top);lineTo(x+lane*.18f,floor);close() }
            drawPath(blade,edge)
            drawLine(iron,Offset(x+lane*.09f,top+3),Offset(x+lane*.09f,floor),2f)
        }
        pixelRect(if(safe)mint else red,x,floor+h*.045f,lane*.18f,3f)
    }
    if(safe) pixelRect(mint.copy(alpha=.16f),left,floor-h*.025f,lane,h*.025f)

}

private fun DrawScope.closingWalls(t: TrapState, danger: Float) {
    val w = size.width
    val h = size.height
    val pressure = (danger * .75f - t.progress.toFloat() / t.required * .5f).coerceIn(0f, .75f)
    val width = w * (.13f + pressure * .40f)
    for (left in listOf(true, false)) {
        val x = if (left) 0f else w - width
        masonry(x, h * .10f, width, h * .70f, Color(0xff48545a))
        pixelRect(iron, if (left) width - 8 else x, h * .10f, 8f, h * .70f)
        for (j in 0..4) {
            val cy = h * (.20f + j * .12f)
            val path =
                Path().apply {
                    val edgeX = if (left) width else x
                    moveTo(edgeX, cy)
                    lineTo(edgeX + (if (left) 18 else -18), cy + 9)
                    lineTo(edgeX, cy + 18)
                    close()
                }
            drawPath(path, edge)
        }
        val handleX = if (left) width - 21 else x + 12
        pixelRect(amber, handleX, h * .50f, 9f, h * .12f)
        pixelRect(Color(0xff2b2520), handleX + 3, h * .51f, 3f, h * .10f)
    }
    for (i in 0 until t.required) pixelRect(
        if (i < t.progress) mint else iron,
        w * (.25f + i * .10f),
        h * .15f,
        14f,
        5f,
    )
}

private fun DrawScope.memoryStones(t: TrapState) {
    val w = size.width
    val h = size.height
    val side = t.visualSide()
    for (row in 0..3) for (col in 0..1) {
        val x = w * (.18f + col * .36f)
        val y = h * (.34f + row * .11f)
        pixelRect(Color(0xff0b131b), x - 3, y - 3, w * .29f + 6, h * .075f + 6)
        pixelRect(
            if (side == col) Color(0xff557767) else Color(0xff3e515b),
            x,
            y,
            w * .29f,
            h * .075f,
        )
        pixelRect(if (side == col) mint else iron, x + 4, y + 4, w * .29f - 8, 3f)
        pixelRect(if (side == col) mint else muted, x + w * .13f, y + h * .02f, w * .025f, h * .03f)
    }
    for (i in 0..4) {
        val x = w * (.12f + i * .17f)
        chain(x + 12, h * .13f)
        pixelRect(Color(0xff38454d), x, h * .10f, 27f, h * .10f)
        pixelRect(iron, x + 3, h * .10f + 3, 21f, 3f)
        if (t.phase == TrapPhase.RESULT && !t.success) pixelRect(iron, x, h * .39f, 27f, h * .30f)
    }
    for (i in t.pattern.indices) pixelRect(
        if (i < t.progress) mint else line,
        w * (.30f + i * .10f),
        h * .26f,
        12f,
        4f,
    )
}

private fun DrawScope.fallingBeam(t: TrapState) {
    val w = size.width
    val h = size.height
    val fall = if (t.phase == TrapPhase.PREPARE) 0f else t.beamDescent()
    val heroHeight = 48 * spriteUnit(min(w * .23f, h * .28f))
    val thickness = heroHeight * .38f
    val contactY = h * .85f - heroHeight - thickness
    val passage = if (t.phase == TrapPhase.RESULT) (t.elapsedMs / 650f).coerceIn(0f, 1f) else 0f
    // Contact reaches the standing head exactly at timeout, then passes over the crouched body.
    val y = h * .14f + (contactY - h * .14f) * fall + heroHeight * .35f * passage
    // Rails and head-height marks make the collision plane visible throughout the descent.
    for (x in listOf(.17f, .80f)) {
        pixelRect(iron.copy(alpha = .7f), w * x, h * .13f, w * .018f, h * .58f)
        for (i in 0..3) pixelRect(amber, w * x, h * (.56f + i * .025f), w * .018f, 3f)
    }
    chain(w * .19f, y)
    chain(w * .81f, y)
    pixelRect(Color(0xff33291f), w * .09f, y + 5, w * .82f, thickness)
    pixelRect(wood, w * .09f, y, w * .82f, thickness)
    for (i in 0..7) pixelRect(Color(0xffaa7c50), w * (.12f + i * .10f), y + h * .025f, w * .07f, 3f)
    for (x in listOf(.17f, .76f)) {
        pixelRect(iron, w * x, y - 2, w * .045f, thickness + 3)
        pixelRect(edge, w * x + 2, y + 2, 2f, thickness - 3)
    }
    // The open space under the beam is the route the crouched body fits through.
    pixelRect(mint.copy(alpha = .10f), w * .35f, h * .74f, w * .32f, h * .10f)
    for (i in 0..4) pixelRect(mint, w * (.36f + i * .065f), h * .83f, w * .03f, 3f)
    if (t.phase == TrapPhase.ACTIVE) {
        for (x in listOf(.30f, .67f)) {
            val cy = y + h * .18f
            drawLine(amber, Offset(w * x, cy - 10), Offset(w * x, cy + 7), 3f)
            drawLine(amber, Offset(w * x - 6, cy), Offset(w * x, cy + 7), 3f)
            drawLine(amber, Offset(w * x + 6, cy), Offset(w * x, cy + 7), 3f)
        }
    }
}
