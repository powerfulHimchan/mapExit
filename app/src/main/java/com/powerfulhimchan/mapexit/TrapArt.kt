package com.powerfulhimchan.mapexit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipRect
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.*

private val iron = Color(0xff647983)
private val edge = Color(0xffc7d8d9)

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
        0 -> cauldron(t)
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
        with(ObstacleArtwork) { piece("plate",x,h*.84f,w*.23f,h*.07f) }
        pixelRect(color, x + 5, h * .85f, w * .23f - 10, 4f)
        if (lit) {
            drawCircle(color.copy(alpha = .14f), w * .15f, Offset(x + w * .115f, h * .85f))
            for (j in 0..2) pixelRect(color, x + w * (.04f + j * .07f), h * .87f, w * .02f, 3f)
        }
    }
    val heroWidth = min(w * .22f, h * .20f)
    val heroCenter=if(t.type==2) {
        val pressure=(danger*.75f-t.progress.toFloat()/t.required*.5f).coerceIn(0f,.75f)
        val wallWidth=w*(.13f+pressure*.40f)
        if(side==0)wallWidth+heroWidth*.43f else w-wallWidth-heroWidth*.43f
    } else w*ObstacleLayout.sequenceHeroCenter(t)
    val heroX = heroCenter - spriteUnit(heroWidth) * 16f
    val pose=when {
        t.phase==TrapPhase.RESULT && !t.success->"hit"
        t.type==3->"back"
        t.type==2->if(side==0)"push_left" else "push_right"
        t.phase==TrapPhase.ACTIVE && side!=null->if(side==0)"left" else "right"
        else->"idle"
    }
    with(ObstacleArtwork) { hero(s,heroX+spriteUnit(heroWidth)*16,h*.94f,32*spriteUnit(heroWidth),pose,
        if(t.phase==TrapPhase.RESULT && !t.success)red else null) }
    if (t.phase == TrapPhase.ACTIVE) {
        pixelRect(line, w * .12f, h * .97f, w * .76f, 4f)
        pixelRect(amber, w * .12f, h * .97f, w * .76f * (1 - time / t.limitMs).coerceIn(0f, 1f), 3f)
    }
}

private fun DrawScope.chain(x: Float, bottom: Float) {
    val width=size.width*.025f
    var y=0f
    val step=width*3
    while(y<bottom) {
        val height=min(step,bottom-y)
        clipRect(top=y,bottom=y+height) { with(ObstacleArtwork) { piece("chain",x-width/2,y,width,step) } }
        y+=step
    }
}

private fun DrawScope.cauldron(t: TrapState) {
    val w=size.width;val h=size.height
    val lane=ObstacleLayout.cauldronWaterLane(t)
    val pourRight=lane?.let { it>.5f } ?: true
    val tilt=(if(pourRight)1 else -1)*(if(lane==null)2f else 24f)
    val pivot=Offset(w*.5f,h*.22f)
    chain(w*.32f,h*.18f);chain(w*.68f,h*.18f)
    withTransform({rotate(tilt,pivot)}) { with(ObstacleArtwork) { piece("cauldron",w*.24f,h*.12f,w*.52f,h*.29f) } }
    for(i in 0..2) {
        val drift=sin(t.elapsedMs*.004f+i)*w*.015f
        with(ObstacleArtwork) { piece("steam",w*(.38f+i*.08f)+drift,h*(.04f-(i%2)*.015f),w*.04f,h*.10f,.4f) }
    }
    if(lane!=null) {
        val rad=tilt*PI.toFloat()/180
        val local=Offset(w*(if(pourRight).72f else .28f),h*.17f)-pivot
        val lip=pivot+Offset(local.x*cos(rad)-local.y*sin(rad),local.x*sin(rad)+local.y*cos(rad))
        val landing=Offset(w*lane,h*.94f)
        val age=if(t.phase==TrapPhase.RESULT)300L else t.elapsedMs-t.stepStartedMs
        val reach=(age/260f).coerceIn(.12f,1f)
        val fade=if(t.phase==TrapPhase.RESULT && t.success)(1-t.elapsedMs/700f).coerceIn(0f,1f) else 1f
        // Texture follows a continuous curve from the tilted rim to the actual dangerous lane.
        for(i in 0..19) {
            val a=i/20f;val b=min((i+1)/20f,reach)
            if(a>=reach)break
            fun point(f:Float)=Offset(lip.x+(landing.x-lip.x)*f,lip.y+(landing.y-lip.y)*f*f)
            val start=point(a);val end=point(b);val d=end-start
            withTransform({rotate(atan2(d.y,d.x)*180/PI.toFloat()-90,start)}) {
                with(ObstacleArtwork) { piece("water",start.x-w*.015f,start.y,w*.03f,hypot(d.x,d.y)+2,fade) }
            }
        }
        if(reach>=.95f) with(ObstacleArtwork) { piece("splash",landing.x-w*.13f,landing.y-h*.055f,w*.26f,h*.075f,fade) }
    }
}

