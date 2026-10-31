package com.powerfulhimchan.mapexit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerfulhimchan.mapexit.core.*
import kotlinx.coroutines.delay

@Composable
internal fun AchievementNotice(vm: GameModel) {
    val ids = vm.engine.state.achievements.notices.toList()
    LaunchedEffect(ids) {
        if (ids.isNotEmpty()) {
            delay(4000)
            vm.dismissAchievementNotices(ids)
        }
    }
    if (ids.isNotEmpty()) {
        val first = Achievements.all.firstOrNull { it.id == ids.first() }
        Text("업적 달성 · ${first?.name ?: "새 업적"}${if (ids.size > 1) " 외 ${ids.size - 1}개" else ""} · 기록에서 확인",
            color = amber, fontSize = 11.sp, lineHeight = 15.sp,
            modifier = Modifier.fillMaxWidth().background(panel).padding(horizontal = 8.dp, vertical = 5.dp).semantics {
                contentDescription = "업적 달성 알림"
            })
    }
}

internal fun LazyListScope.achievementCards(vm: GameModel) {
    val s = vm.engine.state
    val p = s.achievements
    item(key = "achievement-summary") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("달성 ${p.unlocked.size} / ${Achievements.all.size} · 보상 수령 ${p.claimed.size}", color = mint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("진행도와 칭호는 사망·귀환·계승 후에도 유지됩니다. 보상은 지상에서 업적마다 한 번 받습니다.", color = muted, fontSize = 12.sp, lineHeight = 18.sp)
            Text("칭호: ${Achievements.title(s) ?: "없음"}", color = amber, fontSize = 14.sp)
            if (Achievements.title(s) != null) GameButton(onClick = { vm.choose(Choice("칭호 해제", "selectTitle", -1)) }) {
                Text("칭호 해제", color = muted)
            }
            Text("골드·아이템·성장 포인트에는 기존 손실 및 회차 초기화 규칙이 적용됩니다. 이전 저장은 확인 가능한 기록만 반영합니다.", color = muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
    itemsIndexed(Achievements.all, key = { _, a -> "achievement:${a.id}" }) { index, a ->
        val unlocked = a.id in p.unlocked
        val claimed = a.id in p.claimed
        val count = if (unlocked) a.target else Achievements.progress(s, a)
        Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(8.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuArt("achievements", Modifier.size(30.dp))
                Text(a.name, color = if (unlocked) amber else pale, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (claimed) "수령 완료" else if (unlocked) "달성" else "진행 중", color = if (unlocked) mint else muted, fontSize = 11.sp)
            }
            Text(a.condition, color = pale, fontSize = 13.sp, lineHeight = 19.sp)
            LinearProgressIndicator(progress = { count.toFloat() / a.target }, modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "${a.name} 진행도 · $count / ${a.target}"
            }, color = mint, trackColor = line)
            Text("$count / ${a.target}", color = muted, fontSize = 12.sp)
            Text("보상: ${a.rewardText}", color = amber, fontSize = 12.sp, lineHeight = 18.sp)
            if (a.specialGear) {
                val ring = s.inventory.firstOrNull { it.variantId == "exit-seal" } ?: Achievements.specialRing(-1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(46.dp).semantics { contentDescription = "특별 장비 · 귀환자의 인장" }) { ItemArt(ring, Modifier.fillMaxSize()) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ring.summary(), color = pale, fontSize = 12.sp, lineHeight = 18.sp)
                        Text("반지 슬롯 사용 · 일반 장비와 같은 사망·판매·계승 규칙", color = muted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
                if (claimed && ring.id in s.quickEquipIds) LootEquipButton(vm, ring)
            }
            if (unlocked && !claimed) {
                if (!Achievements.canClaim(s, a)) Text("지상으로 귀환한 뒤 보상을 받을 수 있습니다", color = muted, fontSize = 12.sp)
                GameButton(onClick = { vm.choose(Choice("보상 받기", "claimAchievement", index.toLong())) },
                    enabled = Achievements.canClaim(s, a), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                        contentDescription = "업적 보상 받기 · ${a.name}"
                    }) { Text("보상 받기", color = if (Achievements.canClaim(s, a)) mint else muted) }
            }
            if (claimed && a.title != null) {
                val selected = p.selectedTitle == a.id
                GameButton(onClick = { vm.choose(Choice("칭호 선택", "selectTitle", index.toLong())) },
                    enabled = !selected, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "칭호 선택 · ${a.title}" }) {
                    Text(if (selected) "사용 중인 칭호 · ${a.title}" else "칭호 사용 · ${a.title}", color = if (selected) amber else mint)
                }
            }
        }
    }
}
