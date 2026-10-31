package com.powerfulhimchan.mapexit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerfulhimchan.mapexit.core.*

@Composable
internal fun MenuArt(action: String, modifier: Modifier = Modifier) {
    val rows =
        when (action) {
            "enter" ->
                listOf(
                    "########",
                    "##....##",
                    "##.##.##",
                    "##.##.##",
                    "##.##.##",
                    "##....##",
                    "##....##",
                    "########",
                )
            "return" ->
                listOf(
                    "...##...",
                    "..####..",
                    ".######.",
                    "########",
                    ".#....#.",
                    ".#.##.#.",
                    ".#.##.#.",
                    ".######.",
                )
            "equipment",
            "item" ->
                listOf(
                    "##.##.##",
                    "########",
                    ".######.",
                    "..####..",
                    "..####..",
                    "..####..",
                    "..####..",
                    "..####..",
                )
            "inventory",
            "sell" ->
                listOf(
                    "..####..",
                    "..#..#..",
                    ".######.",
                    "########",
                    "##.##.##",
                    "########",
                    "########",
                    ".######.",
                )
            "shop",
            "quantity" ->
                listOf(
                    ".######.",
                    "########",
                    "#.#.#.##",
                    "########",
                    ".#....#.",
                    ".#.##.#.",
                    ".#.##.#.",
                    ".######.",
                )
            "growth" ->
                listOf(
                    "...##...",
                    "..####..",
                    ".######.",
                    "...##...",
                    "...##...",
                    "#..##..#",
                    "#......#",
                    "########",
                )
            "bank" ->
                listOf(
                    "...##...",
                    "..####..",
                    ".######.",
                    "########",
                    ".#.#.#..",
                    ".#.#.#..",
                    ".#.#.#..",
                    "########",
                )
            "help", "bestiary", "achievements", "challengeRecords", "trainingChallenges" ->
                listOf(
                    ".######.",
                    ".#....#.",
                    ".#.##.#.",
                    ".#....#.",
                    ".#.##.#.",
                    ".#....#.",
                    ".######.",
                    "........",
                )
            "level" ->
                listOf(
                    "...##...",
                    "...##...",
                    "########",
                    ".######.",
                    "..####..",
                    ".######.",
                    ".##..##.",
                    "##....##",
                )
            else ->
                listOf(
                    "..####..",
                    ".######.",
                    "##.##.##",
                    "##.#..##",
                    "##..#.##",
                    "##.##.##",
                    ".######.",
                    "..####..",
                )
        }
    PixelGlyph(rows, if (action in listOf("bank", "coin", "level")) amber else mint, modifier)
}

@Composable
internal fun PreparationMenuGrid(vm: GameModel) {
    val menus =
        listOf(
            "장비" to "equipment",
            "가방" to "inventory",
            "상점" to "shop",
            "성장" to "growth",
            "보관소" to "bank",
            "기록" to "help",
        )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        menus.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, action) ->
                    Column(
                        Modifier.weight(1f)
                            .background(panel, RoundedCornerShape(8.dp))
                            .border(1.dp, line, RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { vm.choose(Choice(label, action)) }
                            .semantics(mergeDescendants = true) { contentDescription = label }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        MenuArt(action, Modifier.size(32.dp))
                        Text(label, color = pale, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun StatusSummary(e: GameEngine) {
    val s = e.state
    val p = e.stats()
    Column(
        Modifier.fillMaxWidth().background(panel).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MenuArt("level", Modifier.size(22.dp))
            Text(
                "LV.${s.level}",
                color = pale,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 5.dp),
            )
            MenuArt("coin", Modifier.size(22.dp))
            Text("${s.gold}G", color = amber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Achievements.title(s)?.let { Text("칭호 · $it", color = mint, fontSize = 12.sp) }
        Stat.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { stat ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        StatArt(stat, Modifier.size(24.dp))
                        Text(
                            if (stat == Stat.HP) "체력" else stat.label,
                            color = muted,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                        )
                        Text(
                            if (stat == Stat.HP) "${s.hp.toInt()}/${p.hp.toInt()}"
                            else statValue(stat, p.value(stat)),
                            color = pale,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemArt(Kind.POTION, Modifier.size(24.dp))
            Text("${s.inventory.count { it.kind == Kind.POTION }}개", color = pale, fontSize = 11.sp)
            MenuArt("bank", Modifier.size(22.dp))
            Text("${s.bank}G", color = amber, fontSize = 11.sp)
            MenuArt("growth", Modifier.size(22.dp))
            Text("${s.points}P", color = mint, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun ExpeditionButton(
    title: String,
    subtitle: String,
    icon: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(panel, RoundedCornerShape(8.dp))
            .border(1.dp, mint, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = title }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MenuArt(icon, Modifier.size(32.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = mint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = muted, fontSize = 11.sp)
        }
        Text("›", color = mint, fontSize = 28.sp)
    }
}

@Composable
internal fun ExplorationRecord(s: State) {
    Column(
        Modifier.fillMaxWidth().background(panel).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Achievements.title(s)?.let { Text("칭호 · $it", color = mint, fontSize = 14.sp) }
        Text("누적 탐험", color = amber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            "${s.cycle}회차 · 최대 지하 ${s.highest}층 · 클리어 ${s.clears}회",
            color = pale,
            fontSize = 13.sp,
        )
        Text(
            "발견한 이동 장치: ${s.devices.sorted().joinToString { "${it}층" }.ifEmpty { "없음" }}",
            color = muted,
            fontSize = 12.sp,
        )
        Text("진행 중인 의뢰", color = amber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            s.quest?.let {
                "${it.title} · ${it.progress}/${it.target}${if (it.complete) " · 달성" else ""}"
            } ?: "진행 중인 의뢰가 없습니다",
            color = pale,
            fontSize = 13.sp,
        )
    }
}