private fun DrawScope.drawPhysicalChallenge(s: State, t: TrapState) {
    val w = size.width
    val h = size.height
    val floor = h * .85f
    // A single continuous lane replaces the unrelated left/right answer stones.
    with(ObstacleArtwork) { piece("ledge",w*.06f,floor,w*.88f,h*.08f) }
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
    if (ducking) crouchingAdventurer(s,heroX,floor,32*spriteUnit(heroWidth),tint)
    else with(ObstacleArtwork) { hero(s,heroX+16*spriteUnit(heroWidth),floor-jumpY,32*spriteUnit(heroWidth),
        if(result && !t.success)"hit" else if(jumping && motion in .1f.. .8f)"jump" else if(jumping && motion<1f)"land" else "idle",tint) }
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
    if (t.type == 4) {
        beamTimingBar(t)
    } else if (t.phase == TrapPhase.ACTIVE) {
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

/** Readiness is conveyed only by the bar. The green boundary uses the collision rule's ms. */
internal fun DrawScope.beamTimingBar(t: TrapState) {
    val x=size.width*.10f; val y=size.height-18f; val width=size.width*.80f
    val start=(t.beamCrouchStartMs.toFloat()/t.limitMs.coerceAtLeast(1)).coerceIn(0f,1f)
    val time=when(t.phase) { TrapPhase.PREPARE -> 0L; TrapPhase.RESULT -> t.actionAtMs; else -> t.elapsedMs }
    val p=(time.toFloat()/t.limitMs.coerceAtLeast(1)).coerceIn(0f,1f)
    pixelRect(ink,x-2,y-3,width+4,16f)
    pixelRect(line,x,y,width,10f)
    pixelRect(mint.copy(alpha=.75f),x+width*start,y,width*(1-start),10f)
    pixelRect(mint,x+width*start,y,2f,10f)
    for(i in 1..3) pixelRect(ink.copy(alpha=.5f),x+width*(start+(1-start)*i/4),y+6,1f,4f)
    val ready=time>=t.beamCrouchStartMs && time<t.limitMs
    pixelRect(if(ready)mint else pale,x+width*p-2,y-4,4f,18f)
}

/** Bent knees, lowered head and a horizontal torso leave visible clearance under the beam. */
private fun DrawScope.crouchingAdventurer(s: State,x: Float,floor: Float,width: Float,tint: Color?) {
    with(ObstacleArtwork) { hero(s,x+width/2,floor,width,"crouch",tint) }
}

private fun DrawScope.floorBlades(t: TrapState) {
    val w=size.width;val h=size.height;val floor=h*.85f
    val extension=if(t.phase==TrapPhase.PREPARE)1f else t.bladeExtension()
    val safe=t.phase!=TrapPhase.PREPARE && t.bladesRetracted()
    val stage=ObstacleLayout.blade(w,h,t);val left=stage.bladeLeft;val lane=stage.bladeRight-left
    pixelRect(Color(0xff080f16),left-3,floor,lane+6,h*.055f)
    for(i in 0..4) {
        val x=left+i*lane/5;val width=lane*.18f
        val bladeHeight=width*2.2f
        if(extension>0) clipRect(left=left-2,top=floor-bladeHeight*extension,right=left+lane+2,bottom=floor) {
            with(ObstacleArtwork) { piece("blade",x,floor-bladeHeight*extension,width,bladeHeight) }
        }
        with(ObstacleArtwork) { piece("socket",x-1,floor,width+2,h*.035f) }
        pixelRect(if(safe)mint else red,x,floor+h*.045f,width,3f)
    }
    if(safe)pixelRect(mint.copy(alpha=.16f),left,floor-h*.025f,lane,h*.025f)
}

private fun DrawScope.closingWalls(t: TrapState,danger: Float) {
    val w=size.width;val h=size.height
    val pressure=(danger*.75f-t.progress.toFloat()/t.required*.5f).coerceIn(0f,.75f)
    val width=w*(.13f+pressure*.40f)
    with(ObstacleArtwork) { piece("wall",0f,h*.10f,width+18,h*.70f) }
    withTransform({scale(-1f,1f,Offset(w/2,h/2))}) { with(ObstacleArtwork) { piece("wall",0f,h*.10f,width+18,h*.70f) } }
    if(t.phase==TrapPhase.ACTIVE) {
        for(x in listOf(width,w-width)) with(ObstacleArtwork) { piece("dust",x-w*.04f,h*.74f,w*.08f,h*.05f,.5f) }
    }
    for(i in 0 until t.required)pixelRect(if(i<t.progress)mint else iron,w*(.25f+i*.10f),h*.15f,14f,5f)
}

private fun DrawScope.memoryStones(t: TrapState) {
    val w=size.width;val h=size.height;val side=t.visualSide()
    for(row in 0..3)for(col in 0..1) {
        val x=w*(.18f+col*.36f);val y=h*(.34f+row*.11f)
        with(ObstacleArtwork) { piece(if(side==col)"tablet_lit" else "tablet",x,y,w*.29f,h*.075f) }
    }
    for(i in 0..4) {
        val x=w*(.12f+i*.17f)
        chain(x+12,h*.13f)
        with(ObstacleArtwork) { piece("weight",x,h*.10f,27f,h*.10f) }
        if(t.phase==TrapPhase.RESULT && !t.success)with(ObstacleArtwork) { piece("weight",x,h*.39f,27f,h*.30f) }
    }
    for(i in t.pattern.indices)pixelRect(if(i<t.progress)mint else line,w*(.30f+i*.10f),h*.26f,12f,4f)
}

private fun DrawScope.fallingBeam(t: TrapState) {
    val w=size.width;val h=size.height
    val fall=if(t.phase==TrapPhase.PREPARE)0f else t.beamDescent()
    val heroHeight=48*spriteUnit(min(w*.23f,h*.28f))
    val thickness=heroHeight*.38f;val contactY=h*.85f-heroHeight-thickness
    val passage=if(t.phase==TrapPhase.RESULT)(t.elapsedMs/650f).coerceIn(0f,1f) else 0f
    val y=h*.14f+(contactY-h*.14f)*fall+heroHeight*.35f*passage
    for(x in listOf(.17f,.80f)) {
        pixelRect(iron.copy(alpha=.7f),w*x,h*.13f,w*.018f,h*.58f)
        for(i in 0..3)pixelRect(amber,w*x,h*(.56f+i*.025f),w*.018f,3f)
    }
    chain(w*.19f,y);chain(w*.81f,y)
    with(ObstacleArtwork) { piece("beam",w*.09f,y,w*.82f,thickness) }
    pixelRect(mint.copy(alpha=.10f),w*.35f,h*.74f,w*.32f,h*.10f)
    for(i in 0..4)pixelRect(mint,w*(.36f+i*.065f),h*.83f,w*.03f,3f)
    if(t.phase==TrapPhase.ACTIVE)for(x in listOf(.30f,.67f)) {
        val cy=y+h*.18f
        drawLine(amber,Offset(w*x,cy-10),Offset(w*x,cy+7),3f)
        drawLine(amber,Offset(w*x-6,cy),Offset(w*x,cy+7),3f)
        drawLine(amber,Offset(w*x+6,cy),Offset(w*x,cy+7),3f)
    }
}
