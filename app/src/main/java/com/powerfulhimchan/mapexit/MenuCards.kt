package com.powerfulhimchan.mapexit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import java.util.Locale
import kotlin.math.roundToInt

internal fun statValue(stat: Stat, value: Double): String =
    if (stat in listOf(Stat.CRIT, Stat.CRIT_DAMAGE))
        String.format(Locale.ROOT, "%.1f", value * 100).removeSuffix(".0") + "%"
    else value.roundToInt().toString()

@Composable
private fun Modifier.card(
    enabled: Boolean,
    label: String,
    compact: Boolean = false,
    action: () -> Unit,
): Modifier =
    gameClickable(enabled = enabled, onClick = action)
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
            if (row.last() == Stat.LUCK) {
                val enabled = points > 0 && s.hp > 0 && s.hp < current.hp
                Column(
                    Modifier.weight(1f).fillMaxHeight().card(enabled, "체력 모두 회복 · 1P", compact = true) {
                        vm.choose(Choice("체력 모두 회복 · 1P", "growthHeal"))
                    },
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ItemArt(Kind.POTION, Modifier.size(if (narrow) 24.dp else 30.dp))
                    Text("체력 모두 회복", color = pale, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
                    Text("${s.hp.roundToInt()} / ${current.hp.roundToInt()}", color = pale, fontSize = 12.sp)
                    Text(if (s.hp >= current.hp) "체력 가득 참" else "→ 최대 체력", color = if (enabled) mint else muted, fontSize = 10.sp)
                    Text("사용 1P", color = if (enabled) amber else muted, fontSize = 12.sp)
                }
            } else repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    item(key = "growth-explanations") {
        Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(8.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("능력치의 영향", color = amber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Stat.entries.forEach { stat ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stat.label, color = mint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(Growth.description(stat), color = pale, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
            Text("체력 모두 회복: 성장 포인트 1P를 사용합니다. 체력이 가득 찼을 때는 포인트를 쓰지 않습니다.", color = muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

/** Stable item IDs keep purchases and sales anchored even when rows regroup after removal. */
internal fun LazyListScope.tradeCards(vm: GameModel, menu: String) {
    val s = vm.engine.state
    val choices = vm.engine.choices()
    val gold = s.gold
    val stacks = if (menu == "sell") InventoryStacks.groups(s.inventory, s.equipment.values)
        else s.shop.map { InventoryStacks.Stack(listOf(it), it, 0) }
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
    if (stacks.isEmpty())
        item(key = "trade-empty") {
            Text(if (menu == "sell") "판매할 아이템이 없습니다" else "남은 상품이 없습니다", color = muted)
        }
    items(stacks.chunked(2), key = { "trade:${it.first().representative.id}" }) { row ->
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { stack ->
                val gear = stack.representative
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
                    ItemArt(gear, Modifier.size(if(menu=="shop")36.dp else 48.dp))
                    Text(stack.displayName,color=if(menu=="shop" || affordable)pale else red,fontSize=14.sp,fontWeight=FontWeight.Bold,lineHeight=18.sp)
                    if(menu=="sell") {
                        if (gear.inherited) Text("◆ 계승 장비", color = amber, fontSize = 11.sp)
                        if (stack.equippedCount > 0) Text(
                            if (gear.id in worn.values) "[착용중] · 판매 시 해제" else "${stack.equippedCount}개 착용 중 · 미장착 아이템부터 판매",
                            color = mint, fontSize = 11.sp, lineHeight = 15.sp)
                        Text(gear.summary(),color=muted,fontSize=11.sp,lineHeight=16.sp)
                        Text("${price}G · ${if (stack.count > 1) "1개 판매" else "판매하기"}",color=amber,fontSize=14.sp)
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun ShopComparison(state: State, gear: Gear, occupiedOnly: Boolean = false) {
    Equipment.compare(state, gear)
        .filter { !occupiedOnly || it.current != null || it.removed.isNotEmpty() }
        .forEach { comparison ->
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
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (occupiedOnly) Box(Modifier.size(30.dp).semantics { contentDescription = "현재 장비 아이콘 · ${it.name}" }) {
                        ItemArt(it, Modifier.fillMaxSize())
                    }
                    Text(it.summary(), color = muted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f))
                }
            }
            val other = comparison.removed.filter { it.id != comparison.current?.id }
            other.forEach { removed ->
                Text("함께 해제: ${removed.name}", color = amber, fontSize = 10.sp, lineHeight = 14.sp)
                if (occupiedOnly) Text(removed.summary(), color = muted, fontSize = 10.sp, lineHeight = 14.sp)
            }
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
