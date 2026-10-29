package com.powerfulhimchan.mapexit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerfulhimchan.mapexit.core.Choice

@Composable
internal fun RunResultScreen(vm: GameModel, modifier: Modifier) {
    val result = vm.engine.state.runResult ?: return
    val seconds = result.elapsedMs / 1000
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("EXPEDITION / RESULT", color = muted, fontSize = 11.sp, letterSpacing = 2.sp)
            Text(
                if (result.retreated) "탐험을 포기했습니다" else "사망했습니다",
                color = red,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
            )
            Text("이번 탐험 기록", color = pale, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Column(
                Modifier.fillMaxWidth().background(panel).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ResultLine(if (result.retreated) "귀환한 위치" else "사망한 위치", "지하 ${result.floor}층")
                ResultLine("최대 도달 층", "지하 ${result.deepestFloor}층")
                ResultLine("처치한 적", "${result.kills}명")
                ResultLine("연 상자", "${result.chests}개")
                ResultLine("피한 함정", "${result.traps}개")
                ResultLine("탐험 시간", "${seconds / 60}분 ${seconds % 60}초")
            }
            Text(
                "잃은 소지품 · ${result.lostItems}개 / ${result.lostGold}G",
                color = red,
                fontSize = 14.sp,
            )
            Text(
                "장착 장비를 포함한 모든 아이템과 소지 골드를 잃었습니다. 성장, 보관 골드와 이동 장치는 유지됩니다.",
                color = muted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }
        ExpeditionButton("준비 화면으로", "다음 탐험 준비", "return", Modifier.padding(vertical = 8.dp)) {
            vm.choose(Choice("준비 화면으로", "runContinue"))
        }
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "$label $value"
        }
    ) {
        Text(label, color = muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = pale, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
