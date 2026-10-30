package com.powerfulhimchan.mapexit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.min

internal fun LazyListScope.bestiaryCards(state: State) {
    val records = state.bestiary.values.sortedWith(compareBy({ it.firstFloor }, { it.name }))
    item(key = "bestiary-summary") {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("발견한 몬스터 ${records.size}종", color = mint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("조우 ${records.sumOf { it.encounters }}회 · 처치 ${records.sumOf { it.defeats }}회 · 사망 ${records.sumOf { it.deaths }}회", color = pale, fontSize = 12.sp)
            Text("마주친 몬스터만 기록합니다. 기록은 사망·귀환·계승 후에도 유지되며 테스트 전투는 제외합니다.", color = muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
    if (records.isEmpty()) item(key = "bestiary-empty") {
        Text("아직 만난 몬스터가 없습니다. 탐험 중 몬스터를 만나면 모습과 특성이 기록됩니다.", color = pale, fontSize = 14.sp, lineHeight = 20.sp)
    }
    items(records, key = { "monster:${it.id}" }) { record ->
        Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(8.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Canvas(Modifier.size(76.dp, 100.dp).semantics { contentDescription = "${record.name} 모습" }) {
                    val art = FineSprites.all[record.sprite] ?: FineSprites.all.getValue("slime")
                    val width = min(size.width * .84f, size.height * .84f * art.width / art.height)
                    sprite(record.sprite, (size.width - 32 * spriteUnit(width)) / 2, (size.height - spriteHeight(record.sprite, width)) / 2, width)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(record.name, color = pale, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("조우 ${record.encounters}회", color = muted, fontSize = 12.sp)
                    Text("처치 ${record.defeats}회 · 사망 ${record.deaths}회", color = amber, fontSize = 13.sp)
                    Text("최초 ${record.firstFloor}층 · 최근 ${record.lastFloor}층", color = muted, fontSize = 11.sp)
                }
            }
            Text(record.traits, color = pale, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}
