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
            else -> NpcInteractions.role(s).title
        }
    val message =
        when (s.encounter) {
            Event.CHEST -> "열어 보시겠습니까? 안에 몬스터가 숨어 있을 수도 있습니다."
            Event.MERCHANT -> "장비와 소모품을 사고, 가지고 있는 장비를 팔 수 있습니다."
            else -> if (NpcInteractions.role(s) != NpcRole.QUEST) NpcInteractions.description(s) else
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
    arena(clock, .52f, s.floor)
    adventurer(s, w * .17f, h * .94f, min(w * .30f, h * .25f))
    if (s.encounter == Event.CHEST) {
        val width = w * .23f
        contactShadow(w * .63f, h * .80f, spriteUnit(width) * 32)
        sprite("chest", w * .63f, h * .80f - spriteHeight("chest", width), width)
    } else {
        val width = min(w * .22f, h * .18f)
        val type = if (s.encounter == Event.MERCHANT) "merchant" else NpcInteractions.role(s).sprite
        val x = w * .65f
        val baseline = h * .79f
        contactShadow(x, baseline + 3, spriteUnit(width) * 32)
        sprite(type, x, baseline - spriteHeight(type, width), width)
        if (s.encounter == Event.MERCHANT) {
            val chestWidth = w * .12f
            sprite(
                "chest",
                x + spriteUnit(width) * 27,
                baseline - spriteHeight("chest", chestWidth),
                chestWidth,
            )
        }
    }
}
