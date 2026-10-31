package com.powerfulhimchan.mapexit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Snackbar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.powerfulhimchan.mapexit.core.*
import com.powerfulhimchan.mapexit.core.State

@Composable
internal fun ExperienceMeter(s: State) {
    val fraction=(s.experience.toFloat()/s.experienceRequired.coerceAtLeast(1)).coerceIn(0f,1f)
    Column(Modifier.fillMaxWidth().padding(bottom=5.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text("경험치 ${s.experience} / ${s.experienceRequired} · 다음 레벨까지 ${s.experienceRemaining}",color=amber,fontSize=11.sp,lineHeight=15.sp)
        Box(Modifier.fillMaxWidth().height(5.dp).background(line).semantics {
            contentDescription="레벨 경험치"
            progressBarRangeInfo=ProgressBarRangeInfo(fraction,0f..1f)
            stateDescription="${s.experience} / ${s.experienceRequired}"
        }) { Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(amber)) }
    }
}

@Composable
internal fun LootRewardRow(vm: GameModel, gear: Gear, showSummary: Boolean = true) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(42.dp).semantics { contentDescription = "획득 아이템 아이콘 · ${gear.name}" }) {
            ItemArt(gear, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(gear.name, color = pale, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
            if (showSummary) Text(gear.summary(), color = muted, fontSize = 11.sp, lineHeight = 15.sp)
            if (gear.kind != Kind.POTION) LootEquipButton(vm, gear)
        }
    }
}

@Composable
internal fun LootEquipButton(vm: GameModel, gear: Gear) {
    vm.revision // Refresh eligibility and worn state even when the row retains the same item instance.
    val s=vm.engine.state
    val worn=gear.id in s.equipment.values
    val enabled=gear.id in s.quickEquipIds && s.inventory.any { it.id==gear.id } && !worn
    val slot=Equipment.quickSlot(s,gear)
    val label=if(worn)"[착용중]" else "바로 장착 · ${slot?.label ?: "장비"}"
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp)
        .gameClickable(enabled = enabled) { vm.choose(Choice(label,"equipLoot",gear.id)) }
        .semantics(mergeDescendants=true) { contentDescription="$label · ${gear.name}" }
        .padding(horizontal=10.dp,vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        MenuArt("equipment",Modifier.size(24.dp))
        Text(label,color=if(enabled)mint else muted,fontSize=12.sp,fontWeight=FontWeight.Bold)
    }
}

@Composable
internal fun ShopDetail(vm: GameModel) {
    val s=vm.engine.state
    val gear=s.shop.find { it.id==s.menu?.argument } ?: return
    var count by remember(gear.id) { mutableIntStateOf(1) }
    val price=Equipment.buyPrice(s,gear)*count
    Dialog(onDismissRequest=vm::back) {
      Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().heightIn(max=600.dp)
            .background(panel,RoundedCornerShape(10.dp))
            .border(1.dp,mint,RoundedCornerShape(10.dp))
            .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("아이템 상세",color=mint,fontSize=20.sp,fontWeight=FontWeight.Bold)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                ItemArt(gear,Modifier.size(48.dp));Text(gear.name,color=pale,fontSize=18.sp,fontWeight=FontWeight.Bold)
            }
            Text(gear.summary(),color=pale,fontSize=12.sp,lineHeight=18.sp)
            if(gear.kind!=Kind.POTION) ShopComparison(s,gear)
            if(gear.kind==Kind.POTION) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                for(n in listOf(1,3,5)) Box(Modifier.weight(1f).heightIn(min=48.dp)
                    .gameClickable(selected = count == n) { count=n }
                    .semantics { contentDescription="구매 수량 ${n}개" },contentAlignment=Alignment.Center) {
                    Text("${n}개",color=if(count==n)mint else pale,fontWeight=FontWeight.Bold)
                }
            }
            Text("가격 ${price}G · 소지 ${s.gold}G",color=amber,fontSize=14.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                GameButton(onClick=vm::back,modifier=Modifier.weight(1f)) { Text("취소",color=muted) }
                GameButton(onClick={ vm.purchase(gear.id,count) },modifier=Modifier.weight(1f)) { Text("구매",color=mint) }
            }
        }
        // A Dialog owns a separate window, so the screen-level notice is behind it.
        if(vm.goldNoticeVisible) Snackbar(
            modifier=Modifier.align(Alignment.Center).padding(16.dp),
            containerColor=panel,contentColor=red,
        ) {
            Text("골드부족",Modifier.fillMaxWidth(),fontSize=18.sp,
                textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontWeight=FontWeight.Bold)
        }
      }
    }
}
