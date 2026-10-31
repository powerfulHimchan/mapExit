package com.powerfulhimchan.mapexit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State
import kotlin.math.min

@Composable
internal fun ChallengeScreen(vm: GameModel, clock: Long, modifier: Modifier) {
    vm.revision
    val s = vm.engine.state.copy()
    val c = s.challenge ?: return
    val phase = c.phase
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SPECIAL / CHALLENGE", color = muted, fontSize = 9.sp, letterSpacing = 2.sp)
                Text(c.kind.title, color = amber, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            if (phase in listOf(ChallengePhase.ACTIVE, ChallengePhase.HAZARD, ChallengePhase.PREVIEW))
                GameButton(onClick = { vm.choose(Choice("일시정지", "challengePause")) }) { Text("일시정지", color = mint) }
        }
        when (phase) {
            ChallengePhase.INTRO -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .background(panel, RoundedCornerShape(8.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("도전 안내", color = mint, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(c.kind.objective, color = pale, fontSize = 15.sp, lineHeight = 22.sp)
                if (c.kind == ChallengeKind.SEALS) Text("기억할 순서: ${c.sealOrder.joinToString(" → ") { ChallengeStages.runeNames[it - 1] }}", color = amber, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (c.kind == ChallengeKind.STEPS) {
                    Text("이동 가능 ${c.stepLimit}칸 · 시작 전에는 시간 제한 없이 계획할 수 있습니다", color = amber, fontSize = 14.sp, lineHeight = 20.sp)
                    ChallengePlan(s.dungeon!!, Modifier.fillMaxWidth().height(220.dp))
                }
                if (c.kind == ChallengeKind.COLLAPSE) Text("갈림길 버튼의 문양과 ‘안정된 문양’을 비교하세요. 잘못 든 길에서는 붕괴 전에 돌아와야 합니다.", color = pale, fontSize = 13.sp, lineHeight = 20.sp)
                Text("몬스터와 미니맵은 없습니다. 이동은 한 번 누를 때 한 칸입니다. 방향 선택 중에는 ‘방향 처음’으로 돌아갈 수 있습니다.", color = muted, fontSize = 12.sp, lineHeight = 18.sp)
                Text("실패·포기 시 보상 없이 원래 던전으로 돌아갑니다. 도전을 시작하면 이번 회차에는 같은 포털에 다시 도전할 수 없습니다.", color = pale, fontSize = 13.sp, lineHeight = 20.sp)
                Text(if (s.training || s.debug) "연습에서는 보상과 도전 기록을 남기지 않습니다" else "성공 보상: ${60 + c.hostFloor * 4}G · ${if (c.kind in listOf(ChallengeKind.COLLAPSE, ChallengeKind.HAZARDS, ChallengeKind.STEPS)) "다음 구간 등급의 도전자 무기 + 상급 회복약 1개" else "상급 회복약 2개"}", color = amber, fontSize = 13.sp, lineHeight = 20.sp)
                Text("준비 중에는 제한 시간이 흐르지 않습니다", color = mint, fontSize = 12.sp)
            }
            ChallengePhase.PREVIEW -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("지도 기억 · ${(c.previewMs + 999) / 1000}초", color = mint, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { contentDescription = "기억 지도 공개" })
                ChallengePlan(s.dungeon!!, Modifier.weight(1f).fillMaxWidth())
                Text("초록: 시작 · 흰색: 출구 · 공개가 끝나면 지도가 사라집니다", color = muted, fontSize = 12.sp)
            }
            ChallengePhase.PAUSED -> Column(Modifier.weight(1f).fillMaxWidth().background(panel).padding(16.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("도전 일시정지", color = mint, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("제한 시간·횃불·붕괴·장애물이 모두 멈췄습니다", color = pale, fontSize = 13.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(10.dp))
                Text("포기하면 이번 회차에는 재도전할 수 없습니다", color = muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
            ChallengePhase.RESULT -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .background(panel, RoundedCornerShape(8.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (c.success) "도전 성공" else "도전 실패", color = if (c.success) mint else amber, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(c.result, color = pale, fontSize = 16.sp, lineHeight = 22.sp)
                Text("소요 ${"%.1f".format(c.elapsedMs / 1000.0)}초 · 이동 ${c.steps}칸", color = muted, fontSize = 13.sp)
                if (c.success && c.rewards.isNotEmpty()) {
                    Text("획득 ${c.rewardGold}G", color = amber, fontSize = 16.sp)
                    c.rewards.forEach { LootRewardRow(vm, it) }
                } else Text(if (s.training || s.debug) "연습 · 보상과 기록은 남기지 않습니다" else "보상 없음 · 장비·체력은 유지됩니다", color = muted, fontSize = 13.sp)
                Text("확인하면 입장했던 포털로 돌아갑니다", color = pale, fontSize = 13.sp)
            }
            ChallengePhase.ACTIVE, ChallengePhase.HAZARD -> {
                Text(if (c.kind == ChallengeKind.STEPS) "남은 발걸음 ${c.remainingSteps} / ${c.stepLimit}" else "남은 시간 ${(c.remainingMs + 999) / 1000}초",
                    color = if (c.kind != ChallengeKind.STEPS && c.remainingMs < 15000) red else mint,
                    fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { contentDescription = "도전 잔여 제한" })
                if (c.kind == ChallengeKind.TORCH) {
                    Text("횃불 ${(c.torchMs + 999) / 1000}초 · 화로에서 보충", color = amber, fontSize = 12.sp)
                    LinearProgressIndicator(progress = { c.torchMs / 22000f }, color = amber, trackColor = line, modifier = Modifier.fillMaxWidth())
                }
                if (c.kind == ChallengeKind.SEALS) Text("작동한 제단 ${c.seals} / 3", color = amber, fontSize = 12.sp)
                if (c.kind == ChallengeKind.HAZARDS) Text("실수 ${c.mistakes} / 3 · 실패 시 8초 차감", color = amber, fontSize = 12.sp)
                Box(Modifier.weight(1f).fillMaxWidth().clearAndSetSemantics {
                    contentDescription = "메인 장면"
                    stateDescription = if (phase == ChallengePhase.HAZARD) {
                        mechanismHint(s.dungeon!!.here().challengeMark) + " · " +
                            if (ChallengeStages.hazardSafe(s.dungeon!!.here().challengeMark, c.hazardMs)) "안전 구간" else "작동 중"
                    } else "벽 표식 ${ChallengeStages.rune(s.dungeon!!.y * s.dungeon!!.width + s.dungeon!!.x)}"
                }) {
                    PixelCanvas(s, clock, Modifier.fillMaxSize(), camera = vm.dungeonCamera.sample(clock.takeIf { it > 0 } ?: android.os.SystemClock.uptimeMillis()))
                    if (phase == ChallengePhase.HAZARD) ChallengeMechanism(s, Modifier.fillMaxSize())
                    Text("벽 표식 ${ChallengeStages.rune(s.dungeon!!.y * s.dungeon!!.width + s.dungeon!!.x)}",
                        color = amber, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).background(ink.copy(alpha = .85f)).padding(7.dp))
                }
                Text(if (phase == ChallengePhase.HAZARD) mechanismHint(s.dungeon!!.here().challengeMark) else ChallengeStages.clue(s), color = pale, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth().background(panel).padding(8.dp).semantics { contentDescription = "도전 단서" })
                if (phase == ChallengePhase.HAZARD && c.feedback.isNotBlank()) Text(c.feedback, color = amber, fontSize = 12.sp, lineHeight = 17.sp)
                if (phase == ChallengePhase.ACTIVE && vm.engine.controls.directionSelectionActive()) GameButton(onClick = vm::resetDirections) { Text("방향 처음", color = mint) }
            }
        }
        Row(Modifier.fillMaxWidth().height(68.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.engine.controls.buttons().forEachIndexed { i, b -> ActionButton(vm, i, b, Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun ChallengePlan(map: Dungeon, modifier: Modifier) {
    Canvas(modifier.background(panel).semantics { contentDescription = "도전 계획도" }) {
        val unit = min(size.width / map.width, size.height / map.height)
        val left = (size.width - map.width * unit) / 2; val top = (size.height - map.height * unit) / 2
        map.cells.forEachIndexed { i, cell ->
            if (!cell.wall) drawRect(if (cell.event == Event.EXIT) pale else if (i == map.width + 1) mint else line,
                Offset(left + i % map.width * unit, top + i / map.width * unit), Size(unit - 1, unit - 1))
        }
    }
}

private fun mechanismHint(mark: Int) = when (mark) {
    ChallengeStages.BLADE -> "바닥 칼날 · 칼날이 완전히 내려간 동안 통과하세요"
    ChallengeStages.FALLING -> "낙하물 · 바닥에 멈춘 동안 넘어가세요"
    else -> "닫히는 벽 · 양쪽 벽이 완전히 열린 동안 통과하세요"
}

@Composable
private fun ChallengeMechanism(s: State, modifier: Modifier) {
    val c = s.challenge ?: return
    val mark = s.dungeon!!.here().challengeMark
    Canvas(modifier) {
        val w = size.width; val h = size.height; val t = (c.hazardMs % 4000).toFloat()
        val safe = ChallengeStages.hazardSafe(mark, c.hazardMs)
        val steel = Color(0xff879a9b); val dark = Color(0xff263740)
        when (mark) {
            ChallengeStages.BLADE -> {
                val extension = when { t < 1400 -> 1 - t / 1400; t <= 2600 -> 0f; else -> (t - 2600) / 1400 }
                drawRect(dark, Offset(w * .10f, h * .78f), Size(w * .8f, h * .04f))
                for (i in 0..6) {
                    val x = w * (.12f + i * .11f)
                    val path = Path().apply { moveTo(x, h * .79f); lineTo(x + w * .045f, h * (.79f - extension * .36f)); lineTo(x + w * .09f, h * .79f); close() }
                    drawPath(path, steel)
                }
            }
            ChallengeStages.FALLING -> {
                val descent = when { t < 1800 -> t / 1800; t <= 3000 -> 1f; else -> (4000 - t) / 1000 }
                val y = h * (.10f + descent * .65f)
                drawLine(steel, Offset(w * .2f, 0f), Offset(w * .2f, y), 3f)
                drawLine(steel, Offset(w * .8f, 0f), Offset(w * .8f, y), 3f)
                drawRect(Color(0xff886a4c), Offset(w * .13f, y), Size(w * .74f, h * .10f))
                drawRect(steel, Offset(w * .18f, y), Size(w * .04f, h * .10f)); drawRect(steel, Offset(w * .78f, y), Size(w * .04f, h * .10f))
            }
            else -> {
                val intrusion = when { t < 900 || t > 3100 -> 0f; t < 2000 -> (t - 900) / 1100; else -> (3100 - t) / 1100 }
                val width = w * (.10f + intrusion * .39f)
                drawRect(dark, Offset(0f, h * .12f), Size(width, h * .72f)); drawRect(dark, Offset(w - width, h * .12f), Size(width, h * .72f))
                drawLine(steel, Offset(width, h * .12f), Offset(width, h * .84f), 5f)
                drawLine(steel, Offset(w - width, h * .12f), Offset(w - width, h * .84f), 5f)
            }
        }
        drawCircle(if (safe) mint else red, w * .018f, Offset(w * .5f, h * .90f))
    }
}

internal fun LazyListScope.challengeRecordCards(s: State) {
    item { Text("성공 횟수와 최고 기록은 사망·귀환·계승 후에도 유지됩니다. 연습은 제외합니다.", color = muted, fontSize = 12.sp, lineHeight = 18.sp) }
    items(ChallengeKind.entries.toList(), key = { "challenge-record:${it.name}" }) { kind ->
        val r = s.challengeProgress.records[kind]
        Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(8.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(kind.title, color = amber, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("도전 ${r?.attempts ?: 0}회 · 성공 ${r?.clears ?: 0}회", color = pale, fontSize = 14.sp)
            Text(if (r?.bestTimeMs == null) "아직 성공 기록이 없습니다" else "최고 ${"%.1f".format(r.bestTimeMs!! / 1000.0)}초 · 최소 이동 ${r.bestSteps}칸", color = mint, fontSize = 12.sp)
        }
    }
}
