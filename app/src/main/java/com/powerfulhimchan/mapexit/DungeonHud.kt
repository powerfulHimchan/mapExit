package com.powerfulhimchan.mapexit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerfulhimchan.mapexit.core.*

@Composable
internal fun DungeonToolbar(vm: GameModel, onRetreat: () -> Unit) {
    val s = vm.engine.state
    val canOpen = s.mode in listOf(Mode.EXPLORE, Mode.MOVING, Mode.FLOOR_CLEAR) && !vm.battleBusy()
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp).height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DungeonShortcut("장비", "equipment", canOpen, Modifier.weight(1f)) {
            vm.openDungeonPanel("equipment")
        }
        DungeonShortcut("능력치", "growth", canOpen, Modifier.weight(1f)) {
            vm.openDungeonPanel("growth")
        }
        DungeonShortcut(
            "지상으로",
            "return",
            s.mode in listOf(Mode.EXPLORE, Mode.MOVING),
            Modifier.weight(1f),
            danger = true,
            onClick = onRetreat,
        )
    }
}

@Composable
private fun DungeonShortcut(
    label: String,
    icon: String,
    enabled: Boolean,
    modifier: Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (!enabled) muted else if (danger) red else mint
    Row(
        modifier
            .fillMaxHeight()
            .background(panel, RoundedCornerShape(6.dp))
            .border(1.dp, if (enabled) line else line.copy(alpha = .4f), RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        MenuArt(icon, Modifier.size(20.dp))
        Text(
            label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

@Composable
internal fun LevelUpBanner(notice: LevelUpNotice, points: Int, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(panel, RoundedCornerShape(6.dp))
            .border(2.dp, amber, RoundedCornerShape(6.dp))
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuArt("level", Modifier.size(30.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Text(
                "레벨 업! LV.${notice.from} → LV.${notice.to}",
                color = amber,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                modifier =
                    Modifier.semantics {
                        contentDescription = "레벨 업 LV.${notice.from}에서 LV.${notice.to}"
                    },
            )
            Text("성장 포인트 +${notice.gainedPoints}P · 보유 ${points}P", color = pale, fontSize = 11.sp)
        }
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.semantics { contentDescription = "레벨업 알림 닫기" },
        ) {
            Text("닫기", color = muted, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun CombatTurnStrip(s: State, motion: BattleVisual?) {
    if (s.enemy == null || s.mode !in listOf(Mode.COMBAT_ATTACK, Mode.COMBAT_DEFEND)) return
    val player =
        motion?.let { it.impact.target == ImpactTarget.ENEMY } ?: (s.mode == Mode.COMBAT_ATTACK)
    val label =
        if (motion != null) {
            if (player) "공격 중" else "몬스터 공격 중"
        } else if (player) "공격 차례" else "방어 차례"
    val color = if (player) mint else amber
    Row(
        Modifier.fillMaxWidth()
            .background(panel, RoundedCornerShape(5.dp))
            .border(1.dp, color.copy(alpha = .7f), RoundedCornerShape(5.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "전투 차례" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            if (motion != null) "동작 재생 중" else if (player) "공격을 선택하세요" else "방어 행동을 선택하세요",
            color = muted,
            fontSize = 10.sp,
        )
    }
}
