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
    val menus =
        listOf(
            "장비" to "equipment",
            "능력치" to "growth",
            "가방" to "inventory",
            "기록" to "help",
            "방향 처음" to "directions",
            "지상으로" to "return",
        )
    Column(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        menus.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth().height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { (label, action) ->
                    val enabled =
                        when (action) {
                            "return" -> s.mode in listOf(Mode.EXPLORE, Mode.MOVING)
                            "directions" ->
                                s.mode == Mode.EXPLORE &&
                                    vm.engine.controls.directionSelectionActive()
                            else -> canOpen
                        }
                    DungeonShortcut(
                        label,
                        if (action == "directions") "return" else action,
                        enabled,
                        Modifier.weight(1f),
                        danger = action == "return",
                    ) {
                        when (action) {
                            "return" -> onRetreat()
                            "directions" -> vm.resetDirections()
                            else -> vm.openDungeonPanel(action)
                        }
                    }
                }
            }
        }
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
internal fun LevelUpBanner(notice: LevelUpNotice, points: Int, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .padding(bottom = 6.dp)
            .background(panel, RoundedCornerShape(6.dp))
            .border(2.dp, amber, RoundedCornerShape(6.dp))
            .clickable(role=Role.Button,onClick=onOpen)
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
            onClick = onOpen,
            modifier = Modifier.semantics { contentDescription = "성장 배분 열기" },
        ) {
            Text("능력치 배분 ›", color = mint, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun CombatTurnStrip(s: State, motion: BattleVisual?, compact: Boolean) {
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
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = "전투 차례" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            color = color,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            fontWeight = FontWeight.Bold,
        )
        if (!compact)
            Text(
                if (motion != null) "동작 재생 중" else if (player) "공격을 선택하세요" else "방어 행동을 선택하세요",
                color = muted,
                fontSize = 9.sp,
                lineHeight = 14.sp,
            )
    }
}

@Composable
internal fun CombatShortcuts(vm: GameModel) {
    val s = vm.engine.state
    val enabled = s.mode == Mode.COMBAT_ATTACK && !vm.battleBusy()
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        buildList {
            add("회복약 ${s.inventory.count { it.kind == Kind.POTION && it.potionGrade == PotionGrade.BASIC }}" to "potion")
            if (s.inventory.any { it.kind == Kind.POTION && it.potionGrade == PotionGrade.GREATER })
                add("상급 ${s.inventory.count { it.kind == Kind.POTION && it.potionGrade == PotionGrade.GREATER }}" to "strongPotion")
            add("도주" to "flee")
        }
            .forEach { (label, action) ->
                Row(
                    Modifier.height(30.dp)
                        .background(panel, RoundedCornerShape(5.dp))
                        .border(1.dp, line, RoundedCornerShape(5.dp))
                        .clickable(enabled = enabled, role = Role.Button) {
                            vm.choose(Choice(label, action))
                        }
                        .semantics(mergeDescendants = true) {
                            contentDescription = when (action) { "potion" -> "회복약 사용"; "strongPotion" -> "상급 회복약 사용"; else -> "도주 시도" }
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        color = if (enabled) mint else muted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
    }
}
