package com.powerfulhimchan.mapexit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerfulhimchan.mapexit.core.*
import kotlin.math.ceil
import kotlinx.coroutines.delay

/** Camera, labels and feedback share the scene area so compact message rows never hide vital HP. */
@Composable
internal fun ScenePresentation(vm: GameModel, clock: Long, modifier: Modifier) {
    vm.revision
    val time = clock.takeIf { it > 0 } ?: android.os.SystemClock.uptimeMillis()
    val s = vm.presentationState(time).copy()
    val motion = vm.battleVisual?.takeIf { it.active(time) }
    val inBattle = s.enemy != null || (s.mode == Mode.VICTORY && s.battleResult != null)
    val enemy =
        if (
            motion?.impact?.target == ImpactTarget.ENEMY &&
                time < motion.startedAt + BattleAnimation.HIT_MS
        )
            motion.scene.enemy
        else s.enemy ?: s.battleResult?.enemy
    val dungeonPose =
        vm.dungeonCamera.sample(clock.takeIf { it > 0 } ?: android.os.SystemClock.uptimeMillis())
    val density = LocalDensity.current
    var headerHeight by remember(s.battleId) { mutableStateOf(0.dp) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compactBattle = maxHeight < 300.dp
        if (inBattle) {
            key(s.battleId) {
                val camera = remember { Animatable(0f) }
                LaunchedEffect(Unit) { camera.animateTo(1f, tween(420)) }
                if (camera.value < 1f)
                    PixelCanvas(
                        s.copy(mode = Mode.EXPLORE, enemy = null, battleResult = null),
                        time,
                        Modifier.fillMaxSize(),
                        camera = dungeonPose,
                        battle = motion,
                    )
                PixelCanvas(
                    s,
                    time,
                    Modifier.fillMaxSize().graphicsLayer { alpha = camera.value },
                    vm.visualEffect,
                    vm.visualEffectAt,
                    battle = motion,
                )
            }
        } else
            PixelCanvas(
                s,
                time,
                Modifier.fillMaxSize(),
                vm.visualEffect,
                vm.visualEffectAt,
                dungeonPose,
                motion,
            )
        if (inBattle && enemy != null && s.mode != Mode.VICTORY) {
            Column(
                Modifier.align(Alignment.TopCenter)
                    .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                    .fillMaxWidth()
                    .padding(if (compactBattle) 4.dp else 8.dp)
                    .background(ink.copy(alpha = .94f), RoundedCornerShape(6.dp))
                    .border(1.dp, line, RoundedCornerShape(6.dp))
                    .padding(if (compactBattle) 4.dp else 8.dp)
                    .semantics { contentDescription = "전투 정보" },
                verticalArrangement = Arrangement.spacedBy(if (compactBattle) 1.dp else 3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "전투",
                        color = mint,
                        fontSize = if (compactBattle) 8.sp else 10.sp,
                        lineHeight = if (compactBattle) 10.sp else 13.sp,
                        modifier = Modifier.semantics { contentDescription = "3인칭 전투" },
                    )
                    Text(
                        enemy.name,
                        color = pale,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compactBattle) 10.sp else 13.sp,
                        lineHeight = if (compactBattle) 12.sp else 17.sp,
                    )
                }
                val health =
                    "몬스터 HP ${ceil(enemy.hp.coerceAtLeast(0.0)).toInt()} / ${ceil(enemy.maxHp).toInt()}"
                Text(
                    health,
                    color = amber,
                    fontSize = if (compactBattle) 10.sp else 13.sp,
                    lineHeight = if (compactBattle) 12.sp else 17.sp,
                    modifier = Modifier.semantics { contentDescription = health },
                )
                val plan = motion?.scene?.enemy ?: enemy
                Text(
                    "몬스터가 ${CombatTactics.cue(plan)}.",
                    color = amber,
                    fontSize = if (compactBattle) 10.sp else 12.sp,
                    lineHeight = if (compactBattle) 12.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier.semantics {
                            contentDescription = "몬스터 행동 예고 ${CombatTactics.cue(plan)}"
                        },
                )
                Text(
                    CombatTactics.hint(plan),
                    color = muted,
                    fontSize = if (compactBattle) 8.sp else 10.sp,
                    lineHeight = if (compactBattle) 10.sp else 14.sp,
                )
                if (plan.opening)
                    Text(
                        if (compactBattle) "빈틈! 다음 공격 강화" else "빈틈! 피해 +35% · 강공격 명중 +20%p",
                        color = mint,
                        fontSize = if (compactBattle) 8.sp else 10.sp,
                        lineHeight = if (compactBattle) 10.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { contentDescription = "빈틈 공격 기회" },
                    )
                Box(
                    Modifier.fillMaxWidth()
                        .height(if (compactBattle) 3.dp else 4.dp)
                        .background(line)
                ) {
                    Box(
                        Modifier.fillMaxWidth((enemy.hp / enemy.maxHp).toFloat().coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(red)
                    )
                }
            }
        }
        if (s.mode in listOf(Mode.TRAP, Mode.TRAP_GUIDE)) {
            s.trap?.let { trap ->
                val caption =
                    when (trap.phase) {
                        TrapPhase.PREPARE -> trap.cue()
                        TrapPhase.REVEAL -> "기억해라"
                        TrapPhase.RESULT -> if (trap.success) "회피 성공" else "회피 실패"
                        else -> null
                    }
                if (caption != null)
                    Text(
                        caption,
                        color = if (trap.phase == TrapPhase.RESULT && !trap.success) red else amber,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Black,
                        modifier =
                            Modifier.align(
                                    if (trap.physicalAction) Alignment.TopCenter
                                    else Alignment.Center
                                )
                                .padding(12.dp)
                                .background(ink.copy(alpha = .94f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
            }
        }
        if (s.mode == Mode.ENCOUNTER) EncounterCaption(vm, Modifier.align(Alignment.TopCenter))
        if (s.mode == Mode.VICTORY && !vm.battleBusy(time)) {
            s.battleResult?.let { result ->
                val feedbackSpace = if (vm.currentImpact?.critical == true) 80.dp else 56.dp
                val victoryHeight = maxHeight - feedbackSpace
                Box(
                    Modifier.fillMaxSize().padding(bottom = feedbackSpace),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .heightIn(max = victoryHeight)
                            .background(ink.copy(alpha = .96f), RoundedCornerShape(10.dp))
                            .border(2.dp, amber, RoundedCornerShape(10.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            "전투 승리",
                            color = amber,
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.semantics { contentDescription = "전투 승리" },
                        )
                        LootBagButton(vm)
                        Text(
                            "${result.enemy.name} 격파",
                            color = pale,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        Text(
                            "몬스터 HP 0 / ${ceil(result.enemy.maxHp).toInt()}",
                            color = muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                        Text(
                            "+${result.gold} G  ·  획득 경험치 +${result.experience}",
                            color = mint,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("경험치 ${s.experience} / ${s.experienceRequired} · 다음 레벨까지 ${s.experienceRemaining}",color=amber,fontSize=11.sp)
                        result.rewards.forEach { gear ->
                            LootRewardRow(vm, gear, showSummary = gear.bossOriginFloor == null)
                        }
                        // Old saves may retain reward names without structured item identities.
                        if (result.rewards.isEmpty() && result.items.isNotEmpty())
                            Text(
                                "획득: ${result.items.joinToString(", ")}",
                                color = pale,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                        if (result.enemy.boss)
                            result.rewards
                                .filter { it.bossOriginFloor != null }
                                .forEach { gear ->
                                    Text(
                                        "보스 전리품",
                                        color = amber,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "기본: ${gear.baseSummary()}",
                                        color = pale,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                    )
                                    Text(
                                        "추가 옵션: ${gear.affixSummary()}",
                                        color = mint,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                    )
                                }
                        if (result.enemy.boss)
                            Text(
                                "${s.floor}층 이동 장치 해금",
                                color = amber,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                        Text("결과 확인 후 계속", color = muted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
        if (s.mode == Mode.EXPLORE && s.chestLoot != null)
            ChestLootCard(s.chestLoot!!, vm, Modifier.align(Alignment.Center).padding(16.dp), (maxHeight - 32.dp).coerceAtLeast(80.dp))
        vm.currentImpact
            ?.takeIf {
                s.mode in listOf(Mode.COMBAT_ATTACK, Mode.COMBAT_DEFEND) || vm.battleBusy(time)
            }
            ?.let { impact ->
                key(impact.serial) {
                    val rise = remember { Animatable(0f) }
                    val hitTime =
                        vm.battleVisual
                            ?.takeIf { it.impact.serial == impact.serial }
                            ?.let { it.startedAt + BattleAnimation.HIT_MS } ?: time
                    var visible by remember { mutableStateOf(time >= hitTime) }
                    var animatePopup by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        animatePopup = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
                        delay((hitTime - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0))
                        visible = true
                        if (animatePopup) rise.animateTo(1f, tween(1200))
                        else delay(1200)
                        visible = false
                    }
                    var popupHeight by remember { mutableStateOf(0.dp) }
                    val enemyHit = impact.target == ImpactTarget.ENEMY
                    val avoided =
                        impact.outcome in
                            listOf(ImpactOutcome.DODGE, ImpactOutcome.MISS, ImpactOutcome.BLOCK)
                    val popupColor =
                        if (avoided) mint
                        else if (!enemyHit) red else if (impact.critical) amber else pale
                    if (visible)
                        Column(
                            modifier =
                                Modifier.onSizeChanged {
                                        popupHeight = with(density) { it.height.toDp() }
                                    }
                                    .offset(
                                        x = maxWidth * (if (enemyHit) .48f else .05f),
                                        y =
                                            (maxHeight * (if (enemyHit) .34f else .62f) -
                                                    24.dp * rise.value)
                                                .coerceAtLeast(headerHeight + 4.dp)
                                                .coerceAtMost(
                                                    (maxHeight - popupHeight - 4.dp).coerceAtLeast(
                                                        0.dp
                                                    )
                                                ),
                                    )
                                    .graphicsLayer {
                                        alpha=if(!animatePopup || rise.value<.72f)1f else ((1-rise.value)/.28f).coerceIn(0f,1f)
                                        val punch=kotlin.math.sin(rise.value.coerceIn(0f,1f)*kotlin.math.PI).toFloat()
                                        scaleX=if(animatePopup) .84f+.16f*BattleAnimation.smooth(rise.value/.12f)+if(impact.critical)punch*.1f else 0f else 1f
                                        scaleY=scaleX
                                    }
                                    .widthIn(max = maxWidth * .50f)
                                    .background(ink.copy(alpha = .94f), RoundedCornerShape(6.dp))
                                    .border(
                                        if (impact.critical) 2.dp else 1.dp,
                                        if (impact.critical) popupColor else line,
                                        RoundedCornerShape(6.dp),
                                    )
                                    .padding(
                                        horizontal = if (compactBattle) 6.dp else 8.dp,
                                        vertical = if (compactBattle) 3.dp else 4.dp,
                                    )
                                    .semantics(mergeDescendants = true) {
                                        contentDescription =
                                            "${if(enemyHit) "몬스터" else "플레이어"} 피해 ${impact.label()}"
                                    },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (impact.outcome == ImpactOutcome.BLOCK)
                                Text(
                                    "완전 방어",
                                    color = mint,
                                    fontSize = if (compactBattle) 10.sp else 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            if (impact.critical)
                                Text(
                                    "치명타!",
                                    color = popupColor,
                                    fontSize = if (compactBattle) 10.sp else 14.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            if (impact.tactic.isNotEmpty())
                                Text(
                                    impact.tactic,
                                    color = mint,
                                    fontSize = if (compactBattle) 8.sp else 10.sp,
                                )
                            if (impact.luckyAttack)
                                Text(
                                    "행운 · 공격 강화",
                                    color = mint,
                                    fontSize = if (compactBattle) 8.sp else 10.sp,
                                )
                            if (impact.luckyCritical)
                                Text(
                                    "행운 · 치명 확률 강화",
                                    color = mint,
                                    fontSize = if (compactBattle) 8.sp else 10.sp,
                                )
                            Text(
                                if (impact.outcome == ImpactOutcome.BLOCK) "0"
                                else if (impact.outcome == ImpactOutcome.DODGE) "회피"
                                else if (impact.outcome == ImpactOutcome.MISS) "빗나감"
                                else "-${impact.amount}",
                                color = popupColor,
                                fontSize =
                                    if (compactBattle) 18.sp
                                    else if (impact.critical) 32.sp else 25.sp,
                                lineHeight =
                                    if (compactBattle) 22.sp
                                    else if (impact.critical) 36.sp else 30.sp,
                                fontWeight = FontWeight.Black,
                            )
                            if (impact.outcome == ImpactOutcome.GUARD && !impact.critical)
                                Text(
                                    "방어",
                                    color = popupColor,
                                    fontSize = if (compactBattle) 8.sp else 10.sp,
                                )
                        }
                }
            }
    }
}

@Composable
private fun ChestLootCard(loot: ChestLootNotice, vm: GameModel, modifier: Modifier, availableHeight: androidx.compose.ui.unit.Dp) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = minOf(300.dp, availableHeight))
            .background(ink.copy(alpha = .97f), RoundedCornerShape(8.dp))
            .border(2.dp, amber, RoundedCornerShape(8.dp))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "상자 획득",
            color = amber,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Black,
        )
        LootBagButton(vm)
        loot.items.forEach { gear -> LootRewardRow(vm, gear) }
        Text(
            "+${loot.gold} G",
            color = mint,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("아래 이동 버튼으로 탐험을 계속하세요", color = muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}
