package com.powerfulhimchan.mapexit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.powerfulhimchan.mapexit.core.*
import java.util.Locale
import kotlin.math.roundToInt

internal fun statValue(stat: Stat, value: Double): String =
    if (stat in listOf(Stat.CRIT, Stat.CRIT_DAMAGE))
        String.format(Locale.ROOT, "%.1f%%", value * 100)
    else value.roundToInt().toString()

private fun Modifier.card(enabled: Boolean, label: String, action: () -> Unit): Modifier =
    background(panel, RoundedCornerShape(8.dp))
        .border(1.dp, if (enabled) line else line.copy(alpha = .5f), RoundedCornerShape(8.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = action)
        .semantics(mergeDescendants = true) { contentDescription = label }
        .padding(12.dp)

internal fun LazyListScope.growthCards(vm: GameModel) {
    val s = vm.engine.state
    val points = s.points
    val current = vm.engine.stats()
    item(key = "growth-points") {
        Text(
            "남은 성장 포인트 ${points}P · 카드당 1P",
            color = mint,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    items(Stat.entries.chunked(2), key = { "stats:${it.first().name}" }) { row ->
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { stat ->
                val choice = Choice("${stat.label} 올리기 (${points}P)", "stat", stat.ordinal.toLong())
                val next = Growth.preview(s, stat)
                Column(
                    Modifier.weight(1f).fillMaxHeight().card(points > 0, choice.label) {
                        vm.choose(choice)
                    },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatArt(stat, Modifier.size(44.dp))
                    Text(stat.label, color = pale, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        statValue(stat, current.value(stat)),
                        color = pale,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                    )
                    Text(
                        "성장 후 ${statValue(stat, next.value(stat))}",
                        color = mint,
                        fontSize = 12.sp,
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    "${stat.label} 성장 후 ${statValue(stat, next.value(stat))}"
                            },
                    )
                    Text(
                        if (points > 0) "+ 강화 · 1P" else "포인트 없음",
                        color = if (points > 0) amber else muted,
                        fontSize = 12.sp,
                    )
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Stable item IDs keep purchases and sales anchored even when rows regroup after removal. */
internal fun LazyListScope.tradeCards(vm: GameModel, menu: String) {
    val s = vm.engine.state
    val choices = vm.engine.choices()
    val gold = s.gold
    val items = (if (menu == "sell") s.inventory else s.shop).toList()
    val worn = s.equipment.toMap()
    if (menu == "quantity") {
        item(key = "quantity-wallet") {
            Text("소지 ${gold}G", color = amber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        val potion = s.shop.find { it.id == s.menu?.argument }
        if (potion != null) {
            item(key = "potion-intro") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ItemArt(Kind.POTION, Modifier.size(56.dp))
                    Column {
                        Text("회복약", color = pale, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(potion.summary(), color = muted, fontSize = 12.sp)
                    }
                }
            }
            item(key = "potion-quantities") {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    choices
                        .filter { it.action == "buyQuantity" }
                        .forEach { choice ->
                            val price = Equipment.buyPrice(s, potion) * choice.argument.toInt()
                            Column(
                                Modifier.weight(1f).fillMaxHeight().card(
                                    gold >= price,
                                    choice.label,
                                ) {
                                    vm.choose(choice)
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ItemArt(Kind.POTION, Modifier.size(40.dp))
                                Text(
                                    "${choice.argument}개",
                                    color = pale,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${price}G",
                                    color = amber,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    if (gold >= price) "구매" else "골드 부족",
                                    color = if (gold >= price) mint else red,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                }
            }
        }
        return
    }
    item(key = "trade-balance") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "소지 ${gold}G",
                color = amber,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (menu == "shop")
                OutlinedButton(
                    onClick = { choices.firstOrNull { it.action == "sellMenu" }?.let(vm::choose) }
                ) {
                    Text("아이템 판매")
                }
        }
    }
    if (items.isEmpty())
        item(key = "trade-empty") {
            Text(if (menu == "sell") "판매할 아이템이 없습니다" else "남은 상품이 없습니다", color = muted)
        }
    items(items.chunked(2), key = { "trade:${it.first().id}" }) { row ->
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { gear ->
                val choice =
                    choices.firstOrNull {
                        it.action == (if (menu == "sell") "sell" else "buy") &&
                            it.argument == gear.id
                    }
                val price =
                    if (menu == "sell") Equipment.sellPrice(gear) else Equipment.buyPrice(s, gear)
                val enabled = choice != null && (menu == "sell" || gold >= price)
                Column(
                    Modifier.weight(1f).fillMaxHeight().card(enabled, choice?.label ?: gear.name) {
                        choice?.let(vm::choose)
                    },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ItemArt(gear.kind, Modifier.size(60.dp))
                    Text(
                        gear.name,
                        color = pale,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 18.sp,
                    )
                    if (menu == "sell" && gear.id in worn.values)
                        Text("장착 중 · 판매 시 해제", color = mint, fontSize = 11.sp)
                    Text(gear.summary(), color = muted, fontSize = 11.sp, lineHeight = 16.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${price}G", color = amber, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            menu == "sell" -> "판매하기"
                            !enabled -> "골드 부족"
                            gear.kind == Kind.POTION -> "수량 선택"
                            else -> "구매하기"
                        },
                        color = if (enabled) mint else red,
                        fontSize = 12.sp,
                    )
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
