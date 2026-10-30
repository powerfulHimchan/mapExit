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
import androidx.compose.runtime.Composable
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
        String.format(Locale.ROOT, "%.1f", value * 100).removeSuffix(".0") + "%"
    else value.roundToInt().toString()

private fun Modifier.card(
    enabled: Boolean,
    label: String,
    compact: Boolean = false,
    action: () -> Unit,
): Modifier =
    background(panel, RoundedCornerShape(8.dp))
        .border(1.dp, if (enabled) line else line.copy(alpha = .5f), RoundedCornerShape(8.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = action)
        .semantics(mergeDescendants = true) { contentDescription = label }
        .padding(if (compact) 6.dp else 12.dp)

internal fun LazyListScope.growthCards(vm: GameModel) {
    val s = vm.engine.state
    val points = s.points
    val current = vm.engine.stats()
    item(key = "growth-points") {
        Text(
            "남은 성장 포인트 ${points}P",
            color = mint,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    items(Stat.entries.chunked(4), key = { "stats:${it.first().name}" }) { row ->
        val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 320
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 4.dp else 8.dp),
        ) {
            row.forEach { stat ->
                val choice = Choice("${stat.label} 올리기 (${points}P)", "stat", stat.ordinal.toLong())
                val next = Growth.preview(s, stat)
                Column(
                    Modifier.weight(1f).fillMaxHeight().card(
                        points > 0,
                        choice.label,
                        compact = true,
                    ) {
                        vm.choose(choice)
                    },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatArt(stat, Modifier.size(if (narrow) 24.dp else 30.dp))
                    Text(
                        stat.label,
                        color = pale,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    )
                    Text(
                        statValue(stat, current.value(stat)),
                        color = pale,
                        fontWeight = FontWeight.Black,
                        fontSize = if (narrow) 12.sp else 17.sp,
                    )
                    Text(
                        "→ ${statValue(stat, next.value(stat))}",
                        color = mint,
                        fontSize = 10.sp,
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    "${stat.label} 성장 후 ${statValue(stat, next.value(stat))}"
                            },
                    )
                    Text(
                        if (points > 0) "+ 1P" else "0P",
                        color = if (points > 0) amber else muted,
                        fontSize = 12.sp,
                    )
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
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
    if(menu=="shop") {
        items(vm.engine.lootChoices(),key={ "purchase-equip:${it.argument}" }) { choice ->
            val gear=s.inventory.find { it.id==choice.argument }
            if(gear!=null) LootEquipButton(vm,gear)
        }
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
                Row(Modifier.card(true,"아이템 판매") {
                    choices.firstOrNull { it.action=="sellMenu" }?.let(vm::choose)
                },verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    MenuArt("shop",Modifier.size(22.dp));Text("아이템 판매",color=mint,fontSize=12.sp)
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
                val enabled = choice != null
                val affordable = menu == "sell" || gold >= price
                Column(
                    Modifier.weight(1f).fillMaxHeight().card(enabled, choice?.label ?: gear.name) {
                        choice?.let(vm::choose)
                    },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ItemArt(gear.kind, Modifier.size(if(menu=="shop")36.dp else 48.dp))
                    Text(gear.name,color=if(menu=="shop" || affordable)pale else red,fontSize=14.sp,fontWeight=FontWeight.Bold,lineHeight=18.sp)
                    if(menu=="sell") {
                        if(gear.id in worn.values) Text("[착용중] · 판매 시 해제",color=mint,fontSize=11.sp)
                        Text(gear.summary(),color=muted,fontSize=11.sp,lineHeight=16.sp)
                        Text("${price}G · 판매하기",color=amber,fontSize=14.sp)
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun ShopComparison(state: State, gear: Gear) {
    Equipment.compare(state, gear).forEach { comparison ->
        Column(
            Modifier.fillMaxWidth().background(ink, RoundedCornerShape(4.dp)).padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "${comparison.slot.label} 교체 비교",
                color = amber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "현재: ${comparison.current?.name ?: "비어 있음"}",
                color = pale,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            comparison.current?.let {
                Text(it.summary(), color = muted, fontSize = 10.sp, lineHeight = 14.sp)
            }
            val other = comparison.removed.filter { it.id != comparison.current?.id }
            if (other.isNotEmpty())
                Text(
                    "함께 해제: ${other.joinToString { it.name }}",
                    color = amber,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            val changes =
                Stat.entries.filter {
                    kotlin.math.abs(comparison.after.value(it) - comparison.before.value(it)) >
                        .0001
                }
            if (changes.isEmpty()) Text("능력치 변화 없음", color = muted, fontSize = 10.sp)
            changes.forEach { stat ->
                val before = comparison.before.value(stat)
                val after = comparison.after.value(stat)
                val delta = after - before
                val difference =
                    if (stat in listOf(Stat.CRIT, Stat.CRIT_DAMAGE))
                        String.format(Locale.ROOT, "%+.1f%%p", delta * 100)
                    else String.format(Locale.ROOT, "%+.1f", delta).removeSuffix(".0")
                Text(
                    "${stat.label} ${statValue(stat, before)} → ${statValue(stat, after)} ($difference)",
                    color = if (delta > 0) mint else red,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}
