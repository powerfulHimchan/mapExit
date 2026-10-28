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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerfulhimchan.mapexit.core.*
import kotlin.math.ceil

/** Camera, labels and feedback share the scene area so compact message rows never hide vital HP. */
@Composable
internal fun ScenePresentation(vm: GameModel, clock: Long, modifier: Modifier) {
    vm.revision
    val s = vm.engine.state.copy()
    val inBattle = s.enemy != null || (s.mode == Mode.VICTORY && s.battleResult != null)
    val enemy = s.enemy ?: s.battleResult?.enemy
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (inBattle) {
            key(s.battleId) {
                val camera = remember { Animatable(0f) }
                LaunchedEffect(Unit) { camera.animateTo(1f, tween(420)) }
                if (camera.value < 1f)
                    PixelCanvas(
                        s.copy(mode = Mode.EXPLORE, enemy = null, battleResult = null),
                        clock,
                        Modifier.fillMaxSize(),
                    )
                PixelCanvas(
                    s,
                    clock,
                    Modifier.fillMaxSize().graphicsLayer { alpha = camera.value },
                    vm.visualEffect,
                    vm.visualEffectAt,
                )
            }
        } else PixelCanvas(s, clock, Modifier.fillMaxSize(), vm.visualEffect, vm.visualEffectAt)
        if (inBattle && enemy != null && s.mode != Mode.VICTORY) {
            Column(
                Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(ink.copy(alpha = .94f), RoundedCornerShape(6.dp))
                    .border(1.dp, line, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "3인칭 전투 · ${if(s.mode==Mode.VICTORY) "종료" else if(s.mode==Mode.COMBAT_ATTACK) "공격 차례" else "방어 차례"}",
                    color = mint,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.semantics { contentDescription = "3인칭 전투" },
                )
                Text(
                    enemy.name,
                    color = pale,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                )
                val health =
                    "적 HP ${ceil(enemy.hp.coerceAtLeast(0.0)).toInt()} / ${ceil(enemy.maxHp).toInt()}"
                Text(
                    health,
                    color = amber,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.semantics { contentDescription = health },
                )
                Box(Modifier.fillMaxWidth().height(4.dp).background(line)) {
                    Box(
                        Modifier.fillMaxWidth((enemy.hp / enemy.maxHp).toFloat().coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(red)
                    )
                }
            }
        }
        if (s.mode == Mode.TRAP_GUIDE) {
            s.trap?.let { trap ->
                Column(
                    Modifier.align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .heightIn(max = maxHeight - 20.dp)
                        .background(panel.copy(alpha = .98f), RoundedCornerShape(8.dp))
                        .border(1.dp, amber, RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "함정 발견 · ${trap.title}",
                        color = amber,
                        fontSize = 18.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(trap.guide(), color = pale, fontSize = 13.sp, lineHeight = 20.sp)
                    Text(
                        "제한 시간 ${(trap.limitMs/1000.0)}초 · 설명 중에는 시간이 흐르지 않습니다.",
                        color = muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                    Text(
                        "준비되면 B ‘함정 시작’",
                        color = mint,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (s.mode == Mode.VICTORY) {
            s.battleResult?.let { result ->
                Box(
                    Modifier.fillMaxSize().padding(bottom = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .heightIn(max = maxHeight - 48.dp)
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
                        Text(
                            "${result.enemy.name} 격파",
                            color = pale,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        Text(
                            "적 HP 0 / ${ceil(result.enemy.maxHp).toInt()}",
                            color = muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                        Text(
                            "+${result.gold} G  ·  경험치 +${result.experience}",
                            color = mint,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (result.items.isNotEmpty())
                            Text(
                                "획득: ${result.items.joinToString(", ")}",
                                color = pale,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                        if (result.enemy.boss)
                            Text(
                                "${s.floor}층 이동 장치 해금",
                                color = amber,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                        Text("B로 결과 확인 후 계속", color = muted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
        vm.currentImpact?.let { impact ->
            key(impact.serial) {
                val rise = remember { Animatable(0f) }
                LaunchedEffect(Unit) { rise.animateTo(1f, tween(1200)) }
                val enemyHit = impact.target == ImpactTarget.ENEMY
                val popupColor =
                    if (impact.outcome in listOf(ImpactOutcome.DODGE, ImpactOutcome.MISS)) mint
                    else if (impact.critical || !enemyHit) red else amber
                Text(
                    impact.label(),
                    color = popupColor,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Black,
                    modifier =
                        (if (s.mode == Mode.VICTORY)
                                Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            else Modifier.offset(
                                x = maxWidth * (if (enemyHit) .55f else .12f),
                                y =
                                    maxHeight *
                                        (if (enemyHit) .32f else .62f) - 24.dp * rise.value,
                            ))
                            .background(ink.copy(alpha = .9f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .semantics {
                                contentDescription =
                                    "${if(enemyHit) "적" else "플레이어"} 피해 ${impact.label()}"
                            },
                )
            }
        }
    }
}
