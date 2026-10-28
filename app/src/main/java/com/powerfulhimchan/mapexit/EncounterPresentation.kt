package com.powerfulhimchan.mapexit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.min

@Composable
internal fun EncounterCaption(vm: GameModel, modifier: Modifier) {
    vm.revision
    val s = vm.engine.state
    val title =
        when (s.encounter) {
            Event.CHEST -> "상자 발견"
            Event.MERCHANT -> "떠돌이 상인"
            else -> "여행자의 의뢰"
        }
    val message =
        when (s.encounter) {
            Event.CHEST -> "열어 보시겠습니까? 안에 몬스터가 숨어 있을 수도 있습니다."
            Event.MERCHANT -> "장비와 소모품을 사고, 가지고 있는 장비를 팔 수 있습니다."
            else ->
                s.quest?.let {
                    if (it.complete) "${it.title}\n의뢰 완료 · 보상을 받을 수 있습니다."
                    else "진행 중: ${it.title}\n${it.progress} / ${it.target} · 새 의뢰는 제시하지 않습니다."
                }
                    ?: s.dungeon?.here()?.questOffer?.let {
                        "${it.title}\n보상 ${it.reward} G · 수락하시겠습니까?"
                    }
                    ?: "지금 부탁할 수 있는 의뢰가 없습니다."
        }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
                .padding(10.dp)
                .heightIn(max = maxHeight * .48f)
                .background(ink.copy(alpha = .96f), RoundedCornerShape(8.dp))
                .border(1.dp, amber, RoundedCornerShape(8.dp))
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                color = amber,
                fontSize = 19.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = title },
            )
            Text(message, color = pale, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

/** The near player and far encounter use the same over-the-shoulder viewpoint as combat. */
internal fun DrawScope.drawEncounter(s: State, clock: Long) {
    val w = size.width
    val h = size.height
    masonry(0f, 0f, w, h * .55f, Color(0xFF293C45))
    pixelRect(Color(0xFF172832), 0f, h * .55f, w, h * .45f)
    for (i in 0..6) {
        val t = i / 6f
        drawLine(
            line,
            Offset(0f, h * (.55f + .45f * t * t)),
            Offset(w, h * (.55f + .45f * t * t)),
            1f,
        )
    }
    for (i in -2..5) drawLine(line, Offset(w * .55f, h * .55f), Offset(i * w / 4, h), 1f)
    torch(w * .10f, h * .40f, clock)
    torch(w * .86f, h * .40f, clock + 160)
    val heroWidth = min(w * .30f, h * .25f)
    val unit = (heroWidth / 16).toInt().coerceAtLeast(1).toFloat()
    sprite("heroBack", w * .17f, h * .94f - unit * 24, heroWidth, mint)
    if (s.encounter == Event.CHEST) {
        sprite("chest", w * .63f, h * .66f, w * .23f, amber)
    } else {
        val width = min(w * .22f, h * .18f)
        val npcUnit = (width / 16).toInt().coerceAtLeast(1).toFloat()
        val x = w * .65f
        val y = h * .78f - npcUnit * 24
        sprite("npc", x, y, width, if (s.encounter == Event.MERCHANT) amber else mint)
        if (s.encounter == Event.MERCHANT) {
            sprite("chest", x + width * .65f, h * .76f, w * .12f, amber)
        }
    }
}
